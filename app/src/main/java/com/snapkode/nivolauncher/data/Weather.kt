package com.snapkode.nivolauncher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(val name: String, val detail: String, val latitude: Double, val longitude: Double)

data class Weather(val celsius: Double, val code: Int, val fetchedAt: Long)

/** Pure helpers, kept separate from networking so they can be unit-tested. */
object WeatherFormat {
    fun fahrenheit(celsius: Double) = celsius * 9.0 / 5.0 + 32.0

    fun temperature(celsius: Double, useFahrenheit: Boolean): String =
        "${Math.round(if (useFahrenheit) fahrenheit(celsius) else celsius)}°"

    /** WMO weather interpretation code -> symbol and plain-language description. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "Clear"
        1 -> "🌤️" to "Mostly clear"
        2 -> "⛅" to "Partly cloudy"
        3 -> "☁️" to "Overcast"
        45, 48 -> "🌫️" to "Fog"
        in 51..57 -> "🌦️" to "Drizzle"
        in 61..67 -> "🌧️" to "Rain"
        in 71..77 -> "❄️" to "Snow"
        in 80..82 -> "🌦️" to "Showers"
        85, 86 -> "🌨️" to "Snow showers"
        in 95..99 -> "⛈️" to "Thunderstorm"
        else -> "🌡️" to "Weather"
    }

    /** Whether cached data is still fresh enough to show without refetching. */
    fun isFresh(fetchedAt: Long, now: Long, maxAgeMinutes: Int = 30) = now - fetchedAt < maxAgeMinutes * 60_000L
}

/**
 * Weather via Open-Meteo (free, no account or API key). Only rounded coordinates are sent, and only
 * after the user turns weather on and picks a place. Blocking: call off the main thread.
 */
object WeatherSource {
    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 4000
        c.setRequestProperty("User-Agent", "NivoLauncher")
        c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }.getOrNull()

    fun search(query: String): List<Place> {
        val body = get("https://geocoding-api.open-meteo.com/v1/search?count=5&language=en&format=json&name=" + URLEncoder.encode(query, "UTF-8")) ?: return emptyList()
        return runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            List(results.length()) {
                val o = results.getJSONObject(it)
                val detail = listOf(o.optString("admin1"), o.optString("country")).filter { s -> s.isNotBlank() }.joinToString(", ")
                Place(o.getString("name"), detail, o.getDouble("latitude"), o.getDouble("longitude"))
            }
        }.getOrDefault(emptyList())
    }

    fun fetch(latitude: Double, longitude: Double, now: Long = System.currentTimeMillis()): Weather? {
        val lat = "%.2f".format(java.util.Locale.US, latitude)
        val lon = "%.2f".format(java.util.Locale.US, longitude)
        val body = get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code&timezone=auto&latitude=$lat&longitude=$lon") ?: return null
        return runCatching {
            val current = JSONObject(body).getJSONObject("current")
            Weather(current.getDouble("temperature_2m"), current.getInt("weather_code"), now)
        }.getOrNull()
    }
}
