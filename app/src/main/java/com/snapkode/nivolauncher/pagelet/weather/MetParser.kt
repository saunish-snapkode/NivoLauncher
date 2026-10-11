package com.snapkode.nivolauncher.pagelet.weather

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Reads MET Norway Locationforecast 2.0 "compact" JSON. Pure, so it is tested against recorded responses. */
object MetParser {
    private fun iso(s: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time
    }.getOrNull()

    /**
     * @param nowMs  the current time; the "current" reading is the latest entry at or before it.
     * @param tz     the zone that defines "today" for the high and low.
     * @return null if the body isn't a usable forecast.
     */
    fun parse(body: String, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): MetForecast? = runCatching {
        val root = JSONObject(body)
        val props = root.getJSONObject("properties")
        val updated = iso(props.optJSONObject("meta")?.optString("updated_at").orEmpty()) ?: nowMs
        val series = props.getJSONArray("timeseries")
        class Entry(val t: Long, val o: JSONObject)
        val entries = ArrayList<Entry>(series.length())
        for (i in 0 until series.length()) {
            val o = series.getJSONObject(i)
            val t = iso(o.optString("time")) ?: continue
            entries += Entry(t, o)
        }
        if (entries.isEmpty()) return null
        entries.sortBy { it.t }
        val currentIdx = entries.indexOfLast { it.t <= nowMs }.let { if (it < 0) 0 else it }
        val cur = entries[currentIdx]
        val d = cur.o.getJSONObject("data")
        val details = d.getJSONObject("instant").getJSONObject("details")
        if (!details.has("air_temperature")) return null

        fun symbolOf(data: JSONObject): String? =
            listOf("next_1_hours", "next_6_hours", "next_12_hours")
                .firstNotNullOfOrNull { data.optJSONObject(it)?.optJSONObject("summary")?.optString("symbol_code")?.takeIf { s -> s.isNotBlank() } }

        // Today's extremes over every entry whose local date is today.
        fun dayKey(ms: Long) = Calendar.getInstance(tz).apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        val today = dayKey(nowMs)
        var lo = details.getDouble("air_temperature"); var hi = lo
        for (e in entries) {
            if (dayKey(e.t) != today) continue
            val temp = e.o.optJSONObject("data")?.optJSONObject("instant")?.optJSONObject("details")?.optDouble("air_temperature", Double.NaN) ?: continue
            if (!temp.isNaN()) { lo = minOf(lo, temp); hi = maxOf(hi, temp) }
        }

        val hourly = entries.drop(currentIdx).take(6).mapNotNull { e ->
            val data = e.o.optJSONObject("data") ?: return@mapNotNull null
            val temp = data.optJSONObject("instant")?.optJSONObject("details")?.optDouble("air_temperature", Double.NaN) ?: return@mapNotNull null
            if (temp.isNaN()) null else HourlyPoint(e.t, temp, symbolOf(data) ?: "cloudy")
        }

        MetForecast(
            symbolCode = symbolOf(d) ?: "cloudy",
            tempC = details.getDouble("air_temperature"),
            humidityPct = details.optDouble("relative_humidity", 50.0),
            windMs = details.optDouble("wind_speed", 0.0),
            cloudPct = details.optDouble("cloud_area_fraction", 0.0),
            todayMinC = lo, todayMaxC = hi,
            hourly = hourly,
            updatedAtMs = updated,
        )
    }.getOrNull()
}
