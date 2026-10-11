package com.snapkode.nivolauncher.pagelet.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.TimeZone
import kotlin.random.Random

/** A place to show weather for. */
data class WeatherLocation(val name: String, val lat: Double, val lon: Double)

/** What the Sky is told about the weather. Cached data is always preferred to nothing. */
sealed interface SkyWeatherState {
    /** No place chosen (and no permission to use the phone's position). */
    data object NeedsLocation : SkyWeatherState

    /** Nothing cached and the first request hasn't succeeded. */
    data class Unavailable(val reason: String) : SkyWeatherState

    /** [stale]: the last refresh failed or is overdue, but this is still the last good forecast. */
    data class Ready(val forecast: MetForecast, val place: String, val fetchedAtMs: Long, val stale: Boolean) : SkyWeatherState
}

/**
 * Decides when MET Norway may be asked, and what to show meanwhile. It never asks:
 *  - before the forecast's `Expires` (plus a random jitter), unless the place changed;
 *  - before the back-off after a failure has passed;
 *  - when nobody is looking: the caller only calls [refreshIfDue] while the Sky is on screen.
 */
class SkyWeatherRepository(
    private val client: MetNorwayClient,
    private val cache: WeatherStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val tz: () -> TimeZone = { TimeZone.getDefault() },
) {
    private val _state = MutableStateFlow<SkyWeatherState>(SkyWeatherState.NeedsLocation)
    val state: StateFlow<SkyWeatherState> = _state.asStateFlow()
    private val lock = Mutex()

    private fun keyOf(l: WeatherLocation) = "${MetNorwayClient.round2(l.lat)},${MetNorwayClient.round2(l.lon)}"

    /** Show what is cached right away (before any network), or [SkyWeatherState.NeedsLocation] without a place. */
    fun showCached(location: WeatherLocation?) {
        if (location == null) { _state.value = SkyWeatherState.NeedsLocation; return }
        val cached = cache.load()
        val now = clock()
        _state.value = if (cached != null && cached.key == keyOf(location)) {
            MetParser.parse(cached.body, now, tz())?.let {
                SkyWeatherState.Ready(it, location.name, cached.fetchedAtMs, stale = now > cached.expiresMs + cache.jitterMs)
            } ?: SkyWeatherState.Unavailable("unreadable cache")
        } else SkyWeatherState.Unavailable("not loaded yet")
    }

    /** True if a request is allowed right now. */
    fun isDue(location: WeatherLocation): Boolean {
        val now = clock()
        if (now < cache.notBeforeMs) return false
        val cached = cache.load() ?: return true
        if (cached.key != keyOf(location)) return true     // a different place is always fetched
        return now >= cached.expiresMs + cache.jitterMs
    }

    /** Fetches if due; always leaves [state] showing the best thing available. Network happens off the main thread. */
    suspend fun refreshIfDue(location: WeatherLocation?) = lock.withLock {
        if (location == null) { _state.value = SkyWeatherState.NeedsLocation; return@withLock }
        if (_state.value !is SkyWeatherState.Ready || (_state.value as SkyWeatherState.Ready).place != location.name) showCached(location)
        if (!isDue(location)) return@withLock

        val cached = cache.load()?.takeIf { it.key == keyOf(location) }
        val result = withContext(Dispatchers.IO) { client.fetch(location.lat, location.lon, cached?.lastModified) }
        val now = clock()
        when (result) {
            is MetResult.Fresh -> {
                val expires = result.expiresMs ?: (now + DEFAULT_TTL_MS)
                cache.jitterMs = random.nextLong(0, JITTER_MAX_MS)
                cache.save(WeatherStore.Entry(keyOf(location), result.body, expires, result.lastModified, now, location.name))
                cache.notBeforeMs = 0L
                showCached(location)
            }
            is MetResult.NotModified -> {
                if (cached != null) {
                    val expires = result.expiresMs ?: (now + DEFAULT_TTL_MS)
                    cache.jitterMs = random.nextLong(0, JITTER_MAX_MS)
                    cache.save(WeatherStore.Entry(cached.key, cached.body, expires, cached.lastModified, now, cached.place))
                }
                cache.notBeforeMs = 0L
                showCached(location)
            }
            is MetResult.Failed -> {
                cache.notBeforeMs = result.retryAtMs
                val cur = _state.value
                _state.value = when {
                    cur is SkyWeatherState.Ready -> cur.copy(stale = true)
                    else -> SkyWeatherState.Unavailable(result.reason)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 30 * 60_000L
        const val JITTER_MAX_MS = 2 * 60_000L
    }
}
