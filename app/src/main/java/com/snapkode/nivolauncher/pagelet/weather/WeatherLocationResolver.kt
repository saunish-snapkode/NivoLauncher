package com.snapkode.nivolauncher.pagelet.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.snapkode.nivolauncher.data.LauncherSettings
import java.util.Locale

/**
 * Where to show weather for: the city the user chose, or - if they allowed it - the phone's approximate position.
 * Coarse location only, last-known only: nothing here ever turns the GPS on.
 */
class WeatherLocationResolver(private val context: Context) {
    private var deviceName: Pair<String, String>? = null      // "lat,lon" → resolved name

    fun hasCoarsePermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A chosen city wins; otherwise the phone's position if permitted; otherwise null. Blocking (may geocode): call off the main thread. */
    fun resolve(s: LauncherSettings, allowGeocode: Boolean = true): WeatherLocation? {
        if (s.weatherPlace.isNotEmpty() && (s.weatherLat != 0.0 || s.weatherLon != 0.0)) {
            return WeatherLocation(s.weatherPlace, s.weatherLat, s.weatherLon)
        }
        return deviceLocation(allowGeocode)
    }

    @SuppressLint("MissingPermission")
    private fun deviceLocation(allowGeocode: Boolean): WeatherLocation? {
        if (!hasCoarsePermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        val key = MetNorwayClient.round2(loc.latitude) + "," + MetNorwayClient.round2(loc.longitude)
        val name = deviceName?.takeIf { it.first == key }?.second ?: (if (allowGeocode) reverseName(loc.latitude, loc.longitude).also { deviceName = key to it } else "Your location")
        return WeatherLocation(name, loc.latitude, loc.longitude)
    }

    /** A city name for the position if the system's Geocoder can give one; "Your location" if not (or if it is slow or absent). */
    @Suppress("DEPRECATION")
    private fun reverseName(lat: Double, lon: Double): String = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull() ?: "Your location"
}
