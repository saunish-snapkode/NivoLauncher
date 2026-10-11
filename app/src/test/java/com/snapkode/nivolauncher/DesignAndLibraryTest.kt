package com.snapkode.nivolauncher

import com.snapkode.nivolauncher.data.AppClassifier
import com.snapkode.nivolauncher.data.NivoPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignAndLibraryTest {
    @Test
    fun primaryTextMeetsAaOnEveryPalette() {
        listOf(NivoPalettes.Light, NivoPalettes.Dark, NivoPalettes.Amoled).forEach { p ->
            listOf(p.canvas, p.surface, p.elevated).forEach { bg ->
                assertTrue("primary ${p.textPrimary.toString(16)} on ${bg.toString(16)}", NivoPalettes.contrast(p.textPrimary, bg) >= 7.0)
            }
        }
    }

    @Test
    fun secondaryTextMeetsAaOnEveryPalette() {
        listOf(NivoPalettes.Light, NivoPalettes.Dark, NivoPalettes.Amoled).forEach { p ->
            listOf(p.canvas, p.surface, p.elevated).forEach { bg ->
                val ratio = NivoPalettes.contrast(p.textSecondary, bg)
                assertTrue("secondary ${p.textSecondary.toString(16)} on ${bg.toString(16)} = $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun surfacesStepUpInLightnessInDarkThemes() {
        listOf(NivoPalettes.Dark, NivoPalettes.Amoled).forEach { p ->
            val l = listOf(p.canvas, p.surface, p.elevated, p.highest).map { NivoPalettes.luminance(it) }
            assertEquals(l.sorted(), l)
        }
    }

    @Test
    fun classifiesCommonApps() {
        fun c(pkg: String, label: String, cat: Int = AppClassifier.CAT_UNDEFINED) = AppClassifier.classify(pkg, label, cat)
        assertEquals("communication", c("com.whatsapp", "WhatsApp"))
        assertEquals("communication", c("com.google.android.gm", "Gmail"))
        assertEquals("social", c("com.instagram.android", "Instagram"))
        assertEquals("finance", c("com.phonepe.app", "PhonePe"))
        assertEquals("shopping", c("in.amazon.mShop.android.shopping", "Amazon"))
        assertEquals("travel", c("com.google.android.apps.maps", "Maps"))
        assertEquals("photography", c("com.google.android.apps.photos", "Photos"))
        assertEquals("entertainment", c("com.google.android.youtube", "YouTube"))
        assertEquals("productivity", c("com.google.android.calendar", "Calendar"))
        assertEquals("games", c("com.some.studio", "Anything", 0))
        assertEquals("utilities", c("com.android.settings", "Settings"))
        // "snap" inside a developer name must not make an app a photo app.
        assertEquals("utilities", c("com.snapkode.nivolauncher", "Nivo Launcher"))
    }

    @Test
    fun androidCategoryIsUsedWhenNothingElseMatches() {
        assertEquals("entertainment", AppClassifier.classify("com.example.zzz", "Zzz", 1))
        assertEquals("utilities", AppClassifier.classify("com.example.zzz", "Zzz"))
    }
}

class ThemeSchedulerTest {
    private fun m(h: Int, min: Int = 0) = h * 60 + min
    private val start = m(19)
    private val end = m(7)

    @org.junit.Test
    fun overnightWindowWrapsPastMidnight() {
        org.junit.Assert.assertTrue(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(23), start, end))
        org.junit.Assert.assertTrue(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(3), start, end))
        org.junit.Assert.assertTrue(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(19), start, end))
        org.junit.Assert.assertFalse(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(7), start, end))
        org.junit.Assert.assertFalse(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(12), start, end))
    }

    @org.junit.Test
    fun sameDayWindowAndEqualBounds() {
        org.junit.Assert.assertTrue(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(14), m(13), m(15)))
        org.junit.Assert.assertFalse(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(16), m(13), m(15)))
        org.junit.Assert.assertFalse(com.snapkode.nivolauncher.data.ThemeScheduler.isDark(m(5), m(8), m(8)))
    }

    @org.junit.Test
    fun nextChangeIsComputedAcrossMidnight() {
        // 23:30 is dark; the theme turns light at 07:00 = 7.5h later.
        org.junit.Assert.assertEquals(7 * 60 + 30, com.snapkode.nivolauncher.data.ThemeScheduler.minutesUntilChange(m(23, 30), start, end))
        // 12:00 is light; it turns dark at 19:00.
        org.junit.Assert.assertEquals(7 * 60, com.snapkode.nivolauncher.data.ThemeScheduler.minutesUntilChange(m(12), start, end))
    }
}

class WeatherFormatTest {
    @org.junit.Test
    fun temperatureRoundsAndConverts() {
        org.junit.Assert.assertEquals("22°", com.snapkode.nivolauncher.data.WeatherFormat.temperature(21.6, false))
        org.junit.Assert.assertEquals("72°", com.snapkode.nivolauncher.data.WeatherFormat.temperature(22.0, true))
        org.junit.Assert.assertEquals("32°", com.snapkode.nivolauncher.data.WeatherFormat.temperature(0.0, true))
        org.junit.Assert.assertEquals("-4°", com.snapkode.nivolauncher.data.WeatherFormat.temperature(-4.2, false))
    }

    @org.junit.Test
    fun weatherCodesMapToPlainLanguage() {
        val d = com.snapkode.nivolauncher.data.WeatherFormat::describe
        org.junit.Assert.assertEquals("Clear", d(0).second)
        org.junit.Assert.assertEquals("Rain", d(63).second)
        org.junit.Assert.assertEquals("Snow", d(73).second)
        org.junit.Assert.assertEquals("Thunderstorm", d(96).second)
        org.junit.Assert.assertEquals("Weather", d(1234).second)
    }

    @org.junit.Test
    fun cacheFreshnessWindow() {
        val f = com.snapkode.nivolauncher.data.WeatherFormat::isFresh
        org.junit.Assert.assertTrue(f(0L, 29 * 60_000L, 30))
        org.junit.Assert.assertFalse(f(0L, 31 * 60_000L, 30))
    }
}
