package com.snapkode.nivolauncher.pagelet.weather

import kotlin.math.pow

/**
 * "Feels like": the heat index above 27 °C, wind chill below 10 °C (with enough wind for it to matter), otherwise simply the
 * air temperature.
 */
object FeelsLike {
    fun celsius(tempC: Double, humidityPct: Double, windMs: Double): Double = when {
        tempC >= 27.0 -> heatIndexC(tempC, humidityPct).coerceAtLeast(tempC)
        tempC <= 10.0 && windMs * 3.6 > 4.8 -> windChillC(tempC, windMs)
        else -> tempC
    }

    /** NWS Rothfusz regression, in °F internally. */
    fun heatIndexC(tempC: Double, rh: Double): Double {
        val t = tempC * 9.0 / 5.0 + 32.0
        val hi = -42.379 + 2.04901523 * t + 10.14333127 * rh - 0.22475541 * t * rh - 6.83783e-3 * t * t -
            5.481717e-2 * rh * rh + 1.22874e-3 * t * t * rh + 8.5282e-4 * t * rh * rh - 1.99e-6 * t * t * rh * rh
        return (hi - 32.0) * 5.0 / 9.0
    }

    /** Environment Canada / NWS wind chill: temperature in °C, wind in km/h. */
    fun windChillC(tempC: Double, windMs: Double): Double {
        val v = (windMs * 3.6).pow(0.16)
        return 13.12 + 0.6215 * tempC - 11.37 * v + 0.3965 * tempC * v
    }
}
