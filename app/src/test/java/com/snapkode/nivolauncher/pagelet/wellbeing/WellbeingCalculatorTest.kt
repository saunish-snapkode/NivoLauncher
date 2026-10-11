package com.snapkode.nivolauncher.pagelet.wellbeing

import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.ACTIVITY_PAUSED
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.ACTIVITY_RESUMED
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.ACTIVITY_STOPPED
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.KEYGUARD_HIDDEN
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.SCREEN_INTERACTIVE
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellbeingCalculatorTest {
    private val day = 1_000_000_000_000L          // an arbitrary "midnight"
    private val H = 3_600_000L
    private val M = 60_000L
    private fun at(h: Int, m: Int = 0, s: Int = 0) = day + h * H + m * M + s * 1000L
    private fun ev(type: Int, pkg: String, t: Long) = RawEvent(type, pkg, t)
    private fun calc(vararg e: RawEvent, now: Long = at(23, 59), excluded: Set<String> = setOf("launcher")) =
        WellbeingCalculator.compute(e.toList(), day, now, excluded)

    @Test fun oneSessionIsItsLength() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(10)), ev(ACTIVITY_PAUSED, "a", at(10, 30)))
        assertEquals(30, d.totalMinutes)
        assertEquals(30, d.hourlyMinutes[10]); assertEquals(30, d.hourlyMinutes.sum())
    }

    @Test fun aSessionAcrossAnHourBoundaryIsSplit() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(10, 50)), ev(ACTIVITY_PAUSED, "a", at(11, 10)))
        assertEquals(10, d.hourlyMinutes[10]); assertEquals(10, d.hourlyMinutes[11]); assertEquals(20, d.totalMinutes)
    }

    @Test fun theScreenTurningOffEndsWhateverWasOpen() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(10)), ev(SCREEN_NON_INTERACTIVE, "android", at(10, 20)))
        assertEquals(20, d.totalMinutes)
        assertEquals(listOf(at(10, 20)), d.screenOffTimesMs)
    }

    @Test fun anAppOpenSinceBeforeTheWindowCountsFromTheStart() {
        val d = calc(ev(ACTIVITY_PAUSED, "a", at(0, 15)))
        assertEquals(15, d.totalMinutes); assertEquals(15, d.hourlyMinutes[0])
    }

    @Test fun aSecondPauseForTheSameAppDoesNotInventTime() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(9)), ev(ACTIVITY_PAUSED, "a", at(9, 10)), ev(ACTIVITY_PAUSED, "a", at(9, 40)))
        assertEquals(10, d.totalMinutes)
    }

    @Test fun anAppStillOpenIsClosedAtTheEnd() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(13)), now = at(13, 45))
        assertEquals(45, d.totalMinutes)
    }

    @Test fun excludedPackagesNeverCount() {
        val d = calc(
            ev(ACTIVITY_RESUMED, "launcher", at(8)), ev(ACTIVITY_PAUSED, "launcher", at(9)),
            ev(ACTIVITY_RESUMED, "a", at(9)), ev(ACTIVITY_PAUSED, "a", at(9, 5)),
        )
        assertEquals(5, d.totalMinutes); assertEquals(setOf("a"), d.perAppMs.keys)
    }

    @Test fun overlappingAppsAreCountedOnceButEachKeepsItsOwnTime() {
        val d = calc(
            ev(ACTIVITY_RESUMED, "a", at(10)), ev(ACTIVITY_RESUMED, "b", at(10, 10)),
            ev(ACTIVITY_PAUSED, "a", at(10, 30)), ev(ACTIVITY_PAUSED, "b", at(10, 40)),
        )
        assertEquals("split screen is 40 min of screen, not 60", 40, d.totalMinutes)
        assertEquals(30 * M, d.perAppMs["a"]); assertEquals(30 * M, d.perAppMs["b"])
    }

    @Test fun aDuplicateResumeDoesNotRestartTheClock() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(10)), ev(ACTIVITY_RESUMED, "a", at(10, 20)), ev(ACTIVITY_PAUSED, "a", at(10, 30)))
        assertEquals(30, d.totalMinutes)
    }

    @Test fun stoppedEndsASessionLikePaused() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(10)), ev(ACTIVITY_STOPPED, "a", at(10, 12)))
        assertEquals(12, d.totalMinutes)
    }

    @Test fun flickerUnderOneSecondIsIgnored() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", at(10)), ev(ACTIVITY_PAUSED, "a", at(10, 0, 0) + 400))
        assertEquals(0L, d.totalMs); assertTrue(d.appSessions.isEmpty())
    }

    @Test fun eventsOutsideTheWindowAreIgnored() {
        val d = calc(ev(ACTIVITY_RESUMED, "a", day - H), ev(ACTIVITY_PAUSED, "a", day - H + M), now = at(5))
        assertEquals(0L, d.totalMs)
    }

    @Test fun unlocksAreCountedFromKeyguardHidden() {
        val d = calc(ev(KEYGUARD_HIDDEN, "android", at(7)), ev(KEYGUARD_HIDDEN, "android", at(9)), ev(KEYGUARD_HIDDEN, "android", at(9, 30)))
        assertEquals(3, d.unlocks)
    }

    @Test fun upSinceIsTheFirstUnlockAfterFourAm() {
        val d = calc(ev(KEYGUARD_HIDDEN, "android", at(2)), ev(KEYGUARD_HIDDEN, "android", at(3, 59)), ev(KEYGUARD_HIDDEN, "android", at(6, 30)), ev(KEYGUARD_HIDDEN, "android", at(7)))
        assertEquals(at(6, 30), d.upSinceMs)
        assertEquals("night-time unlocks still count as unlocks", 4, d.unlocks)
    }

    @Test fun noUnlocksMeansNoUpSince() = assertNull(calc().upSinceMs)

    @Test fun oldPhonesFallBackToScreenWakes() {
        val d = calc(ev(SCREEN_INTERACTIVE, "android", at(8)), ev(SCREEN_INTERACTIVE, "android", at(12)))
        assertEquals(2, d.unlocks)
    }

    @Test fun keyguardEventsWinOverScreenWakes() {
        val d = calc(ev(SCREEN_INTERACTIVE, "android", at(8)), ev(SCREEN_INTERACTIVE, "android", at(9)), ev(KEYGUARD_HIDDEN, "android", at(9, 1)))
        assertEquals(1, d.unlocks)
    }

    @Test fun theLongestSessionJoinsUsesWithinHalfAMinute() {
        val d = calc(
            ev(ACTIVITY_RESUMED, "a", at(10)), ev(ACTIVITY_PAUSED, "a", at(10, 10)),
            ev(ACTIVITY_RESUMED, "b", at(10, 10, 20)), ev(ACTIVITY_PAUSED, "b", at(10, 20, 20)),
            ev(ACTIVITY_RESUMED, "c", at(12)), ev(ACTIVITY_PAUSED, "c", at(12, 5)),
        )
        assertEquals(20 * M + 20_000L, d.longestSessionMs)
    }

    @Test fun aGapOverHalfAMinuteStartsANewSession() {
        val d = calc(
            ev(ACTIVITY_RESUMED, "a", at(10)), ev(ACTIVITY_PAUSED, "a", at(10, 10)),
            ev(ACTIVITY_RESUMED, "b", at(10, 11)), ev(ACTIVITY_PAUSED, "b", at(10, 16)),
        )
        assertEquals(10 * M, d.longestSessionMs)
    }

    @Test fun topAppsAreOrderedAndIgnoreTrivialUse() {
        val d = calc(
            ev(ACTIVITY_RESUMED, "a", at(9)), ev(ACTIVITY_PAUSED, "a", at(9, 10)),
            ev(ACTIVITY_RESUMED, "b", at(10)), ev(ACTIVITY_PAUSED, "b", at(10, 40)),
            ev(ACTIVITY_RESUMED, "c", at(11)), ev(ACTIVITY_PAUSED, "c", at(11, 0, 5)),
        )
        assertEquals(listOf("b", "a"), d.topApps(3).map { it.key })
    }

    @Test fun anEmptyDayIsAllZeros() {
        val d = calc()
        assertEquals(0L, d.totalMs); assertEquals(24, d.hourlyMinutes.size); assertTrue(d.hourlyMinutes.all { it == 0 })
        assertEquals(0L, d.longestSessionMs); assertEquals(0, d.unlocks)
    }

    @Test fun hourlyTotalsMatchTheTotal() {
        val d = calc(
            ev(ACTIVITY_RESUMED, "a", at(7, 45)), ev(ACTIVITY_PAUSED, "a", at(9, 20)),
            ev(ACTIVITY_RESUMED, "b", at(22, 50)), ev(ACTIVITY_PAUSED, "b", at(23, 40)),
        )
        assertEquals(d.totalMinutes, d.hourlyMinutes.sum())
    }

    @Test fun eventsInAnyOrderGiveTheSameAnswer() {
        val a = ev(ACTIVITY_RESUMED, "a", at(10)); val b = ev(ACTIVITY_PAUSED, "a", at(10, 30))
        assertEquals(calc(a, b).totalMs, calc(b, a).totalMs)
    }

    @Test fun mergeJoinsOverlapsAndSmallGapsOnly() {
        val m = WellbeingCalculator.mergeIntervals(listOf(0L to 10L, 5L to 20L, 25L to 30L, 100L to 110L), gapMs = 5L)
        assertEquals(listOf(0L to 30L, 100L to 110L), m)
        assertEquals(emptyList<Pair<Long, Long>>(), WellbeingCalculator.mergeIntervals(emptyList(), 0))
    }
}
