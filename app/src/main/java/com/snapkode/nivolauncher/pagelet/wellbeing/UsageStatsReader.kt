package com.snapkode.nivolauncher.pagelet.wellbeing

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

/** Reads the phone's own usage log. Nothing is stored or sent: it is computed fresh from what Android already keeps. */
class UsageStatsReader(private val context: Context) {
    private val usm get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** This launcher, System UI and any home screen: sitting on one isn't screen time. */
    private val excluded: Set<String> by lazy {
        val homes = runCatching {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        (homes + listOf(context.packageName, "com.android.systemui", "android")).toSet()
    }

    /** Midnight at the start of the local day containing [timeMs]. */
    fun dayStart(timeMs: Long, cal: Calendar = Calendar.getInstance()): Long {
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** The events of [startMs, endMs). Empty if usage access isn't allowed. Blocking. */
    fun rawEvents(startMs: Long, endMs: Long): List<RawEvent> {
        val manager = usm ?: return emptyList()
        val events = runCatching { manager.queryEvents(startMs, endMs) }.getOrNull() ?: return emptyList()
        val out = ArrayList<RawEvent>(512)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEventType.ACTIVITY_RESUMED, UsageEventType.ACTIVITY_PAUSED, UsageEventType.ACTIVITY_STOPPED,
                UsageEventType.SCREEN_INTERACTIVE, UsageEventType.SCREEN_NON_INTERACTIVE,
                UsageEventType.KEYGUARD_SHOWN, UsageEventType.KEYGUARD_HIDDEN,
                -> out += RawEvent(e.eventType, e.packageName.orEmpty(), e.timeStamp)
            }
        }
        return out
    }

    /** One local day, up to [endMs] (now for today, the next midnight for a past day). */
    fun day(dayStartMs: Long, endMs: Long): WellbeingDay =
        WellbeingCalculator.compute(rawEvents(dayStartMs, endMs), dayStartMs, endMs, excluded)

    fun label(pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))
}
