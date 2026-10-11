package com.snapkode.nivolauncher

import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.SearchTools
import com.snapkode.nivolauncher.data.appWidgetIdOf
import com.snapkode.nivolauncher.data.isFolderId
import com.snapkode.nivolauncher.data.isWidgetId
import com.snapkode.nivolauncher.data.widgetItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    @Test
    fun prefixBeatsContainsBeatsFuzzy() {
        val prefix = SearchTools.score("Camera", "cam", fuzzy = true)
        val contains = SearchTools.score("Action Camera", "era", fuzzy = true)
        val fuzzy = SearchTools.score("Camera", "cmr", fuzzy = true)
        assertTrue(prefix > contains)
        assertTrue(contains > fuzzy)
        assertTrue(fuzzy > 0)
    }

    @Test
    fun fuzzyCanBeSwitchedOff() {
        assertEquals(0, SearchTools.score("Camera", "cmr", fuzzy = false))
    }

    @Test
    fun wordPrefixMatches() {
        assertEquals(3, SearchTools.score("Google Maps", "maps", fuzzy = true))
    }

    @Test
    fun lettersOutOfOrderDoNotMatch() {
        assertEquals(0, SearchTools.score("Camera", "rmc", fuzzy = true))
    }

    @Test
    fun settingsSearchNeedsTwoLetters() {
        assertTrue(SearchTools.matchingSettings("w").isEmpty())
        assertTrue(SearchTools.matchingSettings("wifi").any { it.title == "Wi-Fi" })
    }

    @Test
    fun widgetSpansCoverTheirCells() {
        val widget = HomeItem("widget:7", page = 0, col = 1, row = 2, spanX = 2, spanY = 2)
        assertTrue(widget.occupies(0, 1, 2))
        assertTrue(widget.occupies(0, 2, 3))
        assertFalse(widget.occupies(0, 3, 2))
        assertFalse(widget.occupies(1, 1, 2))
    }

    @Test
    fun singleCellItemOccupiesOnlyItself() {
        val app = HomeItem("app", page = 0, col = 0, row = 0)
        assertTrue(app.occupies(0, 0, 0))
        assertFalse(app.occupies(0, 1, 0))
    }

    @Test
    fun idHelpersRoundTrip() {
        assertTrue(isWidgetId(widgetItemId(42)))
        assertEquals(42, appWidgetIdOf(widgetItemId(42)))
        assertTrue(isFolderId("folder:abc"))
        assertFalse(isFolderId("com.example/Main#0"))
    }

    @Test
    fun dockItemsAreFlagged() {
        assertTrue(HomeItem("a", HomeItem.DOCK, 0, 0).inDock)
        assertFalse(HomeItem("a", 0, 0, 0).inDock)
    }
}
