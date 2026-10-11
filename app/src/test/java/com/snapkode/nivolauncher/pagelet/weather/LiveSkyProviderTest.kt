package com.snapkode.nivolauncher.pagelet.weather

import com.snapkode.nivolauncher.ui.pagelet.sky.WeatherStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

class LiveSkyProviderTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(s: String): Long = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = utc }.parse(s)!!.time
    private var now = at("2026-10-11T07:30:00Z")

    private val sample = """{"properties":{"meta":{"updated_at":"2026-10-11T07:11:08Z"},"timeseries":[
      {"time":"2026-10-11T07:00:00Z","data":{"instant":{"details":{"air_temperature":28.4,"relative_humidity":70.0,"wind_speed":2.6}},
        "next_1_hours":{"summary":{"symbol_code":"rain"}}}}]}}"""

    private class Mem : WeatherStore {
        var e: WeatherStore.Entry? = null
        override fun load() = e
        override fun save(e: WeatherStore.Entry) { this.e = e }
        override fun clear() { e = null }
        override var notBeforeMs = 0L
        override var jitterMs = 0L
    }

    private fun provider(loc: WeatherLocation?, fetcher: HttpFetcher, store: WeatherStore = Mem()): Pair<LiveSkyProvider, SkyWeatherRepository> {
        val repo = SkyWeatherRepository(MetNorwayClient(fetcher, "UA") { now }, store, { now }, Random(1), { utc })
        val clock = { Calendar.getInstance(utc).apply { timeInMillis = now } }
        return LiveSkyProvider(repo, location = { loc }, clock = clock) to repo
    }

    private val mumbai = WeatherLocation("Mumbai", 19.076, 72.878)

    @Test fun withoutAPlaceThereIsNoWeatherAndNoRequest() = runBlocking {
        var calls = 0
        val (p, repo) = provider(null, { _, _ -> calls++; HttpResponse(200, sample) })
        repo.refreshIfDue(null)
        val d = p.snapshot()
        assertNull(d.weather); assertEquals(WeatherStatus.NeedsLocation, d.weatherStatus); assertEquals(0, calls)
    }

    @Test fun aFreshForecastIsLiveAndNotLabelledSample() = runBlocking {
        val (p, repo) = provider(mumbai, { _, _ -> HttpResponse(200, sample, now + 1_800_000L, "LM") })
        repo.refreshIfDue(mumbai)
        val d = p.snapshot()
        assertEquals(WeatherStatus.Live, d.weatherStatus)
        assertEquals("Mumbai", d.weather!!.city); assertEquals("rain", d.weather!!.symbolCode); assertEquals(28.4, d.weather!!.tempC, 1e-9)
        assertTrue(!d.weatherIsSample)
        assertTrue("nothing is invented for sections that were not read", !d.wellbeingIsSample && d.wellbeing == null)
    }

    @Test fun sunTimesComeFromThePlaceNotFromAConstant() = runBlocking {
        val (p, _) = provider(mumbai, { _, _ -> HttpResponse(200, sample) })
        val d = p.snapshot()
        // Mumbai in October: sunrise ≈ 06:32 IST = 01:02 UTC; the test clock is UTC, so only check it isn't the fallback.
        assertTrue(d.sunriseMin != 6 * 60 + 30 || d.sunsetMin != 18 * 60 + 30)
    }

    @Test fun withoutAPlaceTheSkyStillHasOrdinaryHours() {
        val (p, _) = provider(null, { _, _ -> HttpResponse(200, sample) })
        val d = p.snapshot()
        assertEquals(6 * 60 + 30, d.sunriseMin); assertEquals(18 * 60 + 30, d.sunsetMin)
    }

    @Test fun anOverdueForecastIsMarkedStaleButStillShown() = runBlocking {
        val store = Mem()
        store.save(WeatherStore.Entry("19.08,72.88", sample, now - 3_600_000L, "LM", now - 7_200_000L, "Mumbai"))
        val (p, _) = provider(mumbai, { _, _ -> HttpResponse(503) }, store)
        val d = p.snapshot()
        assertEquals(WeatherStatus.Stale, d.weatherStatus); assertNotNull(d.weather)
    }

    @Test fun nothingCachedAndNoNetworkIsUnavailableNotBlank() = runBlocking {
        val (p, repo) = provider(mumbai, { _, _ -> HttpResponse(503) })
        repo.refreshIfDue(mumbai)
        val d = p.snapshot()
        assertNull(d.weather); assertEquals(WeatherStatus.Unavailable, d.weatherStatus)
        // And the sky still has a time of day.
        assertEquals((now - at("2026-10-11T00:00:00Z")).toInt() / 60_000, d.nowMin)
    }
}
