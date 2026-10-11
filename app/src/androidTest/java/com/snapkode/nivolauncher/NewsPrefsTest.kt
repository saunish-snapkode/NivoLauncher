package com.snapkode.nivolauncher

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.data.LayoutStore
import com.snapkode.nivolauncher.data.NewsTopic
import com.snapkode.nivolauncher.data.news.NewsCustomFeed
import com.snapkode.nivolauncher.data.news.NewsOrder
import com.snapkode.nivolauncher.data.news.NewsPrefs
import com.snapkode.nivolauncher.data.news.NewsStyle
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A Headlines Pagelet's news choices survive a restart and travel in backups; a phone from before News was a Pagelet is read back correctly. */
@RunWith(AndroidJUnit4::class)
class NewsPrefsTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences("nivo_launcher", android.content.Context.MODE_PRIVATE)

    /** Leave a clean slate for whichever test runs next. */
    @org.junit.After fun cleanUp() { prefs.edit().clear().commit() }

    private val chosen = PageletConfig(
        news = NewsPrefs(
            providers = setOf("guardian", "thehindu", "npr"),
            custom = listOf(NewsCustomFeed("My, \"odd\" blog", "https://blog.example.com/feed?x=1&y=2")),
            style = NewsStyle.Compact, images = false, perSource = 5, order = NewsOrder.Newest,
        ),
        newsTopic = NewsTopic.Technology,
    )

    @Test fun choicesRoundTripThroughTheStore() {
        val id = "nivo:pagelet:HEADLINES:test0001"
        LayoutStore(app).savePageletConfig(id, chosen)
        assertEquals(chosen, LayoutStore(app).loadPageletConfigs()[id])
        LayoutStore(app).savePageletConfig(id, null)
    }

    @Test fun backupCarriesTheNewsChoices() {
        val store = LayoutStore(app)
        val id = "nivo:pagelet:HEADLINES:test0002"
        store.savePageletConfig(id, chosen)
        val json = store.exportJson(app.filesDir)
        store.savePageletConfig(id, PageletConfig())
        assertTrue(store.importJson(json, app.filesDir))
        assertEquals(chosen, LayoutStore(app).loadPageletConfigs()[id])
        store.savePageletConfig(id, null)
    }

    // ---- the old separate News page ----------------------------------------------------------------------------------

    @Test fun aPhoneThatNeverHadTheOldPageHasNothingToMigrate() {
        prefs.edit().clear().commit()
        assertNull(LayoutStore(app).loadLegacyNews())
    }

    @Test fun theOldPagesSettingsAreReadBack() {
        prefs.edit().clear()
            .putBoolean("newsPage", true).putInt("newsTopic", NewsTopic.Sports.ordinal)
            .putStringSet("newsProviders", setOf("espn")).putString("newsCustom", """[{"name":"Mine","url":"https://m.example.com/rss"}]""")
            .putInt("newsStyle", NewsStyle.Headlines.ordinal).putBoolean("newsImages", false).putInt("newsPerSource", 3).putInt("newsOrder", NewsOrder.Newest.ordinal)
            .commit()
        val legacy = LayoutStore(app).loadLegacyNews()!!
        assertTrue(legacy.enabled)
        assertEquals(NewsTopic.Sports, legacy.topic)
        assertEquals(
            NewsPrefs(setOf("espn"), listOf(NewsCustomFeed("Mine", "https://m.example.com/rss")), NewsStyle.Headlines, false, 3, NewsOrder.Newest),
            legacy.news,
        )
    }

    @Test fun anOldInstallThatNeverChoseKeepsReadingTheBbc() {
        prefs.edit().clear().putBoolean("newsPage", true).commit()
        val legacy = LayoutStore(app).loadLegacyNews()!!
        assertEquals(NewsPrefs(), legacy.news)
        assertEquals(setOf("bbc"), legacy.news.providers)
    }

    @Test fun anEmptyOldChoiceIsRememberedAsEmpty() {
        prefs.edit().clear().putBoolean("newsPage", true).putStringSet("newsProviders", emptySet()).commit()
        assertEquals(emptySet<String>(), LayoutStore(app).loadLegacyNews()!!.news.providers)
    }

    @Test fun corruptOrAbsurdOldValuesAreSafe() {
        prefs.edit().clear().putBoolean("newsPage", true).putString("newsCustom", "{not json").putInt("newsPerSource", 9999).putInt("newsStyle", 99).commit()
        val n = LayoutStore(app).loadLegacyNews()!!.news
        assertEquals(emptyList<NewsCustomFeed>(), n.custom)
        assertEquals(50, n.perSource); assertEquals(NewsPrefs().style, n.style)
    }

    @Test fun clearingTheOldKeysEndsTheMigration() {
        prefs.edit().clear().putBoolean("newsPage", true).putInt("newsTopic", 2).commit()
        LayoutStore(app).clearLegacyNews()
        assertNull(LayoutStore(app).loadLegacyNews())
    }
}
