package com.snapkode.nivolauncher.pagelet.weather

import android.content.Context

/** The last good response and when we may ask again. Kept in its own file so it isn't dragged into layout backups. */
class PrefsWeatherStore(context: Context) : WeatherStore {
    private val prefs = context.getSharedPreferences("nivo_weather_met", Context.MODE_PRIVATE)

    override fun load(): WeatherStore.Entry? {
        val key = prefs.getString("key", null) ?: return null
        val body = prefs.getString("body", null) ?: return null
        return WeatherStore.Entry(key, body, prefs.getLong("expires", 0L), prefs.getString("lastModified", null), prefs.getLong("fetchedAt", 0L), prefs.getString("place", "").orEmpty())
    }

    override fun save(e: WeatherStore.Entry) {
        prefs.edit().putString("key", e.key).putString("body", e.body).putLong("expires", e.expiresMs)
            .putString("lastModified", e.lastModified).putLong("fetchedAt", e.fetchedAtMs).putString("place", e.place).apply()
    }

    override fun clear() = prefs.edit().clear().apply()

    /** Earliest time any request may be made, after a failure. */
    override var notBeforeMs: Long
        get() = prefs.getLong("notBefore", 0L)
        set(v) { prefs.edit().putLong("notBefore", v).apply() }

    /** Random extra delay chosen at fetch time so installs don't all refresh the instant a forecast expires. */
    override var jitterMs: Long
        get() = prefs.getLong("jitter", 0L)
        set(v) { prefs.edit().putLong("jitter", v).apply() }
}
