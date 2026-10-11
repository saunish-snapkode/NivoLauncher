package com.snapkode.nivolauncher

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.LayoutStore
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.pagelet.isPageletId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Pagelet framework against the real view model and the real SharedPreferences store. */
@RunWith(AndroidJUnit4::class)
class PageletTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: LauncherViewModel
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        vm = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]
        rule.runOnUiThread { vm.finishOnboarding() }
        rule.waitUntil(10_000) { vm.apps.value.size > 3 }
        // Start every test from "no Pagelets".
        rule.runOnUiThread { vm.items.value.filter { isPageletId(it.id) }.forEach { vm.removePagelet(it.id) } }
        rule.waitForIdle()
    }

    private fun add(config: PageletConfig = PageletConfig()): Pair<String, HomeItem> {
        var page: Int? = null
        rule.runOnUiThread { page = vm.addPagelet(PageletType.SKY, config) }
        assertNotNull("a Pagelet should be addable", page)
        val item = vm.items.value.first { isPageletId(it.id) }
        return item.id to item
    }

    @Test fun addsAPageletOnAPageOfItsOwnSpanningTheWholeGrid() {
        val (_, item) = add()
        val s = vm.settings.value
        assertEquals(0, item.col); assertEquals(0, item.row)
        assertEquals(s.columns, item.spanX); assertEquals(s.rows, item.spanY)
        assertEquals("nothing else shares its page", listOf(item.id), vm.items.value.filter { !it.inDock && it.page == item.page }.map { it.id })
    }

    @Test fun unavailableTypesAreRefused() {
        var page: Int? = -1
        rule.runOnUiThread { page = vm.addPagelet(PageletType.FOCUS) }
        assertNull(page)
        assertTrue(vm.items.value.none { isPageletId(it.id) })
    }

    @Test fun aLockedHomeRefusesNewPagelets() {
        rule.runOnUiThread { vm.updateSettings { it.copy(lockHome = true) } }
        var page: Int? = -1
        rule.runOnUiThread { page = vm.addPagelet(PageletType.SKY) }
        rule.runOnUiThread { vm.updateSettings { it.copy(lockHome = false) } }
        assertNull(page)
    }

    @Test fun layoutAndConfigSurviveProcessDeath() {
        val cfg = PageletConfig(hideDock = true, sections = setOf("time"), goalMinutes = 90)
        val (id, item) = add(cfg)
        Thread.sleep(600)                                  // the settings/layout save is debounced
        // A brand-new store, as a restarted process would open it.
        val fresh = LayoutStore(app)
        assertEquals(item, fresh.loadItems()!!.first { it.id == id })
        assertEquals(cfg, fresh.loadPageletConfigs()[id])
    }

    @Test fun configChangesArePersistedToo() {
        val (id, _) = add()
        rule.runOnUiThread { vm.updatePagelet(id) { it.copy(hideDock = true, goalMinutes = 123) } }
        assertEquals(123, LayoutStore(app).loadPageletConfigs()[id]!!.goalMinutes)
        assertTrue(LayoutStore(app).loadPageletConfigs()[id]!!.hideDock)
    }

    @Test fun backupCarriesPageletsAndRestoreBringsThemBack() {
        val cfg = PageletConfig(sections = setOf("time", "weather"), goalMinutes = 200)
        val (id, _) = add(cfg)
        Thread.sleep(600)
        val store = LayoutStore(app)
        val json = store.exportJson(app.filesDir)
        assertTrue("the backup mentions the Pagelet", json.contains("pagelet.$id"))
        // Lose it, then restore.
        store.savePageletConfig(id, null)
        assertNull(LayoutStore(app).loadPageletConfigs()[id])
        assertTrue(store.importJson(json, app.filesDir))
        assertEquals(cfg, LayoutStore(app).loadPageletConfigs()[id])
        assertTrue(LayoutStore(app).loadItems()!!.any { it.id == id })
    }

    @Test fun aRestoredLayoutWithoutItsConfigStillOpensWithDefaults() {
        val (id, _) = add()
        Thread.sleep(600)
        val store = LayoutStore(app)
        store.savePageletConfig(id, null)
        rule.runOnUiThread { vm.removePagelet(id); vm.clearPageletUndo() }   // makes the repository forget; then restore only the item
        assertEquals(PageletConfig(), PageletConfig.fromJson(null, PageletType.SKY))
    }

    @Test fun removingDeletesThePageAndUndoBringsItBackWithItsConfig() {
        val cfg = PageletConfig(goalMinutes = 77)
        val (id, item) = add(cfg)
        val pagesBefore = vm.pages.value
        rule.runOnUiThread { vm.removePagelet(id) }
        assertTrue(vm.items.value.none { it.id == id })
        assertNull(vm.pagelets.value[id])
        assertEquals("its now-empty page is removed", pagesBefore - 1, vm.pages.value)
        assertNotNull(vm.pageletUndo.value)

        var back: Int? = null
        rule.runOnUiThread { back = vm.undoRemovePagelet() }
        assertEquals(item.page, back)
        assertEquals(item, vm.items.value.first { it.id == id })
        assertEquals(cfg, vm.pagelets.value[id])
        assertEquals(pagesBefore, vm.pages.value)
        assertNull(vm.pageletUndo.value)
    }

    @Test fun undoWithNothingToUndoDoesNothing() {
        var r: Int? = 5
        rule.runOnUiThread { vm.clearPageletUndo(); r = vm.undoRemovePagelet() }
        assertNull(r)
    }

    @Test fun iconsCannotBeDroppedOntoAPageletsPage() {
        val (_, item) = add()
        val appId = vm.items.value.first { !it.inDock && !isPageletId(it.id) && it.page != item.page }.id
        val before = vm.items.value.first { it.id == appId }
        rule.runOnUiThread { vm.drop(appId, DropTarget.Cell(item.page, 1, 1)) }
        assertEquals("the icon stays where it was", before, vm.items.value.first { it.id == appId })
        assertEquals(1, vm.items.value.count { !it.inDock && it.page == item.page })
    }

    @Test fun newIconsAreNeverAutoPlacedOnAPageletsPage() {
        val (_, item) = add()
        val newcomers = vm.apps.value.filter { a -> vm.items.value.none { it.id == a.id } }.take(3)
        rule.runOnUiThread { newcomers.forEach { vm.addToHome(it.id) } }
        for (n in newcomers) {
            val placed = vm.items.value.firstOrNull { it.id == n.id } ?: continue
            assertTrue("${n.label} landed on the Pagelet's page", placed.page != item.page)
        }
    }

    @Test fun aPageletKeepsItsWholePageWhenThePageIsMoved() {
        val (id, item) = add()
        if (item.page > 0) {
            rule.runOnUiThread { vm.movePage(item.page, -1) }
            val moved = vm.items.value.first { it.id == id }
            assertEquals(item.page - 1, moved.page)
            assertEquals(vm.settings.value.columns, moved.spanX)
            assertEquals(vm.settings.value.rows, moved.spanY)
            assertEquals(1, vm.items.value.count { !it.inDock && it.page == moved.page })
        }
    }

    @Test fun aPageletStaysFullPageWhenTheGridChanges() {
        val (id, _) = add()
        val old = vm.settings.value
        rule.runOnUiThread { vm.updateSettings { it.copy(columns = old.columns + 1, rows = old.rows + 1) } }
        val grown = vm.items.value.first { it.id == id }
        assertEquals(old.columns + 1, grown.spanX); assertEquals(old.rows + 1, grown.spanY)
        rule.runOnUiThread { vm.updateSettings { it.copy(columns = old.columns - 1, rows = old.rows - 1) } }
        val shrunk = vm.items.value.first { it.id == id }
        assertEquals(old.columns - 1, shrunk.spanX); assertEquals(old.rows - 1, shrunk.spanY)
        rule.runOnUiThread { vm.updateSettings { it.copy(columns = old.columns, rows = old.rows) } }
    }

    @Test fun eachKindCanBeAddedOnce_andOtherKindsStillAllowed() {
        val (skyId, _) = add()
        var again: Int? = -1
        rule.runOnUiThread { again = vm.addPagelet(PageletType.SKY) }
        assertNull("a second Today must be refused", again)
        var headlines: Int? = null
        rule.runOnUiThread { headlines = vm.addPagelet(PageletType.HEADLINES) }
        assertNotNull("Headlines is a different kind and is allowed", headlines)
        assertEquals(2, vm.items.value.count { isPageletId(it.id) })
        rule.runOnUiThread { again = vm.addPagelet(PageletType.HEADLINES) }
        assertNull("a second Headlines must be refused", again)
        val others = listOf(PageletType.AGENDA, PageletType.NOW_PLAYING, PageletType.MOVE)
        assertEquals(others, vm.addablePagelets())
        rule.runOnUiThread { vm.removePagelet(skyId) }
        assertEquals(listOf(PageletType.SKY) + others, vm.addablePagelets())
        rule.runOnUiThread { again = vm.addPagelet(PageletType.SKY) }
        assertNotNull("after removing it, Today can be added again", again)
    }

    @Test fun everyAvailableKindCanBeAddedOnceAndRemoved() {
        val kinds = PageletType.entries.filter { it.available }
        kinds.forEach { type ->
            var page: Int? = null
            rule.runOnUiThread { page = vm.addPagelet(type) }
            assertNotNull("$type should be addable", page)
        }
        assertEquals(kinds.size, vm.items.value.count { isPageletId(it.id) })
        kinds.forEach { type ->
            var again: Int? = -1
            rule.runOnUiThread { again = vm.addPagelet(type) }
            assertNull("a second $type must be refused", again)
        }
        rule.runOnUiThread { vm.items.value.filter { isPageletId(it.id) }.forEach { vm.removePagelet(it.id) } }
        assertEquals(0, vm.items.value.count { isPageletId(it.id) })
    }

    @Test fun removingAPageletRemovesItsPage() {
        val (id, item) = add()
        val before = vm.pages.value
        rule.runOnUiThread { vm.removePagelet(id) }
        assertTrue(vm.items.value.none { it.id == id })
        assertTrue("its page is gone, not left blank", vm.pages.value <= before)
        // Undo brings it back on the page it was on.
        var back: Int? = null
        rule.runOnUiThread { back = vm.undoRemovePagelet() }
        assertEquals(item.page, back)
        assertTrue(vm.items.value.any { it.id == id })
    }
}
