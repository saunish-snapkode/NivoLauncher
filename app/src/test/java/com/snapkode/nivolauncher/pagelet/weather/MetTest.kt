package com.snapkode.nivolauncher.pagelet.weather

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
private fun utc(s: String): Long = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = UTC }.parse(s)!!.time

/** A Locationforecast 2.0 "compact" response, trimmed but shaped exactly like the real thing. */
private val SAMPLE = """
{"type":"Feature","geometry":{"type":"Point","coordinates":[72.88,19.08,14]},
 "properties":{"meta":{"updated_at":"2026-10-11T07:11:08Z","units":{"air_temperature":"celsius"}},
 "timeseries":[
  {"time":"2026-10-11T06:00:00Z","data":{"instant":{"details":{"air_temperature":26.1,"relative_humidity":74.0,"wind_speed":2.1,"cloud_area_fraction":40.0}},
    "next_1_hours":{"summary":{"symbol_code":"partlycloudy_day"},"details":{"precipitation_amount":0.0}}}},
  {"time":"2026-10-11T07:00:00Z","data":{"instant":{"details":{"air_temperature":28.4,"relative_humidity":70.0,"wind_speed":2.6,"cloud_area_fraction":55.0}},
    "next_1_hours":{"summary":{"symbol_code":"cloudy"},"details":{"precipitation_amount":0.0}},
    "next_6_hours":{"summary":{"symbol_code":"rain"},"details":{"air_temperature_max":30.0,"air_temperature_min":27.0}}}},
  {"time":"2026-10-11T08:00:00Z","data":{"instant":{"details":{"air_temperature":30.2,"relative_humidity":66.0,"wind_speed":3.0,"cloud_area_fraction":60.0}},
    "next_1_hours":{"summary":{"symbol_code":"lightrain"}}}},
  {"time":"2026-10-11T09:00:00Z","data":{"instant":{"details":{"air_temperature":31.0,"relative_humidity":62.0,"wind_speed":3.2,"cloud_area_fraction":65.0}},
    "next_1_hours":{"summary":{"symbol_code":"rain"}}}},
  {"time":"2026-10-11T10:00:00Z","data":{"instant":{"details":{"air_temperature":30.5,"relative_humidity":64.0,"wind_speed":3.0,"cloud_area_fraction":70.0}},
    "next_1_hours":{"summary":{"symbol_code":"cloudy"}}}},
  {"time":"2026-10-11T11:00:00Z","data":{"instant":{"details":{"air_temperature":29.0,"relative_humidity":68.0,"wind_speed":2.8,"cloud_area_fraction":50.0}},
    "next_1_hours":{"summary":{"symbol_code":"partlycloudy_day"}}}},
  {"time":"2026-10-11T12:00:00Z","data":{"instant":{"details":{"air_temperature":27.5,"relative_humidity":72.0,"wind_speed":2.4,"cloud_area_fraction":45.0}},
    "next_6_hours":{"summary":{"symbol_code":"clearsky_night"}}}},
  {"time":"2026-10-12T06:00:00Z","data":{"instant":{"details":{"air_temperature":40.0,"relative_humidity":50.0,"wind_speed":1.0,"cloud_area_fraction":0.0}},
    "next_1_hours":{"summary":{"symbol_code":"clearsky_day"}}}}
 ]}}
""".trimIndent()

class MetParserTest {
    private val now = utc("2026-10-11T07:30:00Z")

    @Test fun readsTheCurrentEntryAtOrBeforeNow() {
        val f = MetParser.parse(SAMPLE, now, UTC)!!
        assertEquals(28.4, f.tempC, 1e-9)                // the 07:00 entry, not 08:00
        assertEquals("cloudy", f.symbolCode)               // next_1_hours wins over next_6_hours
        assertEquals(70.0, f.humidityPct, 1e-9)
        assertEquals(2.6, f.windMs, 1e-9)
        assertEquals(55.0, f.cloudPct, 1e-9)
    }

    @Test fun updatedAtComesFromTheMetaBlock() = assertEquals(utc("2026-10-11T07:11:08Z"), MetParser.parse(SAMPLE, now, UTC)!!.updatedAtMs)

    @Test fun todaysHighAndLowAreTakenOverTodaysEntriesOnly() {
        val f = MetParser.parse(SAMPLE, now, UTC)!!
        assertEquals(26.1, f.todayMinC, 1e-9)
        assertEquals(31.0, f.todayMaxC, 1e-9)               // the 40 °C tomorrow is not today
    }

    @Test fun todayIsInTheUsersTimeZoneNotUtc() {
        // 23:30 UTC on the 11th is already the 12th in Tokyo, so "today" is tomorrow's single 40 °C entry.
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        val f = MetParser.parse(SAMPLE, utc("2026-10-11T23:30:00Z"), tokyo)!!
        assertEquals(40.0, f.todayMaxC, 1e-9)
    }

    @Test fun hourlyIsTheNextSixEntriesFromNow() {
        val h = MetParser.parse(SAMPLE, now, UTC)!!.hourly
        assertEquals(6, h.size)
        assertEquals(utc("2026-10-11T07:00:00Z"), h.first().epochMs)
        assertEquals(listOf("cloudy", "lightrain", "rain", "cloudy", "partlycloudy_day", "clearsky_night"), h.map { it.symbolCode })
    }

    @Test fun fallsBackThroughTheSymbolHorizons() {
        val only6 = """{"properties":{"timeseries":[{"time":"2026-10-11T07:00:00Z","data":{"instant":{"details":{"air_temperature":10.0}},
            "next_6_hours":{"summary":{"symbol_code":"fog"}}}}]}}"""
        assertEquals("fog", MetParser.parse(only6, now, UTC)!!.symbolCode)
        val none = """{"properties":{"timeseries":[{"time":"2026-10-11T07:00:00Z","data":{"instant":{"details":{"air_temperature":10.0}}}}]}}"""
        assertEquals("cloudy", MetParser.parse(none, now, UTC)!!.symbolCode)
    }

    @Test fun beforeTheFirstEntryUsesTheFirstEntry() {
        assertEquals(26.1, MetParser.parse(SAMPLE, utc("2026-10-11T01:00:00Z"), UTC)!!.tempC, 1e-9)
    }

    @Test fun missingHumidityAndWindGetNeutralDefaults() {
        val f = MetParser.parse("""{"properties":{"timeseries":[{"time":"2026-10-11T07:00:00Z","data":{"instant":{"details":{"air_temperature":15.0}}}}]}}""", now, UTC)!!
        assertEquals(50.0, f.humidityPct, 0.0); assertEquals(0.0, f.windMs, 0.0)
    }

    @Test fun garbageIsNullNotACrash() {
        assertNull(MetParser.parse("", now, UTC))
        assertNull(MetParser.parse("<html>Bad gateway</html>", now, UTC))
        assertNull(MetParser.parse("{}", now, UTC))
        assertNull(MetParser.parse("""{"properties":{"timeseries":[]}}""", now, UTC))
        assertNull(MetParser.parse("""{"properties":{"timeseries":[{"time":"2026-10-11T07:00:00Z","data":{"instant":{"details":{}}}}]}}""", now, UTC))
    }
}

class FeelsLikeTest {
    @Test fun mildWeatherFeelsLikeTheAir() = assertEquals(18.0, FeelsLike.celsius(18.0, 60.0, 3.0), 1e-9)

    @Test fun humidHeatFeelsHotter() {
        val f = FeelsLike.celsius(32.0, 70.0, 1.0)
        assertTrue("feels $f", f > 36.0 && f < 45.0)
    }

    @Test fun dryHeatNeverFeelsCoolerThanTheAir() = assertTrue(FeelsLike.celsius(35.0, 5.0, 1.0) >= 35.0)

    @Test fun windChillBitesInTheCold() {
        // -10 °C with a 10 m/s (36 km/h) wind is a textbook ≈ -20 °C.
        assertEquals(-20.0, FeelsLike.celsius(-10.0, 80.0, 10.0), 1.5)
    }

    @Test fun coldButCalmIsJustCold() = assertEquals(5.0, FeelsLike.celsius(5.0, 80.0, 0.5), 1e-9)

    @Test fun forecastExposesFeelsLike() {
        val f = MetParser.parse(SAMPLE, utc("2026-10-11T07:30:00Z"), UTC)!!
        assertTrue(f.feelsLikeC >= f.tempC)
    }
}

class SolarTimesTest {
    private fun near(expected: Int, actual: Int, tol: Double) =
        assertTrue("expected ~%02d:%02d but was %02d:%02d".format(expected / 60, expected % 60, actual / 60, actual % 60), kotlin.math.abs(expected - actual) <= tol)


    @Test fun londonMidsummer() {
        // 2024-06-21 in London: sunrise 04:43 BST, sunset 21:21 BST (NOAA).
        val t = SolarTimes.forDate(51.5074, -0.1278, 2024, 6, 21, TimeZone.getTimeZone("Europe/London"))
        near(4 * 60 + 43, t.sunriseMin!!, 3.0)
        near(21 * 60 + 21, t.sunsetMin!!, 3.0)
    }

    @Test fun mumbaiOctober() {
        // Mumbai, mid-October: sunrise ≈ 06:32, sunset ≈ 18:17 IST.
        val t = SolarTimes.forDate(19.076, 72.8777, 2026, 10, 11, TimeZone.getTimeZone("Asia/Kolkata"))
        near(6 * 60 + 32, t.sunriseMin!!, 6.0)
        near(18 * 60 + 17, t.sunsetMin!!, 6.0)
    }

    @Test fun newYorkWinter() {
        // 2024-12-21 in New York: sunrise 07:17, sunset 16:32 EST.
        val t = SolarTimes.forDate(40.7128, -74.006, 2024, 12, 21, TimeZone.getTimeZone("America/New_York"))
        near(7 * 60 + 17, t.sunriseMin!!, 3.0)
        near(16 * 60 + 32, t.sunsetMin!!, 3.0)
    }

    @Test fun sydneyDecember() {
        // 2024-12-21 in Sydney (southern hemisphere summer): sunrise 05:41, sunset 20:05 AEDT.
        val t = SolarTimes.forDate(-33.8688, 151.2093, 2024, 12, 21, TimeZone.getTimeZone("Australia/Sydney"))
        near(5 * 60 + 41, t.sunriseMin!!, 3.0)
        near(20 * 60 + 5, t.sunsetMin!!, 3.0)
    }

    @Test fun sunriseIsAlwaysBeforeSunsetAtNormalLatitudes() {
        for (lat in listOf(-45.0, -10.0, 0.0, 28.6, 51.5, 60.0)) for (month in 1..12) {
            val t = SolarTimes.forDate(lat, 10.0, 2026, month, 15, UTC)
            assertTrue("$lat/$month ${t.sunriseMin} ${t.sunsetMin}", t.sunriseMin!! < t.sunsetMin!!)
        }
    }

    @Test fun midnightSunAndPolarNightHaveNoRiseOrSet() {
        val summer = SolarTimes.forDate(78.2, 15.6, 2026, 6, 21, UTC)           // Svalbard
        assertNull(summer.sunriseMin); assertTrue(summer.polarDay)
        val winter = SolarTimes.forDate(78.2, 15.6, 2026, 12, 21, UTC)
        assertNull(winter.sunsetMin); assertTrue(winter.polarNight)
    }
}

class MetClientTest {
    private class Fake(val replies: ArrayDeque<HttpResponse>) : HttpFetcher {
        val calls = ArrayList<Pair<String, Map<String, String>>>()
        override fun get(url: String, headers: Map<String, String>): HttpResponse { calls += url to headers; return replies.removeFirst() }
    }

    private fun client(f: HttpFetcher, now: Long = 1_000_000L) = MetNorwayClient(f, "NivoLauncher/1.0 (+test@example.com)") { now }

    @Test fun urlRoundsToTwoDecimalsWithADotWhateverTheLocale() {
        val old = Locale.getDefault()
        Locale.setDefault(Locale("de", "DE"))
        try {
            assertEquals("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=19.08&lon=72.88", MetNorwayClient.url(19.07612345, 72.87771234))
            assertEquals("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=-33.87&lon=151.21", MetNorwayClient.url(-33.8688, 151.2093))
        } finally { Locale.setDefault(old) }
    }

    @Test fun userAgentIdentifiesTheAppAndItsContact() {
        assertEquals("NivoLauncher/1.0 (+me@example.com)", MetNorwayClient.userAgent("1.0", "me@example.com"))
        assertEquals("NivoLauncher/1.0 (Android)", MetNorwayClient.userAgent("1.0", ""))
    }

    @Test fun everyRequestCarriesTheUserAgentAndOnlyUsesHttps() {
        val f = Fake(ArrayDeque(listOf(HttpResponse(200, "{}"))))
        client(f).fetch(1.0, 2.0, null)
        assertEquals("NivoLauncher/1.0 (+test@example.com)", f.calls.single().second["User-Agent"])
        assertTrue(f.calls.single().first.startsWith("https://"))
    }

    @Test fun ifModifiedSinceIsTheExactPreviousLastModified() {
        val f = Fake(ArrayDeque(listOf(HttpResponse(304), HttpResponse(200, "x"))))
        val c = client(f)
        c.fetch(1.0, 2.0, "Sun, 11 Oct 2026 07:11:08 GMT")
        assertEquals("Sun, 11 Oct 2026 07:11:08 GMT", f.calls[0].second["If-Modified-Since"])
        c.fetch(1.0, 2.0, null)
        assertFalse(f.calls[1].second.containsKey("If-Modified-Since"))
    }

    @Test fun mapsEachStatus() {
        fun r(code: Int, body: String? = null, retry: Long? = null) =
            client(Fake(ArrayDeque(listOf(HttpResponse(code, body, expiresMs = 5L, lastModified = "lm", retryAfterSec = retry))))).fetch(1.0, 2.0, null)
        assertTrue(r(200, "body") is MetResult.Fresh)
        assertTrue(r(203, "body") is MetResult.Fresh)
        assertTrue(r(304) is MetResult.NotModified)
        assertTrue(r(200, "") is MetResult.Failed)
        val blocked = r(403) as MetResult.Failed
        assertTrue("a 403 backs off for about an hour", blocked.retryAtMs >= 1_000_000L + 59 * 60_000L)
        val limited = r(429, retry = 7200) as MetResult.Failed
        assertTrue("429 honours a long Retry-After", limited.retryAtMs >= 1_000_000L + 7_200_000L)
        assertTrue((r(503) as MetResult.Failed).retryAtMs > 1_000_000L)
        assertTrue(r(404) is MetResult.Failed)
    }

    @Test fun aNetworkExceptionIsAFailureNotACrash() {
        val boom = HttpFetcher { _, _ -> throw java.io.IOException("offline") }
        val r = client(boom).fetch(1.0, 2.0, null)
        assertTrue(r is MetResult.Failed)
    }

    @Test fun freshCarriesTheHeaders() {
        val f = Fake(ArrayDeque(listOf(HttpResponse(200, "b", expiresMs = 9_000L, lastModified = "LM"))))
        val r = client(f).fetch(1.0, 2.0, null) as MetResult.Fresh
        assertEquals(9_000L, r.expiresMs); assertEquals("LM", r.lastModified); assertEquals("b", r.body)
    }

    @Test fun parsesHttpDates() {
        assertEquals(utc("2026-10-11T07:11:08Z"), parseHttpDate("Sun, 11 Oct 2026 07:11:08 GMT"))
        assertNull(parseHttpDate("tomorrow-ish")); assertNull(parseHttpDate(null)); assertNull(parseHttpDate(""))
    }
}

class SkyWeatherRepositoryTest {
    private class MemStore : WeatherStore {
        var entry: WeatherStore.Entry? = null
        override fun load() = entry
        override fun save(e: WeatherStore.Entry) { entry = e }
        override fun clear() { entry = null }
        override var notBeforeMs = 0L
        override var jitterMs = 0L
    }

    private class Scripted(val replies: ArrayDeque<HttpResponse>) : HttpFetcher {
        var count = 0; val last = ArrayList<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>): HttpResponse { count++; last += headers; return replies.removeFirst() }
    }

    private val mumbai = WeatherLocation("Mumbai", 19.076, 72.878)
    private val pune = WeatherLocation("Pune", 18.52, 73.86)
    private var now = utc("2026-10-11T07:30:00Z")
    private fun repo(fetcher: HttpFetcher, store: WeatherStore = MemStore()) =
        SkyWeatherRepository(MetNorwayClient(fetcher, "UA") { now }, store, { now }, Random(1), { UTC })

    private fun ok(expires: Long = now + 30 * 60_000L, lm: String? = "LM1") = HttpResponse(200, SAMPLE, expires, lm)

    @Test fun noPlaceMeansNeedsLocationAndNoRequest() = runBlocking {
        val f = Scripted(ArrayDeque())
        val r = repo(f); r.refreshIfDue(null)
        assertEquals(SkyWeatherState.NeedsLocation, r.state.value); assertEquals(0, f.count)
    }

    @Test fun firstRefreshFetchesAndShowsReady() = runBlocking {
        val f = Scripted(ArrayDeque(listOf(ok())))
        val r = repo(f); r.refreshIfDue(mumbai)
        val s = r.state.value as SkyWeatherState.Ready
        assertEquals("Mumbai", s.place); assertEquals(28.4, s.forecast.tempC, 1e-9); assertFalse(s.stale); assertEquals(1, f.count)
    }

    @Test fun nothingIsRequestedBeforeExpiresPlusJitter() = runBlocking {
        val f = Scripted(ArrayDeque(listOf(ok(expires = now + 30 * 60_000L))))
        val store = MemStore(); val r = repo(f, store)
        r.refreshIfDue(mumbai)
        now += 29 * 60_000L                       // still inside the forecast's lifetime
        r.refreshIfDue(mumbai); r.refreshIfDue(mumbai)
        assertEquals("no second request before Expires", 1, f.count)
        assertTrue(store.jitterMs in 0 until SkyWeatherRepository.JITTER_MAX_MS)
    }

    @Test fun afterExpiryItAsksAgainWithIfModifiedSince() = runBlocking {
        val f = Scripted(ArrayDeque(listOf(ok(lm = "LM1"), HttpResponse(304, expiresMs = now + 90 * 60_000L))))
        val store = MemStore(); val r = repo(f, store)
        r.refreshIfDue(mumbai)
        now += 40 * 60_000L
        r.refreshIfDue(mumbai)
        assertEquals(2, f.count)
        assertEquals("LM1", f.last[1]["If-Modified-Since"])
        // A 304 keeps the cached forecast; 40 minutes on, "now" has simply moved to the 08:00 entry.
        assertEquals(30.2, (r.state.value as SkyWeatherState.Ready).forecast.tempC, 1e-9)
        assertTrue("and pushes Expires out", store.entry!!.expiresMs > now)
    }

    @Test fun aDifferentPlaceIsFetchedImmediatelyEvenBeforeExpiry() = runBlocking {
        val f = Scripted(ArrayDeque(listOf(ok(), ok(lm = "LM2"))))
        val r = repo(f)
        r.refreshIfDue(mumbai); r.refreshIfDue(pune)
        assertEquals(2, f.count)
        assertEquals("Pune", (r.state.value as SkyWeatherState.Ready).place)
    }

    @Test fun aFailureKeepsTheLastGoodForecastAndBacksOff() = runBlocking {
        val f = Scripted(ArrayDeque(listOf(ok(), HttpResponse(503))))
        val store = MemStore(); val r = repo(f, store)
        r.refreshIfDue(mumbai)
        now += 40 * 60_000L
        r.refreshIfDue(mumbai)
        val s = r.state.value as SkyWeatherState.Ready
        assertTrue("still shows the old forecast, marked stale", s.stale); assertEquals(28.4, s.forecast.tempC, 1e-9)
        assertTrue(store.notBeforeMs > now)
        now += 60_000L
        r.refreshIfDue(mumbai)
        assertEquals("no hammering during the back-off", 2, f.count)
    }

    @Test fun aFailureWithNothingCachedIsUnavailableNotBlank() = runBlocking {
        val r = repo(Scripted(ArrayDeque(listOf(HttpResponse(403)))))
        r.refreshIfDue(mumbai)
        assertTrue(r.state.value is SkyWeatherState.Unavailable)
    }

    @Test fun aRestartShowsTheCacheBeforeAnyNetwork() {
        val store = MemStore()
        store.save(WeatherStore.Entry("19.08,72.88", SAMPLE, now + 60_000L, "LM", now, "Mumbai"))
        val r = repo(Scripted(ArrayDeque()), store)
        r.showCached(mumbai)
        val s = r.state.value as SkyWeatherState.Ready
        assertEquals(28.4, s.forecast.tempC, 1e-9); assertFalse(s.stale)
    }

    @Test fun anExpiredCacheIsShownAsStaleUntilRefreshed() {
        val store = MemStore()
        store.save(WeatherStore.Entry("19.08,72.88", SAMPLE, now - 3_600_000L, "LM", now - 7_200_000L, "Mumbai"))
        val r = repo(Scripted(ArrayDeque()), store)
        r.showCached(mumbai)
        assertTrue((r.state.value as SkyWeatherState.Ready).stale)
    }

    @Test fun aCorruptCacheDoesNotCrash() {
        val store = MemStore(); store.save(WeatherStore.Entry("19.08,72.88", "garbage", 0, null, 0, "Mumbai"))
        val r = repo(Scripted(ArrayDeque()), store); r.showCached(mumbai)
        assertTrue(r.state.value is SkyWeatherState.Unavailable)
    }

    @Test fun isDueRespectsBackoffAndPlace() {
        val store = MemStore(); val r = repo(Scripted(ArrayDeque()), store)
        assertTrue(r.isDue(mumbai))
        store.save(WeatherStore.Entry("19.08,72.88", SAMPLE, now + 60_000L, null, now, "Mumbai"))
        assertFalse(r.isDue(mumbai)); assertTrue(r.isDue(pune))
        store.notBeforeMs = now + 1_000L
        assertFalse(r.isDue(pune))
    }
}
