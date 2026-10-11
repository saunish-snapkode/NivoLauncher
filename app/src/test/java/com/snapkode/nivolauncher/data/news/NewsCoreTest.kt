package com.snapkode.nivolauncher.data.news

import com.snapkode.nivolauncher.data.NewsItem
import com.snapkode.nivolauncher.data.NewsTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsTextTest {
    @Test fun cleanDropsTagsAndDecodesEntities() {
        assertEquals("Fish & chips", NewsText.clean("<b>Fish</b> &amp; chips"))
        assertEquals("It's “quoted” — fine…", NewsText.clean("It&#39;s &ldquo;quoted&rdquo; &mdash; fine&hellip;"))
        assertEquals("Café", NewsText.clean("Caf&#xE9;"))
        assertEquals("a b", NewsText.clean("  a \n\t b  "))
    }

    @Test fun doubleEscapedFeedsAreStillReadable() = assertEquals("Tom & Jerry", NewsText.clean("Tom &amp;amp; Jerry"))

    @Test fun nullAndBlankAreEmpty() { assertEquals("", NewsText.clean(null)); assertEquals("", NewsText.clean("   ")) }

    @Test fun badNumericEntitiesAreLeftAlone() = assertEquals("x &#99999999999; y", NewsText.clean("x &#99999999999; y"))

    @Test fun firstImageFindsTheFirstOne() =
        assertEquals("https://a/1.jpg", NewsText.firstImage("""<p><img class="x" src='https://a/1.jpg'><img src="https://a/2.jpg"></p>"""))

    @Test fun firstImageSeesThroughEscapedHtml() = assertEquals("https://a/1.jpg", NewsText.firstImage("&lt;img src=&quot;https://a/1.jpg&quot;&gt;"))

    @Test fun noImageIsNull() = assertNull(NewsText.firstImage("<p>just text</p>"))

    @Test fun imageUrlRules() {
        assertEquals("https://a/b.jpg", NewsText.imageUrl("https://a/b.jpg"))
        assertEquals("https://a/b.jpg", NewsText.imageUrl("http://a/b.jpg"))
        assertEquals("https://a/b.jpg", NewsText.imageUrl("//a/b.jpg"))
        assertEquals("https://a/b.jpg?x=1&y=2", NewsText.imageUrl("https://a/b.jpg?x=1&amp;y=2"))
        assertNull(NewsText.imageUrl("/relative.jpg")); assertNull(NewsText.imageUrl("javascript:alert(1)")); assertNull(NewsText.imageUrl(" ")); assertNull(NewsText.imageUrl(null))
    }
}

class NewsDateTest {
    @Test fun formats() {
        val z = 1_791_702_668_000L                                           // 2026-10-11T07:11:08Z
        assertEquals(z, NewsDate.parse("Sun, 11 Oct 2026 07:11:08 GMT"))
        assertEquals(z, NewsDate.parse("Sun, 11 Oct 2026 12:41:08 +0530"))
        assertEquals(z, NewsDate.parse("11 Oct 2026 07:11:08 GMT"))
        assertEquals(z, NewsDate.parse("2026-10-11T07:11:08Z"))
        assertEquals(z, NewsDate.parse("2026-10-11T12:41:08+05:30"))
        assertEquals(z + 123, NewsDate.parse("2026-10-11T07:11:08.123Z"))
        assertEquals("long fractions are trimmed", z + 123, NewsDate.parse("2026-10-11T07:11:08.123456789Z"))
    }

    @Test fun junkIsZero() { assertEquals(0L, NewsDate.parse("yesterday")); assertEquals(0L, NewsDate.parse("")); assertEquals(0L, NewsDate.parse(null)) }
}

class NewsFeedUrlTest {
    @Test fun acceptsAndCanonicalises() {
        assertEquals("https://example.com/feed.xml", NewsFeedUrl.normalise("https://example.com/feed.xml"))
        assertEquals("https://example.com/feed", NewsFeedUrl.normalise("example.com/feed"))
        assertEquals("https://example.com/rss", NewsFeedUrl.normalise("http://example.com/rss"))
        assertEquals("https://example.com/rss", NewsFeedUrl.normalise("feed://example.com/rss"))
        assertEquals("https://example.com/rss", NewsFeedUrl.normalise("  <https://example.com/rss>  "))
        assertEquals("https://news.example.co.uk:8443/x", NewsFeedUrl.normalise("https://news.example.co.uk:8443/x"))
    }

    @Test fun refusesWhatIsNotAFeedAddress() {
        for (bad in listOf("", "   ", "not a url", "javascript:alert(1)", "file:///etc/passwd", "ftp://example.com/x", "intent://x#Intent;end", "localhost", "https://", "https://.com", "https://exa mple.com", "https://-.", "https://ex_ample.com/x"))
            assertNull("should refuse '$bad'", NewsFeedUrl.normalise(bad))
    }

    @Test fun nameFromTheSite() {
        assertEquals("example.com", NewsFeedUrl.nameFrom("https://www.example.com/feed"))
        assertEquals("bbci.co.uk", NewsFeedUrl.nameFrom("https://feeds.bbci.co.uk/news/rss.xml"))
    }
}

class NewsMergeTest {
    private fun n(title: String, link: String, t: Long, src: String = "S") = NewsItem(title, src, link, t)
    private fun prefs(order: NewsOrder = NewsOrder.Newest, per: Int = 10) = NewsPrefs(order = order, perSource = per)

    @Test fun newestFirstAcrossSources() {
        val a = listOf(n("a1", "https://a/1", 300), n("a2", "https://a/2", 100))
        val b = listOf(n("b1", "https://b/1", 200))
        assertEquals(listOf("a1", "b1", "a2"), NewsMerge.merge(listOf(a, b), prefs()).map { it.title })
    }

    @Test fun balancedTakesTurnsButLeadsWithTheFreshest() {
        val a = listOf(n("a1", "https://a/1", 900), n("a2", "https://a/2", 800), n("a3", "https://a/3", 700))
        val b = listOf(n("b1", "https://b/1", 950), n("b2", "https://b/2", 100))
        assertEquals(listOf("b1", "a1", "a2", "b2", "a3"), NewsMerge.merge(listOf(a, b), prefs(NewsOrder.Balanced)).map { it.title })
    }

    @Test fun balancedStopsOneSourceFloodingThePage() {
        val chatty = (1..30).map { n("c$it", "https://c/$it", 1000L - it) }
        val quiet = listOf(n("q1", "https://q/1", 1))
        val top5 = NewsMerge.merge(listOf(chatty, quiet), prefs(NewsOrder.Balanced)).take(2).map { it.title }
        assertTrue("the quiet source appears right away", "q1" in top5)
    }

    @Test fun perSourceCapApplies() {
        val a = (1..20).map { n("a$it", "https://a/$it", 100L - it) }
        assertEquals(5, NewsMerge.merge(listOf(a), prefs(per = 5)).size)
    }

    @Test fun capKeepsTheNewestNotJustTheFirstListed() {
        val a = listOf(n("old", "https://a/1", 1), n("new", "https://a/2", 999))
        assertEquals(listOf("new"), NewsMerge.merge(listOf(a), prefs(per = 1)).map { it.title })
    }

    @Test fun theSameStoryTwiceIsShownOnce() {
        val a = listOf(n("Big news today", "https://a.com/x?utm_source=rss", 10))
        val b = listOf(n("Big news today", "https://www.a.com/x/", 20), n("Different", "https://b.com/y", 5))
        val out = NewsMerge.merge(listOf(a, b), prefs())
        assertEquals(2, out.size)
    }

    @Test fun sameHeadlineFromTwoSourcesIsShownOnce() {
        val a = listOf(n("Prime Minister resigns!", "https://a/1", 10, "A"))
        val b = listOf(n("Prime minister resigns", "https://b/2", 20, "B"))
        assertEquals(1, NewsMerge.merge(listOf(a, b), prefs()).size)
    }

    @Test fun undatedStoriesKeepTheirFeedOrderAfterDatedOnes() {
        val a = listOf(n("u1", "https://a/1", 0), n("d1", "https://a/2", 50), n("u2", "https://a/3", 0))
        assertEquals(listOf("d1", "u1", "u2"), NewsMerge.merge(listOf(a), prefs()).map { it.title })
    }

    @Test fun limitIsHonouredAndEmptyIsEmpty() {
        val a = (1..50).map { n("a$it", "https://a/$it", it.toLong()) }
        assertEquals(10, NewsMerge.merge(listOf(a), prefs(per = 50), limit = 10).size)
        assertTrue(NewsMerge.merge(emptyList(), prefs()).isEmpty()); assertTrue(NewsMerge.merge(listOf(emptyList()), prefs()).isEmpty())
    }
}

class NewsCatalogTest {
    @Test fun idsAreUniqueAndNamesPresent() {
        val ids = NewsCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(NewsCatalog.providers.all { it.name.isNotBlank() && it.about.isNotBlank() })
    }

    @Test fun everyFeedIsHttpsAndEveryProviderHasTop() {
        for (p in NewsCatalog.providers) {
            assertTrue("${p.id} has Top", NewsTopic.Top in p.feeds)
            p.feeds.values.forEach { assertTrue("${p.id}: $it", it.startsWith("https://") && !it.contains(' ')) }
            assertTrue(p.home.startsWith("https://"))
        }
    }

    @Test fun theDefaultSourceExists() = assertNotNull(NewsCatalog.find(NewsPrefs().providers.single()))

    @Test fun planUsesEachChosenSourcesFeedForTheTopic() {
        val prefs = NewsPrefs(providers = setOf("bbc", "guardian"))
        val plan = NewsCatalog.plan(prefs, NewsTopic.World)
        assertEquals(listOf("BBC News", "The Guardian"), plan.map { it.sourceName })
        assertTrue(plan.all { it.url.startsWith("https://") })
    }

    @Test fun aSourceWithoutATopicIsSkippedForIt() {
        val plan = NewsCatalog.plan(NewsPrefs(providers = setOf("bbc", "aljazeera")), NewsTopic.Sports)
        assertEquals(listOf("BBC News"), plan.map { it.sourceName })
    }

    @Test fun customFeedsAreTopOnly() {
        val prefs = NewsPrefs(providers = emptySet(), custom = listOf(NewsCustomFeed("My blog", "https://blog.example.com/feed")))
        assertEquals(listOf("My blog"), NewsCatalog.plan(prefs, NewsTopic.Top).map { it.sourceName })
        assertTrue(NewsCatalog.plan(prefs, NewsTopic.Sports).isEmpty())
    }

    @Test fun noSourcesMeansNothingToFetch() = assertTrue(NewsCatalog.plan(NewsPrefs(providers = emptySet()), NewsTopic.Top).isEmpty())

    @Test fun oneFeedUsedForTwoTopicsIsFetchedOnce() {
        val prefs = NewsPrefs(providers = setOf("verge", "hn"))
        assertEquals(2, NewsCatalog.plan(prefs, NewsTopic.Technology).size)
        // A custom feed that duplicates a catalog address collapses too.
        val dup = NewsPrefs(providers = setOf("bbc"), custom = listOf(NewsCustomFeed("BBC again", "https://feeds.bbci.co.uk/news/rss.xml")))
        assertEquals(1, NewsCatalog.plan(dup, NewsTopic.Top).size)
    }

    @Test fun topicChipsAreOnlyThoseSomeChosenSourceCovers() {
        assertEquals(listOf(NewsTopic.Top), NewsCatalog.topicsFor(NewsPrefs(providers = setOf("aljazeera"))))
        assertTrue(NewsTopic.Sports in NewsCatalog.topicsFor(NewsPrefs(providers = setOf("aljazeera", "espn"))))
        assertEquals("with nothing chosen there is still Top", listOf(NewsTopic.Top), NewsCatalog.topicsFor(NewsPrefs(providers = emptySet())))
        assertEquals(NewsTopic.entries.size, NewsCatalog.topicsFor(NewsPrefs(providers = setOf("bbc"))).size)
    }

    @Test fun summaryReadsNaturally() {
        assertEquals("No sources chosen", NewsCatalog.summary(NewsPrefs(providers = emptySet())))
        assertEquals("BBC News", NewsCatalog.summary(NewsPrefs()))
        assertEquals("BBC News, NPR", NewsCatalog.summary(NewsPrefs(providers = setOf("bbc", "npr"))))
        assertEquals("BBC News, The Guardian +2", NewsCatalog.summary(NewsPrefs(providers = setOf("bbc", "guardian", "npr"), custom = listOf(NewsCustomFeed("Blog", "https://b.com/f")))))
    }

    @Test fun categoriesAreAllUsed() = assertTrue(NewsCategory.entries.all { c -> NewsCatalog.providers.any { it.category == c } })

    @Test fun styleAndOrderHaveLabels() {
        assertTrue(NewsStyle.entries.all { it.label.isNotBlank() }); assertTrue(NewsOrder.entries.all { it.label.isNotBlank() && it.about.isNotBlank() })
        assertFalse(NewsPrefs.PerSourceChoices.isEmpty())
    }
}
