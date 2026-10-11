package com.snapkode.nivolauncher.ui.pagelet.sky

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Everything that decides what the Sky looks like, as plain data and pure functions: which time-of-day phase it is, which
 * weather layer applies, the palettes, and the geometry of the 24-hour arc. No drawing happens here, so all of it is
 * covered by JVM unit tests.
 */

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)
private fun hex(v: Long) = Color(0xFF000000 or v)

enum class TimePhase { Dawn, Day, Golden, Dusk, Night }

enum class WeatherLayer { Clear, Cloudy, Fog, Rain, Storm }

enum class Marker { Sun, Moon }

/** The palette and lighting of one time-of-day phase. At most 3 sky colours + 2 ground colours, all desaturated. */
class PhaseStyle(
    val sky0: Color, val sky1: Color, val sky2: Color,
    val ground: Color, val groundEnd: Color,
    /** 0..1: how strongly stars show (only under Clear and Fog). */
    val stars: Float,
    val marker: Marker,
    val sunC: Color, val glow: Color,
    val bloom: Float, val lineOpacity: Float,
    val accent: Color,
    val seg: Color, val segGlow: Color,
) {
    /** Every colour that can end up in the background (not the accent/segment colours, which are foreground). */
    val backgroundColors: List<Color> get() = listOf(sky0, sky1, sky2, ground, groundEnd)
}

object SkyPalettes {
    val Dawn = PhaseStyle(
        hex(0x11141F), hex(0x38324A), hex(0xB88270), hex(0x0D0B10), hex(0x060508), 0.3f, Marker.Sun,
        hex(0xFFC9A3), rgba(240, 160, 120, .55f), 0.95f, 0.22f, hex(0xF5C9B3), hex(0xFFFFFF), rgba(255, 200, 170, .7f),
    )
    val Day = PhaseStyle(
        hex(0x0B1C33), hex(0x284F78), hex(0x8BB0CF), hex(0x0A0F16), hex(0x050709), 0f, Marker.Sun,
        hex(0xFFF0C2), rgba(255, 238, 190, .5f), 0.5f, 0.26f, hex(0xBFE6F0), hex(0xFFFFFF), rgba(255, 255, 255, .75f),
    )
    val Golden = PhaseStyle(
        hex(0x13152A), hex(0x5A4150), hex(0xCF956E), hex(0x0E0B0C), hex(0x060505), 0f, Marker.Sun,
        hex(0xFFC48A), rgba(255, 170, 105, .6f), 1.0f, 0.24f, hex(0xF2CBA4), hex(0xFFFFFF), rgba(255, 190, 130, .8f),
    )
    val Dusk = PhaseStyle(
        hex(0x0A0C1B), hex(0x27203D), hex(0x6A4560), hex(0x08070C), hex(0x040406), 0.6f, Marker.Moon,
        hex(0xE9EEFF), rgba(210, 140, 180, .4f), 0.6f, 0.18f, hex(0xE2C5F2), hex(0xF3DDFF), rgba(235, 195, 255, .7f),
    )
    val Night = PhaseStyle(
        hex(0x030409), hex(0x070B17), hex(0x10172C), hex(0x05060A), hex(0x020203), 1f, Marker.Moon,
        hex(0xE9EEFF), rgba(160, 180, 255, .3f), 0.35f, 0.14f, hex(0xB4C8F5), hex(0xD3E0FF), rgba(170, 200, 255, .75f),
    )

    fun of(phase: TimePhase) = when (phase) {
        TimePhase.Dawn -> Dawn
        TimePhase.Day -> Day
        TimePhase.Golden -> Golden
        TimePhase.Dusk -> Dusk
        TimePhase.Night -> Night
    }
}

/** How a weather layer tints the sky and what ambient particles it adds. */
class WeatherStyle(
    /** Overlay gradient: [overlayTop] at the top to [overlayMid] at 55%, fully transparent by 62%. Null = none. */
    val overlayTop: Color?, val overlayMid: Color?,
    val cloudCount: Int, val cloudMinWidthDp: Int, val cloudMaxWidthDp: Int, val cloudHeightRatio: Float,
    val cloudFill: Color, val cloudBlurDp: Int,
    val fogBands: Int,
    val rainStreaks: Int, val rainMinSec: Float, val rainMaxSec: Float,
    val flash: Boolean,
    /** Multiplies the sun/moon marker's and the bloom's strength. */
    val sunOpacity: Float,
    val showsStars: Boolean,
    /** Black at this strength behind the clock and header (top ~36% of the page), so white text stays ≥ 4.5:1 under light skies. */
    val textScrim: Float = 0f,
)

object SkyWeatherStyles {
    internal const val SCRIM_CLOUDY = 0.30f
    internal const val SCRIM_FOG = 0.30f

    val Clear = WeatherStyle(
        null, null, 2, 220, 300, 0.22f, rgba(255, 255, 255, .18f), 14, 0, 0, 0f, 0f, false, 1.0f, true,
    )
    val Cloudy = WeatherStyle(
        rgba(105, 112, 128, .42f), rgba(140, 148, 165, .28f), 6, 260, 420, 0.34f, rgba(226, 231, 240, .42f), 18,
        0, 0, 0f, 0f, false, 0.5f, false, textScrim = SCRIM_CLOUDY,
    )
    val Fog = WeatherStyle(
        rgba(180, 188, 204, .22f), rgba(215, 222, 232, .42f), 2, 220, 300, 0.22f, rgba(230, 234, 240, .3f), 24,
        3, 0, 0f, 0f, false, 0.4f, true, textScrim = SCRIM_FOG,
    )
    val Rain = WeatherStyle(
        rgba(18, 24, 38, .62f), rgba(46, 56, 76, .45f), 6, 260, 420, 0.34f, rgba(130, 140, 162, .5f), 20,
        0, 36, 0.8f, 1.15f, false, 0.2f, false,
    )
    val Storm = WeatherStyle(
        rgba(6, 8, 16, .76f), rgba(28, 32, 48, .58f), 7, 260, 420, 0.34f, rgba(70, 76, 98, .62f), 20,
        0, 56, 0.5f, 0.85f, true, 0.12f, false,
    )

    fun of(layer: WeatherLayer) = when (layer) {
        WeatherLayer.Clear -> Clear
        WeatherLayer.Cloudy -> Cloudy
        WeatherLayer.Fog -> Fog
        WeatherLayer.Rain -> Rain
        WeatherLayer.Storm -> Storm
    }
}

object SkyScenes {
    /** Fraction of the page height where the horizon sits. */
    const val HORIZON = 0.547f

    /** Where the soft sky→ground band ends. */
    const val GROUND_START = 0.61f

    private const val GOLDEN_MIN = 75
    private const val DAWN_HALF_MIN = 45
    private const val DUSK_MIN = 60

    /** The phase at [nowMin] (minutes since local midnight) for a day whose sun rises and sets at the given minutes. */
    fun phaseAt(nowMin: Int, sunriseMin: Int, sunsetMin: Int): TimePhase = when {
        nowMin in (sunriseMin - DAWN_HALF_MIN) until (sunriseMin + DAWN_HALF_MIN) -> TimePhase.Dawn
        nowMin >= sunriseMin + DAWN_HALF_MIN && nowMin < sunsetMin - GOLDEN_MIN -> TimePhase.Day
        nowMin >= sunsetMin - GOLDEN_MIN && nowMin < sunsetMin -> TimePhase.Golden
        nowMin >= sunsetMin && nowMin < sunsetMin + DUSK_MIN -> TimePhase.Dusk
        else -> TimePhase.Night
    }

    /**
     * MET Norway `symbol_code` → weather layer. The `_day` / `_night` / `_polartwilight` suffix is ignored. Thunder wins over
     * rain; snow is shown as Cloudy until it has a layer of its own. Unknown codes are Cloudy rather than Clear, so a
     * mis-mapped storm never looks like a sunny day.
     */
    fun layerFor(symbolCode: String): WeatherLayer {
        val code = symbolCode.lowercase()
            .removeSuffix("_day").removeSuffix("_night").removeSuffix("_polartwilight")
        return when {
            code == "clearsky" || code == "fair" -> WeatherLayer.Clear
            code.contains("thunder") -> WeatherLayer.Storm
            // Snow (and snow showers) has no layer of its own yet, so it must not be mistaken for rain.
            code.contains("snow") -> WeatherLayer.Cloudy
            code.contains("rain") || code.contains("sleet") || code.contains("showers") -> WeatherLayer.Rain
            code == "fog" -> WeatherLayer.Fog
            code == "partlycloudy" || code == "cloudy" -> WeatherLayer.Cloudy
            else -> WeatherLayer.Cloudy
        }
    }

    /** The condition as words, as it should read in the header. */
    fun conditionLabel(symbolCode: String, phase: TimePhase): String {
        val code = symbolCode.lowercase()
        val night = phase == TimePhase.Dusk || phase == TimePhase.Night
        return when (layerFor(symbolCode)) {
            WeatherLayer.Clear -> if (night) "Clear night" else "Sunny"
            WeatherLayer.Cloudy -> if (code.contains("snow")) "Snow" else if (code.startsWith("partlycloudy")) "Partly cloudy" else "Cloudy"
            WeatherLayer.Fog -> "Fog"
            WeatherLayer.Rain -> if (code.contains("light")) "Light rain" else if (code.contains("heavy")) "Heavy rain" else "Rain"
            WeatherLayer.Storm -> "Thunderstorm"
        }
    }

    /** Condition for a sky *scene* shown without live weather, used by the debug switcher. */
    fun symbolFor(layer: WeatherLayer) = when (layer) {
        WeatherLayer.Clear -> "clearsky_day"
        WeatherLayer.Cloudy -> "cloudy"
        WeatherLayer.Fog -> "fog"
        WeatherLayer.Rain -> "rain"
        WeatherLayer.Storm -> "rainandthunder"
    }
}

/** Geometry of the 24-hour arc: midnight on the left horizon, noon at the top, midnight on the right horizon. */
object SkyArc {
    /** Angle in degrees as Compose measures it (clockwise from 3 o'clock) for [hour] 0..24. */
    fun composeAngleDeg(hour: Float): Float = 180f + 7.5f * hour.coerceIn(0f, 24f)

    /** Position on the arc of radius [radius] centred at ([cx], [horizonY]), with y growing downwards. */
    fun point(hour: Float, cx: Float, horizonY: Float, radius: Float): Pair<Float, Float> {
        val a = composeAngleDeg(hour) * PI / 180.0
        return (cx + radius * cos(a)).toFloat() to (horizonY + radius * sin(a)).toFloat()
    }

    /** The screen-time arc segment for one hour: it starts a hair into the hour and is at most 92% of it long. */
    fun segment(hour: Int, minutes: Float, elapsedFractionOfHour: Float = 1f): Pair<Float, Float>? {
        val m = min(minutes, 60f * elapsedFractionOfHour.coerceIn(0f, 1f))
        if (m <= 0.0001f) return null
        val start = hour + 0.04f
        val length = min(0.92f, m / 60f)
        return start to start + length
    }
}

/** Plain-language, data-driven one-liner about today's screen time. */
object SkyInsight {
    fun text(totalMin: Int, yesterdayMin: Int?, hourlyMinutes: List<Int>, nowHour: Int): String {
        if (totalMin <= 0) return "No screen time yet today."
        // A few minutes of use says nothing about the day yet; better to be quiet than to draw a conclusion from it.
        if (totalMin < 10) return "Just getting started."
        val part = busiestPart(hourlyMinutes, nowHour)
        val compare = yesterdayMin?.let { y ->
            val d = totalMin - y
            when {
                kotlin.math.abs(d) < 5 -> "About the same as yesterday"
                d < 0 -> "Quieter than yesterday by ${formatMin(-d)}"
                else -> "Busier than yesterday by ${formatMin(d)}"
            }
        }
        return when {
            compare != null && part != null -> "$compare. Most of it $part."
            compare != null -> "$compare."
            part != null -> "Most of it $part."
            else -> "Screen time so far today."
        }
    }

    /** "before 11 AM", "this afternoon", ... for the busiest stretch of the day so far; null if nothing stands out. */
    fun busiestPart(hourly: List<Int>, nowHour: Int): String? {
        val total = hourly.sum()
        if (total <= 0) return null
        fun share(range: IntRange) = range.sumOf { hourly.getOrElse(it) { 0 } }.toDouble() / total
        val morning = share(0..10)
        val midday = share(11..16)
        val evening = share(17..23)
        val best = max(morning, max(midday, evening))
        if (best < 0.5 || nowHour < 6) return null
        return when (best) {
            morning -> "before 11 AM"
            midday -> "between 11 AM and 5 PM"
            else -> "this evening"
        }
    }

    fun formatMin(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m".replace(" 0m", "") else "${m} min"
}
