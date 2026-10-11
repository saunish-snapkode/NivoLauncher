package com.snapkode.nivolauncher.pagelet.wellbeing

/** `UsageEvents.Event` type codes (the values are part of the Android API; named here so the maths is testable off-device). */
object UsageEventType {
    const val ACTIVITY_RESUMED = 1       // MOVE_TO_FOREGROUND
    const val ACTIVITY_PAUSED = 2        // MOVE_TO_BACKGROUND
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val ACTIVITY_STOPPED = 23
}

class RawEvent(val type: Int, val pkg: String, val timeMs: Long)

/** One app being used, from [startMs] to [endMs]. */
class AppSession(val pkg: String, val startMs: Long, val endMs: Long) {
    val durationMs get() = endMs - startMs
}

/** Everything the Today page and the Activity log need about one day of phone use. */
class WellbeingDay(
    val dayStartMs: Long,
    val endMs: Long,
    /** Screen time with overlaps counted once (split-screen isn't double). */
    val totalMs: Long,
    /** Minutes of use in each of the day's 24 hours, rounded. */
    val hourlyMinutes: List<Int>,
    val perAppMs: Map<String, Long>,
    val appSessions: List<AppSession>,
    val unlockTimesMs: List<Long>,
    val screenOffTimesMs: List<Long>,
    /** The first unlock after 4 AM, or null. */
    val upSinceMs: Long?,
    val longestSessionMs: Long,
) {
    val unlocks get() = unlockTimesMs.size
    val totalMinutes get() = (totalMs / 60_000L).toInt()

    fun topApps(n: Int) = perAppMs.entries.sortedByDescending { it.value }.take(n).filter { it.value >= 30_000L }
}

/**
 * Turns the raw foreground/background/screen events Android keeps into screen time. Rules, in the order they matter:
 *  - An app is "in use" from RESUMED until PAUSED/STOPPED, and never past the screen turning off.
 *  - Apps listed in [excluded] (this launcher, System UI, other home screens) don't count: sitting on a home screen
 *    isn't screen time.
 *  - A PAUSED with no RESUMED before it means the app was already open at the start of the window.
 *  - Anything still open at the end is closed at the end (now, for today).
 *  - Overlapping use (split screen) counts once.
 *  - Uses closer than [mergeGapMs] are one "session".
 */
object WellbeingCalculator {
    const val HOUR_MS = 3_600_000L
    private const val MIN_SESSION_MS = 1_000L

    fun compute(
        events: List<RawEvent>,
        dayStartMs: Long,
        endMs: Long,
        excluded: Set<String>,
        mergeGapMs: Long = 30_000L,
    ): WellbeingDay {
        val sorted = events.filter { it.timeMs in dayStartMs..endMs }.sortedBy { it.timeMs }
        val open = HashMap<String, Long>()
        val seenPkg = HashSet<String>()
        val sessions = ArrayList<AppSession>()
        val unlocks = ArrayList<Long>()
        val screenOffs = ArrayList<Long>()
        var interactiveCount = 0
        val interactiveTimes = ArrayList<Long>()

        fun close(pkg: String, at: Long) {
            val start = open.remove(pkg) ?: return
            if (at - start >= MIN_SESSION_MS) sessions += AppSession(pkg, start, at)
        }

        for (e in sorted) {
            when (e.type) {
                UsageEventType.ACTIVITY_RESUMED -> {
                    seenPkg += e.pkg
                    if (e.pkg !in excluded && e.pkg !in open) open[e.pkg] = e.timeMs
                }
                UsageEventType.ACTIVITY_PAUSED, UsageEventType.ACTIVITY_STOPPED -> {
                    if (e.pkg in excluded) { seenPkg += e.pkg; continue }
                    if (e.pkg in open) close(e.pkg, e.timeMs)
                    else if (e.pkg !in seenPkg) {
                        // Open since before the window began.
                        if (e.timeMs - dayStartMs >= MIN_SESSION_MS) sessions += AppSession(e.pkg, dayStartMs, e.timeMs)
                    }
                    seenPkg += e.pkg
                }
                UsageEventType.SCREEN_NON_INTERACTIVE -> {
                    screenOffs += e.timeMs
                    open.keys.toList().forEach { close(it, e.timeMs) }
                }
                UsageEventType.SCREEN_INTERACTIVE -> { interactiveCount++; interactiveTimes += e.timeMs }
                UsageEventType.KEYGUARD_HIDDEN -> unlocks += e.timeMs
            }
        }
        open.keys.toList().forEach { close(it, endMs) }

        // Phones before Android 9 don't report the keyguard: waking the screen is the closest honest stand-in.
        val unlockTimes = if (unlocks.isNotEmpty() || interactiveCount == 0) unlocks else interactiveTimes

        val merged = mergeIntervals(sessions.map { it.startMs to it.endMs }, 0L)
        val total = merged.sumOf { it.second - it.first }

        val hourMs = LongArray(24)
        for ((s, e) in merged) {
            var t = s
            while (t < e) {
                val h = ((t - dayStartMs) / HOUR_MS).toInt().coerceIn(0, 23)
                val boundary = if (h == 23) Long.MAX_VALUE else dayStartMs + (h + 1) * HOUR_MS
                val slice = minOf(e, boundary) - t
                if (slice <= 0) break
                hourMs[h] += slice
                t += slice
            }
        }

        val longest = mergeIntervals(sessions.map { it.startMs to it.endMs }, mergeGapMs).maxOfOrNull { it.second - it.first } ?: 0L
        val perApp = sessions.groupBy { it.pkg }.mapValues { (_, v) -> v.sumOf { it.durationMs } }
        val upSince = unlockTimes.filter { it >= dayStartMs + 4 * HOUR_MS }.minOrNull()

        return WellbeingDay(
            dayStartMs = dayStartMs, endMs = endMs, totalMs = total,
            hourlyMinutes = hourMs.map { Math.round(it / 60_000.0).toInt() },
            perAppMs = perApp, appSessions = sessions.sortedBy { it.startMs },
            unlockTimesMs = unlockTimes.sorted(), screenOffTimesMs = screenOffs,
            upSinceMs = upSince, longestSessionMs = longest,
        )
    }

    /** Sorts and joins intervals that overlap or are no more than [gapMs] apart. */
    fun mergeIntervals(intervals: List<Pair<Long, Long>>, gapMs: Long): List<Pair<Long, Long>> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.first }
        val out = ArrayList<Pair<Long, Long>>()
        var cs = sorted[0].first; var ce = sorted[0].second
        for (i in 1 until sorted.size) {
            val (s, e) = sorted[i]
            if (s <= ce + gapMs) ce = maxOf(ce, e) else { out += cs to ce; cs = s; ce = e }
        }
        out += cs to ce
        return out
    }
}
