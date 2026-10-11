package com.snapkode.nivolauncher

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.data.NewsSource
import com.snapkode.nivolauncher.data.news.NewsCatalog
import com.snapkode.nivolauncher.data.news.NewsFeedRef
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Fetches every feed the catalog offers, for real, and reports which ones work. Skipped when offline. */
@RunWith(AndroidJUnit4::class)
class NewsCatalogLiveTest {
    @Test fun everyCatalogFeedIsReachableAndReadable() {
        val refs = NewsCatalog.providers.flatMap { p -> p.feeds.map { (t, u) -> Triple(p.id, t.name, u) } }.distinctBy { it.third }
        val pool = Executors.newFixedThreadPool(6)
        val results = refs.map { (id, topic, url) ->
            pool.submit<String> {
                val f = NewsSource.fetch(NewsFeedRef(id, url))
                if (f == null) "FAIL  $id/$topic  $url"
                else "OK    $id/$topic  items=${f.items.size} images=${f.items.count { it.imageUrl != null }} dated=${f.items.count { it.publishedAt > 0 }} title='${f.title}'"
            }
        }.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown(); pool.awaitTermination(5, TimeUnit.SECONDS)
        results.forEach { Log.d("NEWSLIVE", it) }
        val ok = results.count { it.startsWith("OK") }
        assumeTrue("offline: nothing reachable", ok > 0)
        assertTrue("only $ok of ${results.size} feeds worked", ok >= results.size * 0.7)
    }
}
