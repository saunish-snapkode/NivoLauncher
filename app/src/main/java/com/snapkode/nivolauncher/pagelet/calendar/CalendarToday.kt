package com.snapkode.nivolauncher.pagelet.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone

class CalEvent(
    val id: Long,
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val location: String?,
    /** ARGB, or 0 if the calendar gave none. */
    val color: Int,
)

/** Today's events from the phone's calendars (every account synced to the device). Read-only. */
object CalendarToday {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Local midnight and the next midnight around [nowMs]. */
    fun dayBounds(nowMs: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = nowMs; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        return start to c.timeInMillis
    }

    /** Events still to come or in progress today, soonest first, at most [limit]. Blocking. */
    fun events(context: Context, nowMs: Long, tz: TimeZone = TimeZone.getDefault(), limit: Int = 8): List<CalEvent> {
        if (!hasPermission(context)) return emptyList()
        val (start, end) = dayBounds(nowMs, tz)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = ArrayList<CalEvent>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    // All-day events are stored in UTC midnight; they belong to "today" if they overlap it at all.
                    val b = c.getLong(2); val e = c.getLong(3)
                    out += CalEvent(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Event" }, b, e, allDay, c.getString(5)?.takeIf { it.isNotBlank() }, c.getInt(6))
                }
            }
        }
        return upcoming(out, nowMs).take(limit)
    }

    /** Every event of the local day starting at [dayStartMs], over or not, in time order (timed first, then all-day). Blocking. */
    fun eventsOnDay(context: Context, dayStartMs: Long, tz: TimeZone = TimeZone.getDefault()): List<CalEvent> {
        if (!hasPermission(context)) return emptyList()
        val (start, end) = dayBounds(dayStartMs, tz)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = ArrayList<CalEvent>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    out += CalEvent(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Event" }, c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getString(5)?.takeIf { it.isNotBlank() }, c.getInt(6))
                }
            }
        }
        return out.sortedWith(compareBy<CalEvent> { it.allDay }.thenBy { it.beginMs })
    }

    /** Not yet over, timed events before all-day ones, each group by start time. Pure. */
    fun upcoming(all: List<CalEvent>, nowMs: Long): List<CalEvent> =
        all.filter { it.endMs > nowMs }.sortedWith(compareBy<CalEvent> { it.allDay }.thenBy { it.beginMs })
}

/** The words for how soon an event is. */
object NextUp {
    /** "now" while it is on, "in 12 min" within the hour, "in 2 h 5 min" within 6 hours, otherwise the clock time. */
    fun when_(event: CalEvent, nowMs: Long, clockTime: (Long) -> String): String {
        if (event.allDay) return "All day"
        if (event.beginMs <= nowMs) return "Now"
        val mins = ((event.beginMs - nowMs + 59_999L) / 60_000L).toInt()
        return when {
            mins < 60 -> "in $mins min"
            mins < 360 -> "in ${mins / 60} h" + (if (mins % 60 != 0) " ${mins % 60} min" else "")
            else -> clockTime(event.beginMs)
        }
    }
}
