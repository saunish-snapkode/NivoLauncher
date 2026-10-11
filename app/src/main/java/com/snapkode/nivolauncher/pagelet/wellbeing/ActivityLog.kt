package com.snapkode.nivolauncher.pagelet.wellbeing

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One line of the day's timeline. */
sealed interface ActivityEntry {
    val timeMs: Long

    class Unlock(override val timeMs: Long) : ActivityEntry
    class ScreenOff(override val timeMs: Long) : ActivityEntry
    class AppUse(override val timeMs: Long, val pkg: String, val durationMs: Long) : ActivityEntry
}

enum class ActivityFilter(val label: String) { All("All"), Unlocks("Unlocks"), AppUse("App use") }

/** Turns a [WellbeingDay] into the Activity log: a time-ordered list, grouped by hour, and a CSV. Pure. */
object ActivityLog {
    fun entries(day: WellbeingDay, filter: ActivityFilter = ActivityFilter.All): List<ActivityEntry> {
        val out = ArrayList<ActivityEntry>()
        if (filter != ActivityFilter.AppUse) day.unlockTimesMs.forEach { out += ActivityEntry.Unlock(it) }
        if (filter == ActivityFilter.All) day.screenOffTimesMs.forEach { out += ActivityEntry.ScreenOff(it) }
        if (filter != ActivityFilter.Unlocks) day.appSessions.filter { it.durationMs >= 5_000L }.forEach { out += ActivityEntry.AppUse(it.startMs, it.pkg, it.durationMs) }
        return out.sortedBy { it.timeMs }
    }

    /** Hour of day (0–23, in [tz]) → that hour's entries, earliest hour first. */
    fun byHour(entries: List<ActivityEntry>, tz: TimeZone = TimeZone.getDefault()): List<Pair<Int, List<ActivityEntry>>> {
        val cal = Calendar.getInstance(tz)
        return entries.groupBy { cal.timeInMillis = it.timeMs; cal.get(Calendar.HOUR_OF_DAY) }.toSortedMap().map { it.key to it.value }
    }

    /** RFC 4180 CSV: `time,type,app,duration_seconds`, one row per event, times as local ISO-8601. */
    fun csv(day: WellbeingDay, label: (String) -> String, tz: TimeZone = TimeZone.getDefault()): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US).apply { timeZone = tz }
        val sb = StringBuilder("time,type,app,duration_seconds\r\n")
        for (e in entries(day, ActivityFilter.All)) {
            val (type, app, secs) = when (e) {
                is ActivityEntry.Unlock -> Triple("unlock", "", "")
                is ActivityEntry.ScreenOff -> Triple("screen_off", "", "")
                is ActivityEntry.AppUse -> Triple("app_use", label(e.pkg), (e.durationMs / 1000L).toString())
            }
            sb.append(fmt.format(java.util.Date(e.timeMs))).append(',').append(type).append(',').append(escape(app)).append(',').append(secs).append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(v: String): String = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** "Today", "Yesterday", or "Mon 5 Oct" for the day starting at [dayStartMs]. */
    fun dayTitle(dayStartMs: Long, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val today = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = cal.timeInMillis
        return when (dayStartMs) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault()).apply { timeZone = tz }.format(java.util.Date(dayStartMs))
        }
    }
}
