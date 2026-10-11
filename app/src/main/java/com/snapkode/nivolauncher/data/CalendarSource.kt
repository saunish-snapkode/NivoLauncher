package com.snapkode.nivolauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class NextEvent(val id: Long, val title: String, val begin: Long, val allDay: Boolean)

object CalendarSource {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** The next (or current) event within the coming 24 hours, or null. */
    fun nextEvent(context: Context): NextEvent? {
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + 24 * 60 * 60 * 1000L) }
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                if (c.moveToFirst()) NextEvent(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Event" }, c.getLong(2), c.getInt(3) == 1)
                else null
            }
        }.getOrNull()
    }
}
