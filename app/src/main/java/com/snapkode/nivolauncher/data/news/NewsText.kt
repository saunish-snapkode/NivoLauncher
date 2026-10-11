package com.snapkode.nivolauncher.data.news

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Small, pure helpers for the messy text feeds contain. */
object NewsText {
    private val tag = Regex("<[^>]*>")
    private val spaces = Regex("\\s+")
    private val numeric = Regex("&#(x?[0-9a-fA-F]+);")
    private val img = Regex("<img\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    /** Plain text from a title or summary: tags dropped, entities decoded (also when a feed escaped them twice), spaces tidied. */
    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = decode(raw)
        if (s.contains('<')) s = tag.replace(s, " ")
        s = decode(s)                           // a feed that escaped its HTML once more
        return spaces.replace(s, " ").trim()
    }

    fun decode(s: String): String {
        if (!s.contains('&')) return s
        var out = numeric.replace(s) { m ->
            val v = m.groupValues[1]
            val cp = runCatching { if (v.startsWith("x") || v.startsWith("X")) v.substring(1).toInt(16) else v.toInt() }.getOrNull()
            if (cp != null && cp in 1..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        out = out.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&ldquo;", "“").replace("&rdquo;", "”").replace("&lsquo;", "‘").replace("&rsquo;", "’").replace("&hellip;", "…")
            .replace("&ndash;", "–").replace("&mdash;", "—")
            .replace("&amp;", "&")
        return out
    }

    /** The first `<img src>` in a chunk of HTML, if any. */
    fun firstImage(html: String?): String? = html?.let { img.find(decodeTags(it))?.groupValues?.get(1) }

    private fun decodeTags(s: String) = if (s.contains("&lt;img", ignoreCase = true)) s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"") else s

    /**
     * An https address for a picture, or null. Protocol-relative addresses get https; plain http is upgraded (the app does not
     * allow cleartext traffic); relative paths and anything that isn't a web address are dropped.
     */
    fun imageUrl(raw: String?): String? {
        val u = raw?.trim()?.replace("&amp;", "&")?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            u.startsWith("https://") -> u
            u.startsWith("http://") -> "https://" + u.removePrefix("http://")
            u.startsWith("//") -> "https:$u"
            else -> null
        }
    }
}

/** Publication dates as feeds write them: RFC 822 (RSS) and ISO 8601 (Atom, Dublin Core). Returns 0 when unreadable. */
object NewsDate {
    private val formats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm zzz", "dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd",
    )

    fun parse(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0L
        // ISO fractional seconds of any length: keep three digits so one pattern covers them.
        val norm = s.replace(Regex("(\\d{2}:\\d{2}:\\d{2})\\.(\\d{3})\\d+"), "$1.$2")
        for (f in formats) {
            val d = runCatching {
                SimpleDateFormat(f, Locale.US).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }.parse(norm)
            }.getOrNull()
            if (d != null) return d.time
        }
        return 0L
    }
}

/** Turning what a user typed into a feed address we're willing to fetch. */
object NewsFeedUrl {
    /** A canonical https address, or null if it can't be one. */
    fun normalise(input: String): String? {
        var s = input.trim().trim('<', '>', '"', '\'')
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        if (s.startsWith("feed://")) s = "https://" + s.removePrefix("feed://")
        if (!s.contains("://")) s = "https://$s"
        val scheme = s.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (scheme == "http") s = "https://" + s.removePrefix(s.substring(0, 7))
        val host = s.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.') || host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return null
        return s
    }

    /** A short readable name for a feed that has none: its site. */
    fun nameFrom(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore('/').removePrefix("www.").removePrefix("feeds.")
}
