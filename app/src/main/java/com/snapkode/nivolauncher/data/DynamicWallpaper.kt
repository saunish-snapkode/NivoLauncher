package com.snapkode.nivolauncher.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.Calendar

/** The four parts of the day a wallpaper can be set for. [startHour] is when each begins. */
enum class DayPeriod(val label: String, val range: String, val startHour: Int) {
    Morning("Morning", "5 AM – 12 PM", 5),
    Afternoon("Afternoon", "12 – 5 PM", 12),
    Evening("Evening", "5 – 9 PM", 17),
    Night("Night", "9 PM – 5 AM", 21);

    companion object {
        fun at(hour: Int): DayPeriod = when (hour) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            in 17..20 -> Evening
            else -> Night
        }

        /** Epoch millis of the next hour at which the period changes, strictly after [now]. */
        fun nextBoundary(now: Calendar): Long {
            val next = (now.clone() as Calendar).apply { set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0) }
            val hours = entries.map { it.startHour }.sorted()
            val upcoming = hours.firstOrNull { it > now.get(Calendar.HOUR_OF_DAY) }
            if (upcoming != null) next.set(Calendar.HOUR_OF_DAY, upcoming)
            else { next.add(Calendar.DAY_OF_YEAR, 1); next.set(Calendar.HOUR_OF_DAY, hours.first()) }
            return next.timeInMillis
        }
    }
}

/** What the settings page shows: whether rotation is on, and when each period's picture was saved (0 = none). */
data class DynamicWallpaperState(val enabled: Boolean = false, val versions: Map<DayPeriod, Long> = DayPeriod.entries.associateWith { 0L })

/**
 * Rotates the system wallpaper by time of day. Pictures are copied into the app's own storage, so the gallery
 * originals can move or vanish. An inexact alarm fires at each boundary; the launcher also re-checks when it starts.
 */
object DynamicWallpaper {
    private const val PREFS = "dynamic_wallpaper"
    private const val MAX_SIDE = 2800

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(context: Context, period: DayPeriod) = File(File(context.filesDir, "wallpapers"), "${period.name.lowercase()}.jpg")

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)

    fun state(context: Context) = DynamicWallpaperState(
        isEnabled(context),
        DayPeriod.entries.associateWith { file(context, it).let { f -> if (f.exists()) f.lastModified() else 0L } },
    )

    fun thumbnail(context: Context, period: DayPeriod): Bitmap? {
        val f = file(context, period).takeIf { it.exists() } ?: return null
        return runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 6 }) }.getOrNull()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).remove("last").apply()
        if (enabled) { applyNow(context); schedule(context) } else cancel(context)
    }

    /** Copies and downsizes the picked picture. Blocking: call off the main thread. */
    fun import(context: Context, period: DayPeriod, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return false
        val target = file(context, period)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        temp.renameTo(target)
        bitmap.recycle()
        prefs(context).edit().remove("last").apply()
        if (isEnabled(context)) applyNow(context)
        true
    }.getOrDefault(false)

    fun clear(context: Context, period: DayPeriod) {
        file(context, period).delete()
        prefs(context).edit().remove("last").apply()
    }

    /** Sets the wallpaper for the current period if it has a picture and isn't already showing. */
    fun applyNow(context: Context) {
        if (!isEnabled(context)) return
        val period = DayPeriod.at(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        val f = file(context, period).takeIf { it.exists() } ?: return
        val stamp = "${period.name}:${f.lastModified()}"
        if (prefs(context).getString("last", null) == stamp) return
        val applied = runCatching {
            val bitmap = BitmapFactory.decodeFile(f.path) ?: return@runCatching false
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            bitmap.recycle()
            true
        }.getOrDefault(false)
        if (applied) prefs(context).edit().putString("last", stamp).apply()
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7, Intent(context, DynamicWallpaperReceiver::class.java).setAction("com.snapkode.nivolauncher.WALLPAPER_TICK"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC, DayPeriod.nextBoundary(Calendar.getInstance()), pending(context)) }
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
    }
}

/** Fires at each part-of-day boundary, after a reboot, and after the app is updated. */
class DynamicWallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowed = setOf("com.snapkode.nivolauncher.WALLPAPER_TICK", Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
        if (intent.action !in allowed) return
        val pending = goAsync()
        Thread {
            try {
                DynamicWallpaper.applyNow(context)
                DynamicWallpaper.schedule(context)
            } finally { pending.finish() }
        }.start()
    }
}
