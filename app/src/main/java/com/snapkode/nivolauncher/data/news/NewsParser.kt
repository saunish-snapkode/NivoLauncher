package com.snapkode.nivolauncher.data.news

import com.snapkode.nivolauncher.data.NewsItem
import org.xmlpull.v1.XmlPullParser

/** A parsed feed: its own title (if it has one) and its stories, newest first as the feed listed them. */
class ParsedFeed(val title: String?, val items: List<NewsItem>)

/**
 * Reads RSS 2.0, RSS 1.0 (RDF) and Atom. Everything is matched on the element's local name, so it doesn't matter whether the
 * parser resolves namespaces. A story's picture comes from the first of: `media:content` / `media:thumbnail` (the widest),
 * an image `enclosure`, an `<image>` element, or the first `<img>` in the story's own HTML.
 */
object NewsParser {
    private fun local(name: String?) = name?.substringAfter(':')?.lowercase().orEmpty()

    fun parse(parser: XmlPullParser, sourceName: String, maxItems: Int = 60): ParsedFeed {
        val out = ArrayList<NewsItem>()
        var channelTitle: String? = null
        var inItem = false
        var title = ""; var link = ""; var guid = ""; var published = 0L; var updated = 0L
        var image: String? = null; var imageWidth = -1; var htmlImage: String? = null

        fun reset() { title = ""; link = ""; guid = ""; published = 0L; updated = 0L; image = null; imageWidth = -1; htmlImage = null }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = local(parser.name)
                if (!inItem) {
                    when (name) {
                        "item", "entry" -> { inItem = true; reset() }
                        "title" -> if (channelTitle == null) channelTitle = NewsText.clean(readText(parser)).ifBlank { null }
                    }
                } else when (name) {
                    "title" -> title = NewsText.clean(readText(parser))
                    "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) {
                            val rel = parser.getAttributeValue(null, "rel")
                            if ((rel == null || rel == "alternate") && link.isBlank()) link = href.trim()
                            skip(parser)
                        } else link = readText(parser).trim()
                    }
                    "guid", "id" -> guid = readText(parser).trim()
                    "pubdate", "published", "date", "issued" -> { val t = NewsDate.parse(readText(parser)); if (t > 0) published = t }
                    "updated", "modified" -> { val t = NewsDate.parse(readText(parser)); if (t > 0) updated = t }
                    "thumbnail", "content" -> {
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null) {
                            val medium = parser.getAttributeValue(null, "medium")
                            val type = parser.getAttributeValue(null, "type")
                            val isImage = (medium == null || medium == "image") && (type == null || type.startsWith("image"))
                            val w = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                            if (isImage && w >= imageWidth) { image = url; imageWidth = w }
                            skip(parser)
                        } else {
                            // Atom's <content> and RSS's content:encoded carry the story as HTML; the first picture in it will do.
                            val html = readText(parser)
                            if (htmlImage == null) htmlImage = NewsText.firstImage(html)
                        }
                    }
                    "enclosure" -> {
                        val url = parser.getAttributeValue(null, "url"); val type = parser.getAttributeValue(null, "type").orEmpty()
                        if (url != null && type.startsWith("image") && image == null) image = url
                        skip(parser)
                    }
                    "image" -> {
                        val url = parser.getAttributeValue(null, "url") ?: parser.getAttributeValue(null, "href")
                        if (url != null) { if (image == null) image = url; skip(parser) }
                    }
                    "description", "summary", "encoded" -> { val html = readText(parser); if (htmlImage == null) htmlImage = NewsText.firstImage(html) }
                }
            } else if (event == XmlPullParser.END_TAG && inItem && (local(parser.name) == "item" || local(parser.name) == "entry")) {
                inItem = false
                // RSS says <link>; some feeds only give a permalink <guid>.
                val url = link.ifBlank { guid.takeIf { it.startsWith("http") }.orEmpty() }
                if (title.isNotBlank() && url.isNotBlank()) {
                    val shown = NewsText.imageUrl(image) ?: NewsText.imageUrl(htmlImage)
                    out += NewsItem(title, sourceName, url, if (published > 0) published else updated, shown?.let(NewsImages::enlarge))
                }
                if (out.size >= maxItems) break
            }
            event = parser.next()
        }
        return ParsedFeed(channelTitle, out)
    }

    /** All the text inside the current element, ignoring any markup nested in it; leaves the parser on the element's end tag. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
                XmlPullParser.ENTITY_REF -> sb.append(parser.text.orEmpty())
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}

/** Bigger versions of the pictures that publishers serve at several sizes. */
object NewsImages {
    private val bbc = Regex("/(\\d{3})/")

    /** BBC thumbnails are 240 px wide; the same picture exists at 800. Others are left alone. */
    fun enlarge(url: String): String = if (url.contains("ichef.bbci.co.uk")) bbc.replace(url, "/800/") else url
}
