package com.snapkode.nivolauncher.data

enum class ThemeSchedule(val label: String) {
    Off("Off"),
    /** Dark from 19:00 to 07:00. No location needed. */
    Evening("Evening to morning"),
    Custom("Custom hours"),
}

/** Minutes since midnight for the fixed "evening" window. */
object ThemeScheduleDefaults {
    const val EVENING_START = 19 * 60
    const val MORNING_END = 7 * 60
}

object ThemeScheduler {
    /**
     * Is [minuteOfDay] inside the dark window [start, end)? The window may cross midnight
     * (e.g. 19:00–07:00), and start == end means "never dark".
     */
    fun isDark(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    /** Minutes from [minuteOfDay] until the theme next flips (1..1440). */
    fun minutesUntilChange(minuteOfDay: Int, start: Int, end: Int): Int {
        if (start == end) return 24 * 60
        val target = if (isDark(minuteOfDay, start, end)) end else start
        val delta = (target - minuteOfDay + 24 * 60) % (24 * 60)
        return if (delta == 0) 24 * 60 else delta
    }
}
