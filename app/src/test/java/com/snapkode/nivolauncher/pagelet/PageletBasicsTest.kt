package com.snapkode.nivolauncher.pagelet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageletBasicsTest {
    @Test fun idsRoundTripTheirType() {
        for (t in PageletType.entries) {
            val id = newPageletId(t)
            assertTrue(id.startsWith("nivo:pagelet:"))
            assertTrue(isPageletId(id))
            assertEquals(t, pageletTypeOf(id))
        }
    }

    @Test fun idsAreUnique() {
        val ids = List(200) { newPageletId(PageletType.SKY) }
        assertEquals(200, ids.toSet().size)
    }

    @Test fun nonPageletIdsAreNotPagelets() {
        assertFalse(isPageletId("nivo:clock:ab12"))
        assertFalse(isPageletId("com.example/.Main#0"))
        assertFalse(isPageletId("widget:12"))
        assertNull(pageletTypeOf("nivo:clock:ab12"))
    }

    @Test fun anUnknownTypeFromANewerVersionIsNull() {
        assertTrue(isPageletId("nivo:pagelet:HOLOGRAM:abcd1234"))
        assertNull(pageletTypeOf("nivo:pagelet:HOLOGRAM:abcd1234"))
    }

    @Test fun everyKindButFocusIsAvailable() {
        assertEquals(
            listOf(PageletType.SKY, PageletType.AGENDA, PageletType.NOW_PLAYING, PageletType.HEADLINES, PageletType.MOVE),
            PageletType.entries.filter { it.available },
        )
    }

    @Test fun agendaAndMoveChoicesRoundTrip() {
        val c = PageletConfig(showPast = false, sleepGoalMin = 450, stepGoal = 12_000, sections = setOf("steps", "heart"))
        val back = PageletConfig.fromJson(c.toJson(), PageletType.MOVE)
        assertEquals(c, back)
        assertTrue(back.has(com.snapkode.nivolauncher.pagelet.MoveSection.HEART))
        assertFalse(back.has(com.snapkode.nivolauncher.pagelet.MoveSection.SLEEP))
    }

    @Test fun moveStartsWithAllThreeSectionsAndOthersWithNone() {
        assertEquals(setOf("steps", "sleep", "heart"), PageletConfig.defaultsFor(PageletType.MOVE).sections)
        assertEquals(emptySet<String>(), PageletConfig.defaultsFor(PageletType.AGENDA).sections)
    }

    @Test fun sleepGoalIsClamped() {
        assertEquals(60, PageletConfig.fromJson("""{"sleepGoalMin":1}""", PageletType.MOVE).sleepGoalMin)
        assertEquals(960, PageletConfig.fromJson("""{"sleepGoalMin":99999}""", PageletType.MOVE).sleepGoalMin)
    }

    @Test fun onlySkyIsDrawnByTheLayers() {
        assertEquals(listOf(PageletType.SKY), PageletType.entries.filter { it.isLayered })
    }

    @Test fun headlinesChoicesRoundTripInTheConfig() {
        val c = PageletConfig(
            hideDock = false,
            news = com.snapkode.nivolauncher.data.news.NewsPrefs(
                providers = setOf("guardian", "npr"),
                custom = listOf(com.snapkode.nivolauncher.data.news.NewsCustomFeed("My \"odd\" blog", "https://b.example.com/feed?x=1&y=2")),
                style = com.snapkode.nivolauncher.data.news.NewsStyle.Compact, images = false, perSource = 5,
                order = com.snapkode.nivolauncher.data.news.NewsOrder.Newest,
            ),
            newsTopic = com.snapkode.nivolauncher.data.NewsTopic.Sports,
        )
        assertEquals(c, PageletConfig.fromJson(c.toJson(), PageletType.HEADLINES))
    }

    @Test fun anOldSkyConfigWithoutNewsGetsTheDefaults() {
        val c = PageletConfig.fromJson("""{"hideDock":false,"sections":["time"]}""", PageletType.SKY)
        assertEquals(com.snapkode.nivolauncher.data.news.NewsPrefs(), c.news)
        assertEquals(com.snapkode.nivolauncher.data.NewsTopic.Top, c.newsTopic)
    }

    @Test fun badNewsValuesFallBack() {
        val c = PageletConfig.fromJson("""{"news":{"style":"Hologram","perSource":9999,"custom":[{"name":"","url":"x"},7]},"newsTopic":"Nope"}""", PageletType.HEADLINES)
        assertEquals(com.snapkode.nivolauncher.data.news.NewsPrefs().style, c.news.style)
        assertEquals(50, c.news.perSource)
        assertEquals(emptyList<com.snapkode.nivolauncher.data.news.NewsCustomFeed>(), c.news.custom)
        assertEquals(com.snapkode.nivolauncher.data.NewsTopic.Top, c.newsTopic)
    }

    // ---- config ---------------------------------------------------------------------------------------------------

    @Test fun configRoundTrips() {
        val c = PageletConfig(hideDock = false, sections = setOf("time", "weather"), goalMinutes = 150)
        assertEquals(c, PageletConfig.fromJson(c.toJson(), PageletType.SKY))
    }

    @Test fun defaultsKeepTheDockWithEverySectionOn() {
        val c = PageletConfig.defaultsFor(PageletType.SKY)
        assertFalse(c.hideDock)
        assertEquals(setOf("time", "weather", "wellbeing", "calendar", "steps"), c.sections)
        assertEquals(240, c.goalMinutes)
    }

    @Test fun garbageFallsBackToDefaults() {
        assertEquals(PageletConfig(), PageletConfig.fromJson("{not json", PageletType.SKY))
        assertEquals(PageletConfig(), PageletConfig.fromJson("", PageletType.SKY))
        assertEquals(PageletConfig(), PageletConfig.fromJson(null, PageletType.SKY))
    }

    @Test fun unknownSectionsAndFieldsAreIgnored() {
        val c = PageletConfig.fromJson("""{"hideDock":false,"sections":["time","teleport"],"future":1}""", PageletType.SKY)
        assertFalse(c.hideDock)
        assertEquals(setOf("time"), c.sections)
    }

    @Test fun goalIsKeptInASaneRange() {
        assertEquals(15, PageletConfig.fromJson("""{"goalMinutes":-5}""").goalMinutes)
        assertEquals(1440, PageletConfig.fromJson("""{"goalMinutes":99999}""").goalMinutes)
    }

    // ---- visibility -----------------------------------------------------------------------------------------------

    @Test fun activeOnlyWhenCurrentScreenOnAndResumed() {
        val on = PageletVisibility(onCurrentPage = true, screenOn = true, resumed = true)
        assertTrue(on.active)
        assertFalse(on.copy(onCurrentPage = false).active)
        assertFalse(on.copy(screenOn = false).active)
        assertFalse(on.copy(resumed = false).active)
    }

    @Test fun batterySaverAndLowRamReduceLoad() {
        val base = PageletVisibility(true, true, true)
        assertFalse(base.reducedLoad)
        assertTrue(base.copy(batterySaver = true).reducedLoad)
        assertTrue(base.copy(lowRam = true).reducedLoad)
    }

    @Test fun closenessIsProportionalToScroll() {
        assertEquals(1f, pageCloseness(3f, 3), 1e-6f)
        assertEquals(0.5f, pageCloseness(2.5f, 3), 1e-6f)
        assertEquals(0.25f, pageCloseness(3.75f, 3), 1e-6f)
        assertEquals(0f, pageCloseness(1f, 3), 1e-6f)
        assertEquals(0f, pageCloseness(9f, 3), 1e-6f)
    }

    @Test fun dockFadesInProportionAndIgnoresPagesThatKeepIt() {
        assertEquals(0f, dockHideFraction(2.0f, emptyList()), 1e-6f)
        assertEquals(1f, dockHideFraction(3.0f, listOf(3)), 1e-6f)
        assertEquals(0.4f, dockHideFraction(2.4f, listOf(3)), 1e-6f)
        // Between two hiding pages the nearer one wins; the dock never flickers back.
        assertEquals(0.5f, dockHideFraction(3.5f, listOf(3, 4)), 1e-6f)
        assertEquals(0f, dockHideFraction(3.0f, listOf(5)), 1e-6f)
    }
}
