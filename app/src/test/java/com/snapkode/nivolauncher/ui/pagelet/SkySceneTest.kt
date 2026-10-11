package com.snapkode.nivolauncher.ui.pagelet

import androidx.compose.ui.graphics.Color
import com.snapkode.nivolauncher.ui.pagelet.sky.Marker
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyArc
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyInsight
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyPalettes
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyScenes
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyWeatherStyles
import com.snapkode.nivolauncher.ui.pagelet.sky.TimePhase
import com.snapkode.nivolauncher.ui.pagelet.sky.WeatherLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SkySceneTest {
    private val sunrise = 6 * 60 + 28
    private val sunset = 18 * 60 + 21

    // ---- phases ---------------------------------------------------------------------------------------------------

    @Test fun phaseBoundaries() {
        fun at(m: Int) = SkyScenes.phaseAt(m, sunrise, sunset)
        assertEquals(TimePhase.Night, at(0))
        assertEquals(TimePhase.Night, at(sunrise - 46))
        assertEquals(TimePhase.Dawn, at(sunrise - 45))
        assertEquals(TimePhase.Dawn, at(sunrise + 44))
        assertEquals(TimePhase.Day, at(sunrise + 45))
        assertEquals(TimePhase.Day, at(sunset - 76))
        assertEquals(TimePhase.Golden, at(sunset - 75))
        assertEquals(TimePhase.Golden, at(sunset - 1))
        assertEquals(TimePhase.Dusk, at(sunset))
        assertEquals(TimePhase.Dusk, at(sunset + 59))
        assertEquals(TimePhase.Night, at(sunset + 60))
        assertEquals(TimePhase.Night, at(23 * 60 + 59))
    }

    @Test fun everyMinuteOfTheDayHasExactlyOnePhase() {
        val seen = HashSet<TimePhase>()
        for (m in 0 until 24 * 60) seen += SkyScenes.phaseAt(m, sunrise, sunset)
        assertEquals(TimePhase.entries.toSet(), seen)
    }

    @Test fun markerIsSunByDayAndMoonByNight() {
        assertEquals(Marker.Sun, SkyPalettes.of(TimePhase.Dawn).marker)
        assertEquals(Marker.Sun, SkyPalettes.of(TimePhase.Day).marker)
        assertEquals(Marker.Sun, SkyPalettes.of(TimePhase.Golden).marker)
        assertEquals(Marker.Moon, SkyPalettes.of(TimePhase.Dusk).marker)
        assertEquals(Marker.Moon, SkyPalettes.of(TimePhase.Night).marker)
    }

    // ---- MET Norway symbol_code → layer -----------------------------------------------------------------------------

    @Test fun symbolMapping() {
        fun l(s: String) = SkyScenes.layerFor(s)
        assertEquals(WeatherLayer.Clear, l("clearsky_day"))
        assertEquals(WeatherLayer.Clear, l("clearsky_night"))
        assertEquals(WeatherLayer.Clear, l("fair_day"))
        assertEquals(WeatherLayer.Clear, l("fair_polartwilight"))
        assertEquals(WeatherLayer.Cloudy, l("partlycloudy_day"))
        assertEquals(WeatherLayer.Cloudy, l("cloudy"))
        assertEquals(WeatherLayer.Fog, l("fog"))
        assertEquals(WeatherLayer.Rain, l("rain"))
        assertEquals(WeatherLayer.Rain, l("lightrain"))
        assertEquals(WeatherLayer.Rain, l("heavyrainshowers_day"))
        assertEquals(WeatherLayer.Rain, l("sleet"))
        assertEquals(WeatherLayer.Rain, l("lightsleetshowers_night"))
        assertEquals(WeatherLayer.Storm, l("rainandthunder"))
        assertEquals(WeatherLayer.Storm, l("heavyrainshowersandthunder_day"))
        assertEquals(WeatherLayer.Storm, l("snowandthunder"))
    }

    @Test fun thunderBeatsRainAndSnowFallsBackToCloudy() {
        assertEquals(WeatherLayer.Storm, SkyScenes.layerFor("lightrainandthunder"))
        assertEquals(WeatherLayer.Cloudy, SkyScenes.layerFor("snow"))
        assertEquals(WeatherLayer.Cloudy, SkyScenes.layerFor("heavysnowshowers_night"))
    }

    @Test fun anUnknownCodeNeverLooksLikeASunnyDay() {
        assertEquals(WeatherLayer.Cloudy, SkyScenes.layerFor("something_new_from_met"))
        assertEquals(WeatherLayer.Cloudy, SkyScenes.layerFor(""))
    }

    @Test fun symbolMappingIsCaseInsensitive() = assertEquals(WeatherLayer.Rain, SkyScenes.layerFor("LightRain_Day"))

    @Test fun everyLayerRoundTripsThroughItsDebugSymbol() {
        for (layer in WeatherLayer.entries) assertEquals(layer, SkyScenes.layerFor(SkyScenes.symbolFor(layer)))
    }

    @Test fun conditionWords() {
        assertEquals("Sunny", SkyScenes.conditionLabel("clearsky_day", TimePhase.Day))
        assertEquals("Clear night", SkyScenes.conditionLabel("clearsky_night", TimePhase.Night))
        assertEquals("Clear night", SkyScenes.conditionLabel("clearsky_day", TimePhase.Dusk))
        assertEquals("Partly cloudy", SkyScenes.conditionLabel("partlycloudy_day", TimePhase.Day))
        assertEquals("Light rain", SkyScenes.conditionLabel("lightrain", TimePhase.Day))
        assertEquals("Rain", SkyScenes.conditionLabel("rain", TimePhase.Day))
        assertEquals("Thunderstorm", SkyScenes.conditionLabel("rainandthunder", TimePhase.Day))
        assertEquals("Fog", SkyScenes.conditionLabel("fog", TimePhase.Day))
    }

    // ---- the arc ---------------------------------------------------------------------------------------------------

    @Test fun arcEndpointsAndNoon() {
        val cx = 200f; val hy = 460f; val r = 165f
        val (x0, y0) = SkyArc.point(0f, cx, hy, r)
        val (x12, y12) = SkyArc.point(12f, cx, hy, r)
        val (x24, y24) = SkyArc.point(24f, cx, hy, r)
        assertEquals(cx - r, x0, 0.01f); assertEquals(hy, y0, 0.01f)       // midnight: left horizon
        assertEquals(cx, x12, 0.01f); assertEquals(hy - r, y12, 0.01f)       // noon: top
        assertEquals(cx + r, x24, 0.01f); assertEquals(hy, y24, 0.01f)       // midnight: right horizon
    }

    @Test fun arcQuarterPoints() {
        val (x6, y6) = SkyArc.point(6f, 0f, 0f, 100f)
        assertEquals(-70.71f, x6, 0.01f); assertEquals(-70.71f, y6, 0.01f)
        val (x18, y18) = SkyArc.point(18f, 0f, 0f, 100f)
        assertEquals(70.71f, x18, 0.01f); assertEquals(-70.71f, y18, 0.01f)
    }

    @Test fun arcAnglesAreMonotonicAndClamped() {
        var last = -1f
        for (h in 0..24) { val a = SkyArc.composeAngleDeg(h.toFloat()); assertTrue(a > last); last = a }
        assertEquals(180f, SkyArc.composeAngleDeg(-3f), 1e-6f)
        assertEquals(360f, SkyArc.composeAngleDeg(99f), 1e-6f)
    }

    @Test fun pointsStayOnTheCircle() {
        for (h in 0..240) {
            val (x, y) = SkyArc.point(h / 10f, 10f, 20f, 50f)
            val d = kotlin.math.hypot(x - 10f, y - 20f)
            assertEquals(50f, d, 0.01f)
            assertTrue(y <= 20.01f)               // never below the horizon
        }
    }

    @Test fun segmentStartsHairAfterTheHourAndNeverFillsIt() {
        val (s, e) = SkyArc.segment(7, 22f)!!
        assertEquals(7.04f, s, 1e-5f)
        assertEquals(7.04f + 22f / 60f, e, 1e-5f)
        val (s2, e2) = SkyArc.segment(9, 60f)!!
        assertEquals(0.92f, e2 - s2, 1e-5f)
        assertNull(SkyArc.segment(3, 0f))
    }

    @Test fun currentHourSegmentIsProratedByElapsedTime() {
        // 45 minutes of use recorded, but only 20 minutes of the hour have passed.
        val (s, e) = SkyArc.segment(13, 45f, elapsedFractionOfHour = 20f / 60f)!!
        assertEquals(20f / 60f, e - s, 1e-5f)
        assertNull(SkyArc.segment(13, 10f, elapsedFractionOfHour = 0f))
    }

    // ---- palette rules ---------------------------------------------------------------------------------------------

    private fun hsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red; val g = c.green; val b = c.blue
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
        return Triple(0f, s, l)
    }

    @Test fun noBackgroundColourIsSaturated() {
        for (phase in TimePhase.entries) for (c in SkyPalettes.of(phase).backgroundColors) {
            val (_, s, l) = hsl(c)
            if (l in 0.2f..0.8f) assertTrue("$phase background colour $c has saturation $s at lightness $l", s <= 0.55f)
        }
        for (layer in WeatherLayer.entries) {
            val w = SkyWeatherStyles.of(layer)
            for (c in listOfNotNull(w.overlayTop, w.overlayMid, w.cloudFill)) {
                val (_, s, l) = hsl(c.copy(alpha = 1f))
                if (l in 0.2f..0.8f) assertTrue("$layer weather colour $c has saturation $s at lightness $l", s <= 0.55f)
            }
        }
    }

    @Test fun eachSceneUsesAtMostThreeSkyAndTwoGroundColours() {
        for (phase in TimePhase.entries) assertEquals(5, SkyPalettes.of(phase).backgroundColors.size)
    }

    @Test fun starsOnlyWhereTheSkyIsDark() {
        assertEquals(0f, SkyPalettes.Day.stars, 0f)
        assertEquals(0f, SkyPalettes.Golden.stars, 0f)
        assertEquals(1f, SkyPalettes.Night.stars, 0f)
        assertTrue(SkyWeatherStyles.Clear.showsStars && SkyWeatherStyles.Fog.showsStars)
        assertTrue(!SkyWeatherStyles.Cloudy.showsStars && !SkyWeatherStyles.Rain.showsStars && !SkyWeatherStyles.Storm.showsStars)
    }

    @Test fun weatherDimsTheSunInProportionToSeverity() {
        val o = WeatherLayer.entries.map { SkyWeatherStyles.of(it).sunOpacity }
        assertEquals(1.0f, SkyWeatherStyles.Clear.sunOpacity, 0f)
        assertTrue(SkyWeatherStyles.Cloudy.sunOpacity < SkyWeatherStyles.Clear.sunOpacity)
        assertTrue(SkyWeatherStyles.Rain.sunOpacity < SkyWeatherStyles.Fog.sunOpacity)
        assertTrue(SkyWeatherStyles.Storm.sunOpacity < SkyWeatherStyles.Rain.sunOpacity)
        assertTrue(o.all { it in 0f..1f })
    }

    @Test fun particleBudgetsMatchTheSpec() {
        assertEquals(36, SkyWeatherStyles.Rain.rainStreaks)
        assertEquals(56, SkyWeatherStyles.Storm.rainStreaks)
        assertTrue(SkyWeatherStyles.Storm.flash && !SkyWeatherStyles.Rain.flash)
        assertEquals(3, SkyWeatherStyles.Fog.fogBands)
        assertEquals(7, SkyWeatherStyles.Storm.cloudCount)
        assertEquals(0, SkyWeatherStyles.Clear.rainStreaks)
    }

    // ---- text contrast ---------------------------------------------------------------------------------------------

    private fun over(top: Color, base: Color): Color {
        val a = top.alpha
        return Color(top.red * a + base.red * (1 - a), top.green * a + base.green * (1 - a), top.blue * a + base.blue * (1 - a), 1f)
    }

    private fun lerp(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

    /** The sky colour at vertical fraction [y]: the page gradient with the weather overlay and clouds on top. */
    private fun skyAt(phase: TimePhase, layer: WeatherLayer, y: Float): Color {
        val p = SkyPalettes.of(phase)
        val stops = listOf(0f to p.sky0, 0.36f to p.sky1, SkyScenes.HORIZON to p.sky2, SkyScenes.GROUND_START to p.ground, 1f to p.groundEnd)
        val i = stops.indexOfLast { it.first <= y }.coerceAtMost(stops.size - 2)
        val (y0, c0) = stops[i]; val (y1, c1) = stops[i + 1]
        var c = lerp(c0, c1, ((y - y0) / (y1 - y0)).coerceIn(0f, 1f))
        val w = SkyWeatherStyles.of(layer)
        if (w.overlayTop != null && w.overlayMid != null) {
            val ov = when {
                y <= 0.55f -> lerp(w.overlayTop, w.overlayMid, y / 0.55f).copy(alpha = w.overlayTop.alpha + (w.overlayMid.alpha - w.overlayTop.alpha) * (y / 0.55f))
                y <= 0.62f -> w.overlayMid.copy(alpha = w.overlayMid.alpha * (1f - (y - 0.55f) / 0.07f))
                else -> Color.Transparent
            }
            c = over(ov, c)
        }
        // Worst case for white text: a cloud wash right behind it (clouds only drift through the upper half).
        if (w.cloudCount > 0 && y < 0.5f) c = over(w.cloudFill, c)
        // The legibility scrim sits above the weather light, below the text.
        if (w.textScrim > 0f) {
            val a = when { y <= 0.36f -> w.textScrim; y < 0.46f -> w.textScrim * (1f - (y - 0.36f) / 0.10f); else -> 0f }
            c = over(Color.Black.copy(alpha = a), c)
        }
        return c
    }

    private fun luminance(c: Color): Float {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }

    private fun contrastWithWhite(bg: Color) = 1.05f / (luminance(bg) + 0.05f)

    @Test fun whiteTextIsLegibleOnEverySceneWhereTheTextSits() {
        // Header, clock and the temperature row all sit in the top third of the page. Body text (≥ 24 sp) needs 3:1;
        // small text needs 4.5:1. We hold the whole band to 4.5:1 with a cloud wash assumed directly behind it.
        val rows = listOf(0.045f, 0.12f, 0.20f, 0.27f, 0.33f)
        val failures = ArrayList<String>()
        for (phase in TimePhase.entries) for (layer in WeatherLayer.entries) for (y in rows) {
            val ratio = contrastWithWhite(skyAt(phase, layer, y))
            if (ratio < 4.5f) failures += "$phase·$layer at ${(y * 100).toInt()}% = ${"%.2f".format(ratio)}"
        }
        assertTrue("White text falls below 4.5:1 in: $failures", failures.isEmpty())
    }

    @Test fun groundTextIsAlwaysOnNearBlack() {
        for (phase in TimePhase.entries) for (layer in WeatherLayer.entries) {
            val c = skyAt(phase, layer, 0.72f)
            assertTrue("$phase·$layer ground contrast ${contrastWithWhite(c)}", contrastWithWhite(c) > 12f)
        }
    }

    // ---- insight text ---------------------------------------------------------------------------------------------

    private fun hours(vararg pairs: Pair<Int, Int>): List<Int> = MutableList(24) { 0 }.also { l -> pairs.forEach { (h, m) -> l[h] = m } }

    @Test fun insightComparesWithYesterdayAndFindsTheBusiestPart() {
        val h = hours(7 to 20, 8 to 30, 9 to 25, 14 to 10)
        val t = SkyInsight.text(h.sum(), yesterdayMin = h.sum() + 18, hourlyMinutes = h, nowHour = 15)
        assertEquals("Quieter than yesterday by 18 min. Most of it before 11 AM.", t)
    }

    @Test fun insightSaysBusierWhenItIs() {
        val h = hours(19 to 60, 20 to 50, 21 to 30)
        val t = SkyInsight.text(140, yesterdayMin = 60, hourlyMinutes = h, nowHour = 22)
        assertEquals("Busier than yesterday by 1h 20m. Most of it this evening.", t)
    }

    @Test fun insightNeverInventsAComparison() {
        assertEquals("No screen time yet today.", SkyInsight.text(0, 50, hours(), 9))
        assertTrue(SkyInsight.text(30, null, hours(9 to 30), 10).startsWith("Most of it"))
        assertEquals("About the same as yesterday. Most of it before 11 AM.", SkyInsight.text(100, 98, hours(8 to 60, 9 to 40), 12))
    }

    @Test fun insightIsSilentAboutThePartOfADayThatHasBarelyStarted() {
        assertNull(SkyInsight.busiestPart(hours(1 to 5), nowHour = 3))
    }

    @Test fun minutesFormatting() {
        assertEquals("18 min", SkyInsight.formatMin(18))
        assertEquals("1h", SkyInsight.formatMin(60))
        assertEquals("2h 41m", SkyInsight.formatMin(161))
    }
}
