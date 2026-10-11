package com.snapkode.nivolauncher.data.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class NewsParserTest {
    private fun parse(xml: String, ns: Boolean = false, max: Int = 60): ParsedFeed {
        val p = KXmlParser()
        p.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", ns)
        p.setInput(StringReader(xml))
        return NewsParser.parse(p, "Src", max)
    }

    private val rss = """<?xml version="1.0"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/">
<channel><title>Example &amp; Co. News</title><link>https://example.com</link>
 <image><url>https://example.com/logo.png</url></image>
 <item>
  <title><![CDATA[Markets rally &#39;strongly&#39; as rates fall]]></title>
  <link>https://example.com/a?utm_source=rss</link>
  <guid isPermaLink="false">abc-1</guid>
  <pubDate>Sun, 11 Oct 2026 07:11:08 GMT</pubDate>
  <media:thumbnail url="http://img.example.com/240/a.jpg" width="240" height="135"/>
  <media:content url="https://img.example.com/976/a.jpg" width="976" medium="image"/>
  <description><![CDATA[<p>Hello</p>]]></description>
 </item>
 <item>
  <title>Second story</title>
  <link>https://example.com/b</link>
  <dc:date>2026-10-11T05:00:00+05:30</dc:date>
  <enclosure url="//cdn.example.com/b.png" type="image/png" length="1"/>
 </item>
 <item>
  <title>Third: picture only in the text</title>
  <guid isPermaLink="true">https://example.com/c</guid>
  <description>&lt;p&gt;Look &lt;img src="https://img.example.com/c.jpg" alt=""&gt; here&lt;/p&gt;</description>
 </item>
 <item><title>No link, no guid</title></item>
 <item><link>https://example.com/untitled</link></item>
</channel></rss>"""

    @Test fun readsTheChannelTitleAndSkipsIncompleteItems() {
        val f = parse(rss)
        assertEquals("Example & Co. News", f.title)
        assertEquals(3, f.items.size)
    }

    @Test fun titlesAreCleanedOfCdataAndEntities() = assertEquals("Markets rally 'strongly' as rates fall", parse(rss).items[0].title)

    @Test fun theWidestPictureWinsAndIsHttps() = assertEquals("https://img.example.com/976/a.jpg", parse(rss).items[0].imageUrl)

    @Test fun anImageEnclosureIsUsedAndProtocolRelativeGetsHttps() = assertEquals("https://cdn.example.com/b.png", parse(rss).items[1].imageUrl)

    @Test fun aPictureInsideTheStoryHtmlIsFound() = assertEquals("https://img.example.com/c.jpg", parse(rss).items[2].imageUrl)

    @Test fun aPermalinkGuidStandsInForAMissingLink() = assertEquals("https://example.com/c", parse(rss).items[2].link)

    @Test fun rfc822AndIsoDatesBothWork() {
        val f = parse(rss)
        assertEquals(1_791_702_668_000L, f.items[0].publishedAt)               // 2026-10-11T07:11:08Z
        assertEquals(1_791_675_000_000L, f.items[1].publishedAt)               // 05:00 at +05:30 = 2026-10-10T23:30Z
        assertEquals("a story with no date is not given a made-up one", 0L, f.items[2].publishedAt)
    }

    @Test fun theSourceNameIsStamped() = assertTrue(parse(rss).items.all { it.source == "Src" })

    @Test fun worksWithANamespaceAwareParserToo() {
        val f = parse(rss, ns = true)
        assertEquals(3, f.items.size); assertEquals("https://img.example.com/976/a.jpg", f.items[0].imageUrl)
    }

    private val atom = """<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
 <title>Atom Blog</title>
 <entry>
  <title type="html">Atom &amp;amp; story</title>
  <link rel="self" href="https://blog.example.com/feed/1"/>
  <link rel="alternate" href="https://blog.example.com/1"/>
  <id>urn:uuid:1</id>
  <published>2026-10-10T12:00:00.123456Z</published>
  <updated>2026-10-11T12:00:00Z</updated>
  <content type="html">&lt;img src="https://blog.example.com/i.jpg"&gt;text</content>
 </entry>
 <entry>
  <title>Updated only</title>
  <link href="https://blog.example.com/2"/>
  <updated>2026-10-09T12:00:00+00:00</updated>
  <media:thumbnail url="https://blog.example.com/t.jpg"/>
 </entry>
</feed>"""

    @Test fun readsAtom() {
        val f = parse(atom)
        assertEquals("Atom Blog", f.title); assertEquals(2, f.items.size)
        assertEquals("the alternate link, not the feed's own", "https://blog.example.com/1", f.items[0].link)
        assertEquals("Atom & story", f.items[0].title)
        assertEquals("https://blog.example.com/i.jpg", f.items[0].imageUrl)
        assertEquals("https://blog.example.com/t.jpg", f.items[1].imageUrl)
    }

    @Test fun publishedBeatsUpdatedAndUpdatedIsTheFallback() {
        val f = parse(atom)
        assertEquals(1_791_633_600_123L, f.items[0].publishedAt)                // published 2026-10-10T12:00:00.123Z
        assertEquals(1_791_547_200_000L, f.items[1].publishedAt)                // only updated 2026-10-09T12:00Z
    }

    @Test fun readsRssOnePointZeroRdf() {
        val rdf = """<?xml version="1.0"?><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel><title>RDF Feed</title></channel>
          <item><title>RDF story</title><link>https://rdf.example.com/1</link><dc:date>2026-10-11T00:00:00Z</dc:date></item></rdf:RDF>"""
        val f = parse(rdf)
        assertEquals(1, f.items.size); assertEquals("RDF story", f.items[0].title); assertEquals(1_791_676_800_000L, f.items[0].publishedAt)
    }

    @Test fun relativeAndNonWebPicturesAreDropped() {
        val x = """<rss><channel><item><title>T</title><link>https://x.com/1</link><media:thumbnail xmlns:media="m" url="/relative.jpg"/></item>
            <item><title>U</title><link>https://x.com/2</link><enclosure url="data:image/png;base64,AAA" type="image/png"/></item></channel></rss>"""
        val f = parse(x)
        assertNull(f.items[0].imageUrl); assertNull(f.items[1].imageUrl)
    }

    @Test fun maxItemsIsHonoured() {
        val many = "<rss><channel>" + (1..100).joinToString("") { "<item><title>t$it</title><link>https://x.com/$it</link></item>" } + "</channel></rss>"
        assertEquals(25, parse(many, max = 25).items.size)
    }

    @Test fun bbcThumbnailsAreEnlarged() {
        val x = """<rss><channel><item><title>T</title><link>https://bbc.com/1</link>
          <media:thumbnail xmlns:media="m" url="https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/a.jpg" width="240"/></item></channel></rss>"""
        assertEquals("https://ichef.bbci.co.uk/ace/standard/800/cpsprodpb/a.jpg", parse(x).items[0].imageUrl)
    }

    @Test fun otherPublishersPicturesAreLeftAlone() {
        val x = """<rss><channel><item><title>T</title><link>https://g.com/1</link>
          <media:content xmlns:media="m" url="https://i.guim.co.uk/img/500/x.jpg" width="500"/></item></channel></rss>"""
        assertEquals("https://i.guim.co.uk/img/500/x.jpg", parse(x).items[0].imageUrl)
    }

    @Test fun anEmptyFeedIsEmptyNotAnError() = assertTrue(parse("<rss><channel><title>t</title></channel></rss>").items.isEmpty())

    @Test(expected = Exception::class) fun notXmlIsAnErrorTheCallerCatches() { parse("<html><body>Bad <b>gateway</body>") }
}
