package com.snapkode.nivolauncher.pagelet

import com.snapkode.nivolauncher.pagelet.calendar.CalEvent
import com.snapkode.nivolauncher.pagelet.calendar.CalendarToday
import com.snapkode.nivolauncher.pagelet.calendar.NextUp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class CalendarTodayTest {
    private val now = 1_000_000_000_000L
    private val M = 60_000L
    private fun ev(id: Long, begin: Long, end: Long, allDay: Boolean = false, title: String = "e$id") = CalEvent(id, title, begin, end, allDay, null, 0)
    private fun clock(ms: Long) = "HH:mm@" + (ms - now) / M

    @Test fun finishedEventsAreDroppedAndTheRestKeepStartOrder() {
        val all = listOf(ev(1, now - 90 * M, now - 30 * M), ev(2, now + 60 * M, now + 90 * M), ev(3, now - 10 * M, now + 20 * M), ev(4, now + 10 * M, now + 20 * M))
        assertEquals(listOf(3L, 4L, 2L), CalendarToday.upcoming(all, now).map { it.id })
    }

    @Test fun timedEventsComeBeforeAllDayOnes() {
        val all = listOf(ev(1, now - 5 * 3_600_000L, now + 10 * 3_600_000L, allDay = true), ev(2, now + 30 * M, now + 60 * M))
        assertEquals(listOf(2L, 1L), CalendarToday.upcoming(all, now).map { it.id })
    }

    @Test fun anEventEndingExactlyNowIsOver() = assertEquals(emptyList<Long>(), CalendarToday.upcoming(listOf(ev(1, now - M, now)), now).map { it.id })

    @Test fun nothingScheduledIsAnEmptyList() = assertEquals(emptyList<CalEvent>(), CalendarToday.upcoming(emptyList(), now))

    @Test fun dayBoundsSpanExactlyTheLocalDay() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val (s, e) = CalendarToday.dayBounds(1_760_000_000_000L, tz)
        assertEquals(24 * 3_600_000L, e - s)
        val cal = java.util.Calendar.getInstance(tz).apply { timeInMillis = s }
        assertEquals(0, cal.get(java.util.Calendar.HOUR_OF_DAY)); assertEquals(0, cal.get(java.util.Calendar.MINUTE))
    }

    @Test fun nextUpWording() {
        assertEquals("Now", NextUp.when_(ev(1, now - 5 * M, now + 10 * M), now, ::clock))
        assertEquals("All day", NextUp.when_(ev(1, now, now + 1, allDay = true), now, ::clock))
        assertEquals("in 1 min", NextUp.when_(ev(1, now + 1, now + 9 * M), now, ::clock))
        assertEquals("in 12 min", NextUp.when_(ev(1, now + 12 * M, now + 30 * M), now, ::clock))
        assertEquals("in 59 min", NextUp.when_(ev(1, now + 59 * M, now + 70 * M), now, ::clock))
        assertEquals("in 1 h", NextUp.when_(ev(1, now + 60 * M, now + 70 * M), now, ::clock))
        assertEquals("in 2 h 5 min", NextUp.when_(ev(1, now + 125 * M, now + 130 * M), now, ::clock))
        assertEquals("a long way off shows the clock time", "HH:mm@600", NextUp.when_(ev(1, now + 600 * M, now + 660 * M), now, ::clock))
    }
}
