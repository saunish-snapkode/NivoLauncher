package com.snapkode.nivolauncher.pagelet

import com.snapkode.nivolauncher.data.NewsTopic
import com.snapkode.nivolauncher.data.news.NewsPrefs
import org.json.JSONArray
import org.json.JSONObject

/** The parts of the Today (Sky) Pagelet a user can turn on and off. */
enum class SkySection(val key: String, val label: String) {
    TIME("time", "Time"),
    WEATHER("weather", "Weather"),
    WELLBEING("wellbeing", "Wellbeing"),
    CALENDAR("calendar", "Calendar"),
    STEPS("steps", "Steps"),
}

/** The parts of the Move Pagelet a user can turn on and off. */
enum class MoveSection(val key: String, val label: String) {
    STEPS("steps", "Steps"),
    SLEEP("sleep", "Sleep"),
    HEART("heart", "Heart rate"),
}

/**
 * Per-Pagelet settings. Serialised as JSON under one SharedPreferences key per Pagelet, in the same file as the layout, so
 * backup and restore carry it along. Reading is forgiving: unknown fields are ignored and bad values fall back to defaults.
 */
data class PageletConfig(
    /** Fade the dock out as this page scrolls in, and back as it scrolls away. Off by default: the dock stays on every Pagelet. */
    val hideDock: Boolean = false,
    /** Which sections of the Pagelet are shown. */
    val sections: Set<String> = DEFAULT_SKY_SECTIONS,
    /** Daily screen-time goal in minutes, used by the Sky wellbeing section. */
    val goalMinutes: Int = 240,
    /** Daily step goal for the Steps section. */
    val stepGoal: Int = 8000,
    /** Headlines: which sources, and how the page looks. */
    val news: NewsPrefs = NewsPrefs(),
    /** Headlines: the topic chip selected. */
    val newsTopic: NewsTopic = NewsTopic.Top,
    /** Agenda: keep events that are already over in the list, dimmed. */
    val showPast: Boolean = true,
    /** Move: the sleep goal in minutes. */
    val sleepGoalMin: Int = 8 * 60,
) {
    fun has(section: SkySection) = section.key in sections
    fun has(section: MoveSection) = section.key in sections

    fun toJson(): String = JSONObject()
        .put("hideDock", hideDock)
        .put("sections", JSONArray(sections.sorted()))
        .put("goalMinutes", goalMinutes)
        .put("stepGoal", stepGoal)
        .put("news", news.toJson())
        .put("newsTopic", newsTopic.name)
        .put("showPast", showPast)
        .put("sleepGoalMin", sleepGoalMin)
        .toString()

    companion object {
        val DEFAULT_SKY_SECTIONS = setOf(SkySection.TIME.key, SkySection.WEATHER.key, SkySection.WELLBEING.key, SkySection.CALENDAR.key, SkySection.STEPS.key)

        val DEFAULT_MOVE_SECTIONS = MoveSection.entries.map { it.key }.toSet()

        fun defaultsFor(type: PageletType) = when (type) {
            PageletType.SKY -> PageletConfig()
            PageletType.MOVE -> PageletConfig(sections = DEFAULT_MOVE_SECTIONS)
            else -> PageletConfig(sections = emptySet())
        }

        fun fromJson(raw: String?, type: PageletType? = null): PageletConfig {
            val fallback = if (type != null) defaultsFor(type) else PageletConfig()
            if (raw.isNullOrBlank()) return fallback
            return runCatching {
                val o = JSONObject(raw)
                val sections = o.optJSONArray("sections")?.let { a ->
                    val known = SkySection.entries.map { it.key }.toSet() + MoveSection.entries.map { it.key }
                    List(a.length()) { a.optString(it) }.filter { it in known }.toSet()
                } ?: fallback.sections
                PageletConfig(
                    hideDock = o.optBoolean("hideDock", fallback.hideDock),
                    sections = sections,
                    goalMinutes = o.optInt("goalMinutes", fallback.goalMinutes).coerceIn(15, 24 * 60),
                    stepGoal = o.optInt("stepGoal", fallback.stepGoal).coerceIn(500, 100_000),
                    news = NewsPrefs.fromJson(o.optJSONObject("news"), fallback.news),
                    newsTopic = NewsTopic.entries.firstOrNull { it.name == o.optString("newsTopic") } ?: fallback.newsTopic,
                    showPast = o.optBoolean("showPast", fallback.showPast),
                    sleepGoalMin = o.optInt("sleepGoalMin", fallback.sleepGoalMin).coerceIn(60, 16 * 60),
                )
            }.getOrDefault(fallback)
        }
    }
}
