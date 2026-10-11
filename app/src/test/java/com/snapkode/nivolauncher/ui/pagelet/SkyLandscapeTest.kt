package com.snapkode.nivolauncher.ui.pagelet

import com.snapkode.nivolauncher.ui.pagelet.sky.GlyphKind
import com.snapkode.nivolauncher.ui.pagelet.sky.LandscapeColors
import com.snapkode.nivolauncher.ui.pagelet.sky.LandscapeGen
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyLook
import com.snapkode.nivolauncher.ui.pagelet.sky.TimePhase
import com.snapkode.nivolauncher.ui.pagelet.sky.WeatherLayer
import com.snapkode.nivolauncher.ui.pagelet.sky.glyphFor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The illustrated horizon and the weather glyph: the shapes are fixed, in range, and chosen by time and weather. */
class SkyLandscapeTest {
    @Test fun ridgesAreTheSameEveryTimeAndStayInRange() {
        val a = LandscapeGen.ridge(11, 64, 0.8f)
        assertArrayEquals(a, LandscapeGen.ridge(11, 64, 0.8f), 0f)
        assertEquals(64, a.size)
        assertTrue(a.all { it in 0f..1f })
        assertFalse("different seeds give different hills", a.contentEquals(LandscapeGen.ridge(23, 64, 0.8f)))
    }

    @Test fun skylineIsStableCoversTheRightAndGrowsTowardsIt() {
        val city = LandscapeGen.skyline(5, 412f, 0.34f)
        assertEquals(city.map { it.x01 }, LandscapeGen.skyline(5, 412f, 0.34f).map { it.x01 })
        assertTrue(city.size in 8..25)
        assertTrue(city.first().x01 >= 0.33f)
        assertTrue("it reaches the right edge", city.last().x01 + city.last().widthDp / 412f >= 0.97f)
        city.forEach { assertTrue(it.heightDp in 20f..130f); assertTrue(it.cols in 2..5); assertTrue(it.rows in 2..12) }
        val leftAvg = city.take(city.size / 2).map { it.heightDp }.average()
        val rightAvg = city.drop(city.size / 2).map { it.heightDp }.average()
        assertTrue("taller towards the right", rightAvg > leftAvg)
    }

    @Test fun treesStayNearTheEdgesNotTheMiddle() {
        val trees = LandscapeGen.trees(9, 8)
        assertEquals(8, trees.size)
        assertTrue(trees.all { it.x01 < 0.26f || it.x01 > 0.74f })
        assertTrue(trees.all { it.heightDp in 20f..70f })
    }

    @Test fun windowsAreStableAndFollowTheLitFraction() {
        assertEquals(LandscapeGen.lit(42, 3, 0.5f), LandscapeGen.lit(42, 3, 0.5f))
        assertFalse("none by day", (0 until 200).any { LandscapeGen.lit(42, it, 0f) })
        val few = (0 until 400).count { LandscapeGen.lit(42, it, 0.15f) }
        val many = (0 until 400).count { LandscapeGen.lit(42, it, 0.6f) }
        assertTrue(many > few)
        assertTrue(many in 180..300)
    }

    @Test fun cityLightsFollowTheTimeOfDay() {
        fun lit(p: TimePhase) = LandscapeColors.of(SkyLook(p, WeatherLayer.Clear)).litFraction
        assertEquals(0f, lit(TimePhase.Day), 0f)
        assertTrue(lit(TimePhase.Night) > lit(TimePhase.Dusk))
        assertTrue(lit(TimePhase.Dusk) > lit(TimePhase.Dawn))
        assertTrue(lit(TimePhase.Dawn) > lit(TimePhase.Golden))
        assertTrue(lit(TimePhase.Golden) > 0f)
    }

    @Test fun aGloomyDayLightsSomeWindowsAndAClearOneDoesNot() {
        assertEquals(0f, LandscapeColors.of(SkyLook(TimePhase.Day, WeatherLayer.Clear)).litFraction, 0f)
        assertTrue(LandscapeColors.of(SkyLook(TimePhase.Day, WeatherLayer.Storm)).litFraction > 0f)
    }

    @Test fun birdsOnlyFlyByDayInOpenSkies() {
        assertTrue(LandscapeColors.of(SkyLook(TimePhase.Day, WeatherLayer.Clear)).birds)
        assertTrue(LandscapeColors.of(SkyLook(TimePhase.Golden, WeatherLayer.Cloudy)).birds)
        assertFalse(LandscapeColors.of(SkyLook(TimePhase.Night, WeatherLayer.Clear)).birds)
        assertFalse(LandscapeColors.of(SkyLook(TimePhase.Day, WeatherLayer.Rain)).birds)
    }

    @Test fun layersGetDarkerTowardsTheFront() {
        fun lum(c: androidx.compose.ui.graphics.Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        for (p in TimePhase.entries) {
            val c = LandscapeColors.of(SkyLook(p, WeatherLayer.Clear))
            assertTrue("$p: far >= mid", lum(c.far) >= lum(c.mid) - 0.001f)
            assertTrue("$p: mid >= city", lum(c.mid) >= lum(c.city) - 0.001f)
            assertTrue("$p: city >= front", lum(c.city) >= lum(c.front) - 0.001f)
        }
    }

    @Test fun glyphsMatchTheWeatherCode() {
        assertEquals(GlyphKind.Sun, glyphFor("clearsky_day", false))
        assertEquals(GlyphKind.Moon, glyphFor("clearsky_night", true))
        assertEquals(GlyphKind.Sun, glyphFor("fair_day", false))
        assertEquals(GlyphKind.PartlySun, glyphFor("partlycloudy_day", false))
        assertEquals(GlyphKind.PartlyMoon, glyphFor("partlycloudy_night", true))
        assertEquals(GlyphKind.Cloud, glyphFor("cloudy", false))
        assertEquals(GlyphKind.Rain, glyphFor("lightrainshowers_day", false))
        assertEquals(GlyphKind.Rain, glyphFor("heavyrain", false))
        assertEquals(GlyphKind.Storm, glyphFor("rainandthunder", false))
        assertEquals(GlyphKind.Snow, glyphFor("heavysnow", false))
        assertEquals(GlyphKind.Fog, glyphFor("fog", false))
        assertEquals("an unknown code reads as cloud, never as sun", GlyphKind.Cloud, glyphFor("hologram", false))
    }
}
