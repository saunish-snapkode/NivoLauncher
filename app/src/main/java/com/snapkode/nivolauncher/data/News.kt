package com.snapkode.nivolauncher.data

import android.util.Xml
import com.snapkode.nivolauncher.data.news.NewsFeedRef
import com.snapkode.nivolauncher.data.news.NewsParser
import com.snapkode.nivolauncher.data.news.ParsedFeed
import java.net.HttpURLConnection
import java.net.URL

/** What the News page can be filtered by. Which sources cover which topic is in [com.snapkode.nivolauncher.data.news.NewsCatalog]. */
enum class NewsTopic(val label: String) {
    Top("Top"),
    World("World"),
    Business("Business"),
    Technology("Tech"),
    Science("Science"),
    Sports("Sports"),
    Entertainment("Culture"),
    Health("Health"),
}

data class NewsItem(val title: String, val source: String, val link: String, val publishedAt: Long, val imageUrl: String? = null)

/**
 * Shown by the News page: the current list, whether a refresh is running, and how many of the chosen sources couldn't be
 * reached ([failedSources], with their names, so the page can say which).
 */
data class NewsState(
    val items: List<NewsItem> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val failedSources: List<String> = emptyList(),
)

object NewsFormat {
    /** "5m", "3h", "2d": short, calm timestamps for cards. */
    fun ago(now: Long, then: Long): String {
        val minutes = ((now - then) / 60_000L).coerceAtLeast(0)
        return when {
            then <= 0L -> ""
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            else -> "${minutes / (60 * 24)}d"
        }
    }

    fun isFresh(fetchedAt: Long, now: Long, maxAgeMinutes: Int = 10) = now - fetchedAt < maxAgeMinutes * 60_000L
}

/** Downloads and reads one public RSS/Atom feed. No account, no key; only the request itself leaves the phone. Blocking. */
object NewsSource {
    private const val USER_AGENT = "NivoLauncher/1.0 (Android; RSS reader)"
    private const val MAX_BYTES = 3 * 1024 * 1024
    private const val MAX_REDIRECTS = 4

    /** The parsed feed, or null if it can't be reached or isn't a feed. */
    fun fetch(ref: NewsFeedRef): ParsedFeed? = runCatching {
        var url = ref.url
        var hops = 0
        while (true) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 6000; c.readTimeout = 8000
                c.instanceFollowRedirects = false          // followed by hand: HttpURLConnection won't cross http ↔ https
                c.setRequestProperty("User-Agent", USER_AGENT)
                c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5")
                val code = c.responseCode
                if (code in 301..308 && hops < MAX_REDIRECTS) {
                    val next = c.getHeaderField("Location") ?: return@runCatching null
                    url = URL(URL(url), next).toString().let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
                    hops++
                    continue
                }
                if (code != 200) return@runCatching null
                val bytes = c.inputStream.use { it.readLimited(MAX_BYTES) }
                val parser = Xml.newPullParser()
                parser.setInput(java.io.ByteArrayInputStream(bytes), null)
                return@runCatching NewsParser.parse(parser, ref.sourceName).takeIf { it.items.isNotEmpty() }
            } finally {
                c.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE") null
    }.getOrNull()

    private fun java.io.InputStream.readLimited(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw java.io.IOException("feed too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
