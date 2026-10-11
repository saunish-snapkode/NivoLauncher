package com.snapkode.nivolauncher.pagelet.wellbeing

import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.ACTIVITY_PAUSED
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.ACTIVITY_RESUMED
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.KEYGUARD_HIDDEN
import com.snapkode.nivolauncher.pagelet.wellbeing.UsageEventType.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ActivityLogTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 1_700_000_000_000L - (1_700_000_000_000L % 86_400_000L)   // a UTC midnight
    private val H = 3_600_000L; private val M = 60_000L
    private fun at(h: Int, m: Int = 0) = day + h * H + m * M
    private fun ev(type: Int, pkg: String, t: Long) = RawEvent(type, pkg, t)

    private val d = WellbeingCalculator.compute(
        listOf(
            ev(KEYGUARD_HIDDEN, "android", at(7)),
            ev(ACTIVITY_RESUMED, "com.a", at(7, 1)), ev(ACTIVITY_PAUSED, "com.a", at(7, 20)),
            ev(SCREEN_NON_INTERACTIVE, "android", at(7, 21)),
            ev(KEYGUARD_HIDDEN, "android", at(12, 30)),
            ev(ACTIVITY_RESUMED, "com.b,\"x\"", at(12, 31)), ev(ACTIVITY_PAUSED, "com.b,\"x\"", at(12, 41)),
        ),
        day, at(23, 59), excluded = emptySet(),
    )

    @Test fun allShowsEverythingInTimeOrder() {
        val e = ActivityLog.entries(d, ActivityFilter.All)
        assertEquals(listOf("Unlock", "AppUse", "ScreenOff", "Unlock", "AppUse"), e.map { it.javaClass.simpleName })
        assertEquals(e.sortedBy { it.timeMs }.map { it.timeMs }, e.map { it.timeMs })
    }

    @Test fun unlocksFilterShowsOnlyUnlocks() =
        assertEquals(listOf(at(7), at(12, 30)), ActivityLog.entries(d, ActivityFilter.Unlocks).map { it.timeMs })

    @Test fun appUseFilterShowsOnlyAppSessions() {
        val e = ActivityLog.entries(d, ActivityFilter.AppUse)
        assertEquals(2, e.size); assertTrue(e.all { it is ActivityEntry.AppUse })
    }

    @Test fun hoursAreGroupedEarliestFirst() {
        val g = ActivityLog.byHour(ActivityLog.entries(d), utc)
        assertEquals(listOf(7, 12), g.map { it.first })
        assertEquals(3, g[0].second.size); assertEquals(2, g[1].second.size)
    }

    @Test fun anEmptyDayHasNoEntriesAndNoGroups() {
        val empty = WellbeingCalculator.compute(emptyList(), day, at(23), emptySet())
        assertTrue(ActivityLog.entries(empty).isEmpty()); assertTrue(ActivityLog.byHour(emptyList(), utc).isEmpty())
    }

    @Test fun csvHasAHeaderOneRowPerEventAndEscapesAwkwardNames() {
        val csv = ActivityLog.csv(d, label = { it }, tz = utc)
        val lines = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals("time,type,app,duration_seconds", lines[0])
        assertEquals(1 + 5, lines.size)
        assertTrue(lines[1].startsWith("2023-") && lines[1].contains(",unlock,,"))
        assertTrue("an app name with a comma and quotes is quoted and doubled", csv.contains("\"com.b,\"\"x\"\"\""))
        assertTrue(csv.contains(",app_use,com.a,1140"))          // 19 min
    }

    @Test fun csvLineEndingsAreCrLf() = assertTrue(ActivityLog.csv(d, { it }, utc).contains("\r\n"))

    @Test fun veryShortUseIsLeftOutOfTheLog() {
        val tiny = WellbeingCalculator.compute(listOf(ev(ACTIVITY_RESUMED, "a", at(9)), ev(ACTIVITY_PAUSED, "a", at(9) + 3_000)), day, at(23), emptySet())
        assertTrue(ActivityLog.entries(tiny, ActivityFilter.AppUse).isEmpty())
    }

    @Test fun dayTitles() {
        val now = at(15)
        assertEquals("Today", ActivityLog.dayTitle(day, now, utc))
        assertEquals("Yesterday", ActivityLog.dayTitle(day - 24 * H, now, utc))
        assertTrue(ActivityLog.dayTitle(day - 3 * 24 * H, now, utc).isNotEmpty())
    }
}
