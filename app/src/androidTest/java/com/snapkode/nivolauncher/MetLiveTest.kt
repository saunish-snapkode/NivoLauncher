package com.snapkode.nivolauncher

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.pagelet.weather.MetNorwayClient
import com.snapkode.nivolauncher.pagelet.weather.MetParser
import com.snapkode.nivolauncher.pagelet.weather.MetResult
import com.snapkode.nivolauncher.pagelet.weather.UrlConnectionFetcher
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** One real request to api.met.no through the real client and parser. Skipped (not failed) if the phone is offline. */
@RunWith(AndroidJUnit4::class)
class MetLiveTest {
    @Test fun realForecastForMumbaiParses() {
        val client = MetNorwayClient(UrlConnectionFetcher(), MetNorwayClient.userAgent("test", BuildConfig.MET_CONTACT))
        val r = client.fetch(19.076, 72.878, null)
        Log.d("METLIVE", "UA contact configured: ${BuildConfig.MET_CONTACT.isNotBlank()}; result=${r.javaClass.simpleName}" +
            (if (r is MetResult.Failed) " reason=${r.reason}" else ""))
        assumeTrue("offline or blocked: $r", r is MetResult.Fresh || r is MetResult.Failed && !r.reason.startsWith("network"))
        if (r is MetResult.Failed) org.junit.Assert.fail("MET refused the request: ${r.reason}")
        r as MetResult.Fresh
        Log.d("METLIVE", "expires=${r.expiresMs} lastModified=${r.lastModified} bytes=${r.body.length}")
        val f = MetParser.parse(r.body, System.currentTimeMillis())
        assertNotNull("the real response should parse", f)
        f!!
        Log.d("METLIVE", "temp=${f.tempC} feels=${f.feelsLikeC} symbol=${f.symbolCode} hi=${f.todayMaxC} lo=${f.todayMinC} hourly=${f.hourly.size}")
        assertTrue(f.tempC in -60.0..60.0)
        assertTrue(f.hourly.isNotEmpty())
        // A second call with the validator must be answered 304 or a fresh copy, never an error.
        val again = client.fetch(19.076, 72.878, r.lastModified)
        Log.d("METLIVE", "second call: ${again.javaClass.simpleName}")
        assertTrue(again !is MetResult.Failed)
    }
}
