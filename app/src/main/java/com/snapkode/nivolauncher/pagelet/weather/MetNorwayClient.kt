package com.snapkode.nivolauncher.pagelet.weather

import java.util.Locale

/** The outcome of one request to MET Norway. */
sealed interface MetResult {
    /** A new forecast. [expiresMs] is when it may next be asked for. */
    class Fresh(val body: String, val expiresMs: Long?, val lastModified: String?) : MetResult

    /** Nothing changed since `If-Modified-Since`; the cached body is still current until [expiresMs]. */
    class NotModified(val expiresMs: Long?) : MetResult

    /** The request failed; don't try again before [retryAtMs]. */
    class Failed(val reason: String, val retryAtMs: Long) : MetResult
}

/**
 * Locationforecast 2.0 client, following MET Norway's terms of service that can be followed from a phone:
 *  - an identifying User-Agent (name, version, real contact) - never a browser or library default;
 *  - coordinates rounded to two decimals (about 1 km), which is plenty for weather and gives better caching;
 *  - `If-Modified-Since` with the exact previous `Last-Modified`, and `Expires` honoured by the caller;
 *  - HTTPS, gzip (done by the connection), a 429/403 back-off;
 *  - nothing is requested unless the caller asks, and the caller only asks while the launcher is in use.
 */
class MetNorwayClient(
    private val fetcher: HttpFetcher,
    private val userAgent: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun fetch(lat: Double, lon: Double, lastModified: String?): MetResult {
        val headers = HashMap<String, String>()
        headers["User-Agent"] = userAgent
        headers["Accept"] = "application/json"
        if (lastModified != null) headers["If-Modified-Since"] = lastModified
        val res = try {
            fetcher.get(url(lat, lon), headers)
        } catch (e: Exception) {
            return MetResult.Failed("network: ${e.javaClass.simpleName}", clock() + SHORT_BACKOFF_MS)
        }
        val now = clock()
        return when (res.code) {
            200, 203 -> {
                val body = res.body
                if (body.isNullOrBlank()) MetResult.Failed("empty body", now + SHORT_BACKOFF_MS)
                else MetResult.Fresh(body, res.expiresMs, res.lastModified)
            }
            304 -> MetResult.NotModified(res.expiresMs)
            // Blocked (usually a missing/unidentifiable User-Agent) or rate limited: back off hard so we never hammer them.
            403 -> MetResult.Failed("blocked (HTTP 403): check the User-Agent contact", now + LONG_BACKOFF_MS)
            429 -> MetResult.Failed("rate limited (HTTP 429)", now + maxOf(LONG_BACKOFF_MS / 2, (res.retryAfterSec ?: 0L) * 1000L))
            in 500..599 -> MetResult.Failed("server error (HTTP ${res.code})", now + MEDIUM_BACKOFF_MS)
            else -> MetResult.Failed("unexpected HTTP ${res.code}", now + MEDIUM_BACKOFF_MS)
        }
    }

    companion object {
        private const val SHORT_BACKOFF_MS = 5 * 60_000L
        private const val MEDIUM_BACKOFF_MS = 15 * 60_000L
        private const val LONG_BACKOFF_MS = 60 * 60_000L
        const val BASE = "https://api.met.no/weatherapi/locationforecast/2.0/compact"

        /** Two decimals, always with a dot, whatever the phone's language. */
        fun round2(v: Double) = "%.2f".format(Locale.US, v)

        fun url(lat: Double, lon: Double) = "$BASE?lat=${round2(lat)}&lon=${round2(lon)}"

        /** "NivoLauncher/1.0 (+contact)" - the contact is required by MET and comes from local.properties at build time. */
        fun userAgent(version: String, contact: String) =
            if (contact.isBlank()) "NivoLauncher/$version (Android)" else "NivoLauncher/$version (+$contact)"
    }
}
