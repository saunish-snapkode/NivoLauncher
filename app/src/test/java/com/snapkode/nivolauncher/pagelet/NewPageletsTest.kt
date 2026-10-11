package com.snapkode.nivolauncher.pagelet

import com.snapkode.nivolauncher.MediaProgress
import com.snapkode.nivolauncher.NowPlaying
import com.snapkode.nivolauncher.pagelet.calendar.AgendaItem
import com.snapkode.nivolauncher.pagelet.calendar.AgendaModel
import com.snapkode.nivolauncher.pagelet.calendar.CalEvent
import com.snapkode.nivolauncher.pagelet.move.MoveFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** The pure parts of the Agenda, Now Playing and Move Pagelets. */
class NewPageletsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(h: Int, m: Int = 0): Long = Calendar.getInstance(utc).apply { timeInMillis = 0; set(2026, Calendar.OCTOBER, 11, h, m, 0) }.timeInMillis
    private fun ev(id: Long, title: String, b: Long, e: Long, allDay: Boolean = false) = CalEvent(id, title, b, e, allDay, null, 0)

    // ---- Agenda -----------------------------------------------------------------------------------------------------

    @Test fun agendaPutsAllDayFirstThenTimedInOrderWithTheNowLineBetween() {
        val events = listOf(
            ev(3, "Dinner", at(19), at(21)), ev(1, "Standup", at(9), at(9, 30)), ev(2, "Review", at(11), at(12)), ev(9, "Holiday", at(0), at(23, 59), allDay = true),
        )
        val items = AgendaModel.build(events, at(10), isToday = true, showPast = true, tz = utc)
        assertTrue(items[0] is AgendaItem.AllDay)
        assertEquals(listOf("Standup", "Review", "Dinner"), items.filterIsInstance<AgendaItem.Timed>().map { it.event.title })
        val order = items.map { it::class.simpleName }
        assertEquals(listOf("AllDay", "Timed", "NowLine", "Timed", "Timed"), order)
        assertTrue((items[1] as AgendaItem.Timed).past)
    }

    @Test fun anEventOnNowIsCurrentAndSitsBeforeTheNowLine() {
        val items = AgendaModel.build(listOf(ev(1, "Call", at(10), at(11)), ev(2, "Later", at(14), at(15))), at(10, 30), true, true, utc)
        assertTrue((items[0] as AgendaItem.Timed).current)
        assertTrue(items[1] is AgendaItem.NowLine)
    }

    @Test fun hoursAreLabelledOncePerHour() {
        val items = AgendaModel.build(listOf(ev(1, "A", at(9), at(9, 20)), ev(2, "B", at(9, 30), at(10)), ev(3, "C", at(10), at(11))), at(8), true, true, utc)
        val timed = items.filterIsInstance<AgendaItem.Timed>()
        assertEquals(listOf(true, false, true), timed.map { it.hourStart })
    }

    @Test fun finishedEventsCanBeHidden() {
        val events = listOf(ev(1, "Done", at(8), at(9)), ev(2, "Next", at(15), at(16)))
        val shown = AgendaModel.build(events, at(12), true, showPast = false, tz = utc)
        assertEquals(listOf("Next"), shown.filterIsInstance<AgendaItem.Timed>().map { it.event.title })
    }

    @Test fun tomorrowHasNoNowLineAndNothingIsPast() {
        val items = AgendaModel.build(listOf(ev(1, "Gym", at(7), at(8))), at(20), isToday = false, showPast = false, tz = utc)
        assertEquals(1, items.size)
        assertTrue(!(items[0] as AgendaItem.Timed).past)
    }

    @Test fun anAllPastDayEndsWithTheNowLine() {
        val items = AgendaModel.build(listOf(ev(1, "Early", at(7), at(8))), at(20), true, true, utc)
        assertTrue(items.last() is AgendaItem.NowLine)
    }

    @Test fun emptyDayHasNoRows() {
        assertTrue(AgendaModel.build(emptyList(), at(12), true, true, utc).isEmpty())
    }

    // ---- Now Playing ------------------------------------------------------------------------------------------------

    private fun track(playing: Boolean, pos: Long, at: Long, dur: Long = 200_000L, speed: Float = 1f) =
        NowPlaying("T", "A", playing, "pkg", durationMs = dur, positionMs = pos, positionAtMs = at, speed = speed)

    @Test fun aPlayingTrackAdvancesAndAPausedOneDoesNot() {
        assertEquals(40_000L, MediaProgress.position(track(true, 30_000, 1_000), 11_000))
        assertEquals(30_000L, MediaProgress.position(track(false, 30_000, 1_000), 11_000))
    }

    @Test fun positionNeverPassesTheEndOrGoesNegative() {
        assertEquals(200_000L, MediaProgress.position(track(true, 199_000, 0), 60_000))
        assertEquals(0L, MediaProgress.position(track(false, -5, 0), 0))
        assertEquals(0f, MediaProgress.fraction(track(true, 10, 0, dur = 0), 100), 0f)
    }

    @Test fun speedScalesTheAdvance() {
        assertEquals(20_000L, MediaProgress.position(track(true, 0, 0, speed = 2f), 10_000))
    }

    @Test fun clockFormatsMinutesAndHours() {
        assertEquals("3:07", MediaProgress.clock(187_000))
        assertEquals("1:02:09", MediaProgress.clock(3_729_000))
        assertEquals("0:00", MediaProgress.clock(-1))
    }

    // ---- Move -------------------------------------------------------------------------------------------------------

    @Test fun durationsReadNaturally() {
        assertEquals("45m", MoveFormat.duration(45)); assertEquals("8h", MoveFormat.duration(480)); assertEquals("7h 12m", MoveFormat.duration(432))
    }

    @Test fun progressIsClampedAndSafeForZeroGoal() {
        assertEquals(0.5f, MoveFormat.progress(4000, 8000), 0f)
        assertEquals(1f, MoveFormat.progress(20_000, 8000), 0f)
        assertEquals(0f, MoveFormat.progress(100, 0), 0f)
    }

    @Test fun weekBarsScaleToTheBestDayOrTheGoal() {
        val quiet = MoveFormat.bars(listOf(1000, 2000, 0, 500, 0, 0, 1500), 8000)
        assertEquals(0.25f, quiet[1], 0.001f)                      // a quiet week stays visibly quiet
        val big = MoveFormat.bars(listOf(0, 16_000, 0, 0, 0, 0, 8000), 8000)
        assertEquals(1f, big[1], 0f); assertEquals(0.5f, big[6], 0.001f)
        assertEquals(7, MoveFormat.bars(List(7) { 0L }, 0).size)
    }

    @Test fun sleepNotesComparedWithTheGoal() {
        assertEquals("You reached your goal.", MoveFormat.sleepNote(480, 480))
        assertEquals("Just short of your goal.", MoveFormat.sleepNote(460, 480))
        assertEquals("1h 30m short of your goal.", MoveFormat.sleepNote(390, 480))
    }

    // ---- Agenda extras ----------------------------------------------------------------------------------------------

    @Test fun freeStretchesAppearBetweenEventsOnlyWhenLongEnough() {
        val items = AgendaModel.build(listOf(ev(1, "A", at(9), at(10)), ev(2, "B", at(10, 15), at(11)), ev(3, "C", at(13), at(14))), at(8), true, true, utc)
        val withGaps = AgendaModel.withGaps(items)
        val free = withGaps.filterIsInstance<AgendaItem.Free>()
        assertEquals(1, free.size)
        assertEquals(at(11), free[0].startMs); assertEquals(at(13), free[0].endMs)
        assertEquals("2h", AgendaModel.span(free[0].endMs - free[0].startMs))
    }

    @Test fun overlappingEventsLeaveNoFalseGap() {
        val items = AgendaModel.build(listOf(ev(1, "Long", at(9), at(12)), ev(2, "Short", at(9, 30), at(10)), ev(3, "Next", at(12), at(13))), at(8), true, true, utc)
        assertTrue(AgendaModel.withGaps(items).none { it is AgendaItem.Free })
    }

    @Test fun upNextIsWhatIsOnOtherwiseWhatStartsNext() {
        val events = listOf(ev(1, "On", at(10), at(11)), ev(2, "Next", at(12), at(13)), ev(3, "Holiday", at(0), at(23), allDay = true))
        assertEquals("On", AgendaModel.upNext(events, at(10, 30))?.title)
        assertEquals("Next", AgendaModel.upNext(events, at(11, 30))?.title)
        assertEquals(null, AgendaModel.upNext(events, at(14)))
    }

    @Test fun countdownSaysStartOrEnd() {
        val e = ev(1, "X", at(10), at(11))
        assertEquals("in 30 min", AgendaModel.countdown(e, at(9, 30)))
        assertEquals("in 2 h 5 min", AgendaModel.countdown(e, at(7, 55)))
        assertEquals("ends in 20 min", AgendaModel.countdown(e, at(10, 40)))
    }

    // ---- Move extras ------------------------------------------------------------------------------------------------

    @Test fun estimatesAndAverages() {
        assertEquals(4.89, MoveFormat.distanceKm(6420), 0.01)
        assertEquals(256, MoveFormat.kcal(6420))
        assertEquals(5000L, MoveFormat.average(listOf(0, 4000, 6000)))
        assertEquals(null, MoveFormat.average(listOf(0, 0)))
        assertEquals(0.5f, MoveFormat.position(70, 50, 90), 0f)
        assertEquals(0.5f, MoveFormat.position(70, 70, 70), 0f)
    }
}
