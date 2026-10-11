package com.snapkode.nivolauncher.pagelet.calendar

import java.util.Calendar
import java.util.TimeZone

/** One row of the Agenda page, top to bottom. */
sealed interface AgendaItem {
    /** An all-day event, listed above the timeline. */
    data class AllDay(val event: CalEvent) : AgendaItem

    /**
     * A timed event. [hourStart] is true for the first event of its hour (the page shows the hour in the margin there);
     * [past] events are over; [current] events are on now.
     */
    data class Timed(val event: CalEvent, val hour: Int, val hourStart: Boolean, val past: Boolean, val current: Boolean) : AgendaItem

    /** "Now": where the clock is among today's events. */
    data class NowLine(val atMs: Long) : AgendaItem

    /** A stretch with nothing on, between two timed events. */
    data class Free(val startMs: Long, val endMs: Long) : AgendaItem
}

/** Turns a day's events into the rows the Agenda page draws. Pure, so the ordering and the now line are tested. */
object AgendaModel {
    fun build(events: List<CalEvent>, nowMs: Long, isToday: Boolean, showPast: Boolean, tz: TimeZone = TimeZone.getDefault()): List<AgendaItem> {
        val out = ArrayList<AgendaItem>()
        events.filter { it.allDay }.sortedBy { it.title.lowercase() }.forEach { out += AgendaItem.AllDay(it) }
        val timed = events.filter { !it.allDay }
            .filter { !isToday || showPast || it.endMs > nowMs }
            .sortedWith(compareBy<CalEvent> { it.beginMs }.thenBy { it.endMs })
        var lastHour = -1
        var nowPlaced = !isToday
        val cal = Calendar.getInstance(tz)
        for (e in timed) {
            if (!nowPlaced && e.beginMs > nowMs) { out += AgendaItem.NowLine(nowMs); nowPlaced = true }
            cal.timeInMillis = e.beginMs
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            out += AgendaItem.Timed(e, hour, hour != lastHour, past = isToday && e.endMs <= nowMs, current = isToday && e.beginMs <= nowMs && e.endMs > nowMs)
            lastHour = hour
        }
        if (!nowPlaced && timed.isNotEmpty()) out += AgendaItem.NowLine(nowMs)
        return out
    }

    /** Adds a [AgendaItem.Free] row before any timed event that starts at least [minGapMs] after everything before it ended. */
    fun withGaps(items: List<AgendaItem>, minGapMs: Long = 30 * 60_000L): List<AgendaItem> {
        val out = ArrayList<AgendaItem>(items.size + 4)
        var busyUntil = Long.MIN_VALUE
        for (item in items) {
            if (item is AgendaItem.Timed) {
                if (busyUntil != Long.MIN_VALUE && item.event.beginMs - busyUntil >= minGapMs) out += AgendaItem.Free(busyUntil, item.event.beginMs)
                busyUntil = maxOf(busyUntil, item.event.endMs)
            }
            out += item
        }
        return out
    }

    /** What is on now (the one ending soonest), otherwise the next timed event to start; null if the day is done. */
    fun upNext(events: List<CalEvent>, nowMs: Long): CalEvent? {
        val timed = events.filter { !it.allDay }
        return timed.filter { it.beginMs <= nowMs && it.endMs > nowMs }.minByOrNull { it.endMs }
            ?: timed.filter { it.beginMs > nowMs }.minByOrNull { it.beginMs }
    }

    /** "in 42 min", "in 2 h 5 min", "ends in 18 min". */
    fun countdown(e: CalEvent, nowMs: Long): String {
        val on = e.beginMs <= nowMs
        val mins = (((if (on) e.endMs else e.beginMs) - nowMs + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
        val span = if (mins < 60) "$mins min" else "${mins / 60} h" + (if (mins % 60 != 0) " ${mins % 60} min" else "")
        return if (on) "ends in $span" else "in $span"
    }

    /** "1h 30m" / "45m" for a free stretch. */
    fun span(ms: Long): String {
        val m = (ms / 60_000L).toInt()
        return if (m >= 60) "${m / 60}h" + (if (m % 60 != 0) " ${m % 60}m" else "") else "${m}m"
    }

    /** "9:00 – 10:30" in the user's clock style, or "All day". */
    fun range(e: CalEvent, time: (Long) -> String): String = if (e.allDay) "All day" else "${time(e.beginMs)} – ${time(e.endMs)}"
}
