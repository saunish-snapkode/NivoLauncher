package com.snapkode.nivolauncher.pagelet.weather

/** One hour of the forecast. */
data class HourlyPoint(val epochMs: Long, val tempC: Double, val symbolCode: String)

/** What the Sky needs from a MET Norway Locationforecast response, already reduced to plain numbers. */
data class MetForecast(
    /** MET's `symbol_code`, e.g. `partlycloudy_day`. */
    val symbolCode: String,
    val tempC: Double,
    val humidityPct: Double,
    val windMs: Double,
    val cloudPct: Double,
    /** Today's (local day) lowest and highest temperature in the series. */
    val todayMinC: Double,
    val todayMaxC: Double,
    /** The next six hours. */
    val hourly: List<HourlyPoint>,
    /** When MET produced this forecast. */
    val updatedAtMs: Long,
) {
    val feelsLikeC: Double get() = FeelsLike.celsius(tempC, humidityPct, windMs)
}
