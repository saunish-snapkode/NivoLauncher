package com.snapkode.nivolauncher.pagelet.wellbeing

import android.content.Context
import com.snapkode.nivolauncher.pagelet.UsageAccess
import com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyAppUsage
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyWellbeing
import java.util.Calendar

/** Today's screen time for the Sky, computed from Android's usage log. Reads nothing unless Usage access is allowed. */
class WellbeingSource(private val context: Context, private val reader: UsageStatsReader = UsageStatsReader(context)) {
    private var cachedAt = 0L
    private var cached: WellbeingDay? = null
    private var yesterdayDay = -1
    private var yesterdayMin: Int? = null

    /** Blocking; call off the main thread. Results are reused for 20 s so redraws and repeated checks stay cheap. */
    fun read(nowMs: Long): Pair<SectionStatus, SkyWellbeing?> {
        if (!UsageAccess.isGranted(context)) { cached = null; return SectionStatus.NeedsPermission to null }
        val start = reader.dayStart(nowMs)
        val today = cached?.takeIf { it.dayStartMs == start && nowMs - cachedAt < 20_000L } ?: reader.day(start, nowMs).also { cached = it; cachedAt = nowMs }
        val dayKey = Calendar.getInstance().apply { timeInMillis = start }.get(Calendar.DAY_OF_YEAR)
        if (yesterdayDay != dayKey) {
            val yStart = reader.dayStart(start - 12 * 3_600_000L)
            yesterdayMin = reader.day(yStart, start).totalMinutes.takeIf { it > 0 }
            yesterdayDay = dayKey
        }
        val cal = Calendar.getInstance()
        val up = today.upSinceMs?.let { cal.timeInMillis = it; cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f }
        return SectionStatus.Ready to SkyWellbeing(
            hourlyMinutes = today.hourlyMinutes,
            unlocks = today.unlocks,
            upSinceHour = up,
            longestSessionMin = (today.longestSessionMs / 60_000L).toInt(),
            topApps = today.topApps(3).map { SkyAppUsage(it.key, reader.label(it.key), (it.value / 60_000L).toInt().coerceAtLeast(1)) },
            yesterdayTotalMin = yesterdayMin,
        )
    }
}
