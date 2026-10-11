package com.snapkode.nivolauncher.pagelet.weather

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset in minutes since local midnight, or null when the sun doesn't rise or set that day (polar day/night). */
data class SunTimes(val sunriseMin: Int?, val sunsetMin: Int?, val polarDay: Boolean = false, val polarNight: Boolean = false)

/**
 * NOAA's solar position algorithm (the one behind their sunrise/sunset calculator), so sunrise and sunset work offline and
 * need no network. Accurate to about a minute at normal latitudes.
 */
object SolarTimes {
    fun forDate(lat: Double, lon: Double, year: Int, month: Int, day: Int, tz: TimeZone): SunTimes {
        // Evaluate at local noon of that date, which is what NOAA's tables do.
        val noon = Calendar.getInstance(tz).apply { clear(); set(year, month - 1, day, 12, 0, 0) }
        val tzHours = tz.getOffset(noon.timeInMillis) / 3_600_000.0
        val jd = julianDay(year, month, day) // at 0h UT
        val result = DoubleArray(2) { Double.NaN }
        var polar = 0
        for ((i, rising) in listOf(true, false).withIndex()) {
            // Iterate once: first estimate at noon UT, then refine at the estimated event time.
            var tUtcMin = 720.0 - 4.0 * lon                      // minutes after 0h UT, local solar noon estimate
            repeat(2) {
                val t = (jd + tUtcMin / 1440.0 - 2451545.0) / 36525.0
                val decl = solarDeclination(t)
                val eq = equationOfTime(t)
                val ha = hourAngle(lat, decl)
                if (ha.isNaN()) { polar = if (lat * decl > 0) 1 else -1; return@repeat }
                val haDeg = Math.toDegrees(ha)
                tUtcMin = 720.0 - 4.0 * (lon + (if (rising) haDeg else -haDeg)) - eq
            }
            if (!tUtcMin.isNaN() && polar == 0) result[i] = tUtcMin
        }
        if (polar != 0) return SunTimes(null, null, polarDay = polar > 0, polarNight = polar < 0)
        fun toLocalMin(utcMin: Double): Int {
            var m = utcMin + tzHours * 60.0
            m = ((m % 1440.0) + 1440.0) % 1440.0
            return Math.round(m).toInt() % 1440
        }
        return SunTimes(toLocalMin(result[0]), toLocalMin(result[1]))
    }

    private fun rad(d: Double) = d * PI / 180.0

    private fun julianDay(y: Int, m: Int, d: Int): Double {
        var yy = y; var mm = m
        if (mm <= 2) { yy -= 1; mm += 12 }
        val a = yy / 100
        val b = 2 - a + a / 4
        return Math.floor(365.25 * (yy + 4716)) + Math.floor(30.6001 * (mm + 1)) + d + b - 1524.5
    }

    private fun solarDeclination(t: Double): Double {
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(rad(omega))
        return asin(sin(rad(eps)) * sin(rad(lambda)))
    }

    private fun equationOfTime(t: Double): Double {
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(rad(125.04 - 1934.136 * t))
        val y = tan(rad(eps) / 2).let { it * it }
        val sin2l0 = sin(2 * rad(l0)); val sinm = sin(rad(m)); val cos2l0 = cos(2 * rad(l0)); val sin4l0 = sin(4 * rad(l0)); val sin2m = sin(2 * rad(m))
        val eq = y * sin2l0 - 2 * e * sinm + 4 * e * y * sinm * cos2l0 - 0.5 * y * y * sin4l0 - 1.25 * e * e * sin2m
        return Math.toDegrees(eq) * 4.0
    }

    /** Hour angle of the sun's centre at -0.833° (refraction + radius), in radians, or NaN if it never rises/sets. */
    private fun hourAngle(lat: Double, decl: Double): Double {
        val cosH = cos(rad(90.833)) / (cos(rad(lat)) * cos(decl)) - tan(rad(lat)) * tan(decl)
        return if (cosH > 1.0 || cosH < -1.0) Double.NaN else acos(cosH)
    }
}
