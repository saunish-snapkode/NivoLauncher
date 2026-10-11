package com.snapkode.nivolauncher.pagelet.weather

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HttpResponse(
    val code: Int,
    val body: String? = null,
    /** From the `Expires` header: before this time the same request must not be repeated. */
    val expiresMs: Long? = null,
    /** The exact `Last-Modified` string, to send back as `If-Modified-Since`. */
    val lastModified: String? = null,
    val retryAfterSec: Long? = null,
)

/** The one place that touches the network, so everything above it can be tested with a fake. */
fun interface HttpFetcher {
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

class UrlConnectionFetcher(private val timeoutMs: Int = 8000) : HttpFetcher {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            // HttpURLConnection negotiates gzip by itself when Accept-Encoding is left alone, and unzips it.
            val code = c.responseCode
            val body = if (code == 200 || code == 203) c.inputStream.bufferedReader().use { it.readText() } else null
            return HttpResponse(
                code = code, body = body,
                expiresMs = parseHttpDate(c.getHeaderField("Expires")),
                lastModified = c.getHeaderField("Last-Modified"),
                retryAfterSec = c.getHeaderField("Retry-After")?.trim()?.toLongOrNull(),
            )
        } finally {
            c.disconnect()
        }
    }
}

/** RFC 1123 dates as used by HTTP, e.g. `Sun, 11 Oct 2026 12:30:00 GMT`. */
fun parseHttpDate(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    return runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(s.trim())?.time
    }.getOrNull()
}
