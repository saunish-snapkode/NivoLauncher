package com.snapkode.nivolauncher

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.LayoutStore
import com.snapkode.nivolauncher.data.NewsTopic
import com.snapkode.nivolauncher.data.news.NewsPrefs
import com.snapkode.nivolauncher.data.news.NewsStyle
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.pagelet.isPageletId
import com.snapkode.nivolauncher.pagelet.pageletTypeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A phone that had the old News page gets a Headlines Pagelet in the same place, with the same choices; a phone that had it switched off gets none. */
@RunWith(AndroidJUnit4::class)
class HeadlinesMigrationTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences("nivo_launcher", android.content.Context.MODE_PRIVATE)

    /** Leave a clean slate for whichever test runs next. */
    @org.junit.After fun cleanUp() { prefs.edit().clear().commit() }

    private fun launchWith(newsOn: Boolean): Triple<List<HomeItem>, Map<String, com.snapkode.nivolauncher.pagelet.PageletConfig>, Int> {
        prefs.edit().clear().commit()
        val store = LayoutStore(app)
        // An existing layout (any item will do) so this is an upgrade, not a first run.
        store.saveItems(listOf(HomeItem("builtin:nothing", 0, 0, 0)))
        store.savePageCount(1)
        prefs.edit()
            .putBoolean("newsPage", newsOn).putInt("newsTopic", NewsTopic.Business.ordinal)
            .putStringSet("newsProviders", setOf("guardian")).putInt("newsStyle", NewsStyle.Compact.ordinal)
            .commit()
        var out: Triple<List<HomeItem>, Map<String, com.snapkode.nivolauncher.pagelet.PageletConfig>, Int>? = null
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var vm: LauncherViewModel
            sc.onActivity { vm = ViewModelProvider(it)[LauncherViewModel::class.java] }
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && LayoutStore(app).loadLegacyNews() != null) Thread.sleep(50)
            Thread.sleep(300)
            out = Triple(vm.items.value, vm.pagelets.value, vm.settings.value.defaultPage)
        }
        return out!!
    }

    @Test fun theOldPageIsDroppedAndNothingTakesItsPlace() {
        val (items, configs, home) = launchWith(newsOn = true)
        assertTrue(items.none { isPageletId(it.id) })
        assertTrue(configs.isEmpty())
        assertEquals(0, home)
        assertNull(LayoutStore(app).loadLegacyNews())
    }

    @Test fun anOldPageThatWasSwitchedOffAddsNothing() {
        val (items, _, home) = launchWith(newsOn = false)
        assertTrue(items.none { isPageletId(it.id) })
        assertEquals(0, home)
        assertNull(LayoutStore(app).loadLegacyNews())
    }

    @Test fun aHeadlinesPageAddedByAnEarlierBuildIsRemovedOnce_thenUserAddedOnesStay() {
        prefs.edit().clear().commit()
        val store = LayoutStore(app)
        val id = "nivo:pagelet:HEADLINES:auto0001"
        store.saveItems(listOf(HomeItem(id, 0, 0, 0, 4, 6), HomeItem("builtin:nothing", 1, 0, 0)))
        store.savePageCount(2)
        store.savePageletConfig(id, com.snapkode.nivolauncher.pagelet.PageletConfig())
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var vm: LauncherViewModel
            sc.onActivity { vm = ViewModelProvider(it)[LauncherViewModel::class.java] }
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && !LayoutStore(app).autoHeadlinesCleaned()) Thread.sleep(50)
            Thread.sleep(500)
            assertTrue(vm.items.value.none { isPageletId(it.id) })
            // Now the user adds one: it must survive the next launch.
            sc.onActivity { vm.addPagelet(PageletType.HEADLINES) }
            Thread.sleep(300)
            assertTrue(vm.items.value.any { isPageletId(it.id) })
        }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var vm: LauncherViewModel
            sc.onActivity { vm = ViewModelProvider(it)[LauncherViewModel::class.java] }
            Thread.sleep(800)
            assertTrue(vm.items.value.any { isPageletId(it.id) })
        }
    }
}
