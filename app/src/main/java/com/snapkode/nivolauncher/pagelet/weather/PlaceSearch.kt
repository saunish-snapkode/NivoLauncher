package com.snapkode.nivolauncher.pagelet.weather

import android.content.Context
import android.location.Geocoder
import com.snapkode.nivolauncher.data.Place
import java.util.Locale

/** City search through Android's own Geocoder. Empty (not an error) when the phone has none or it can't answer. Blocking. */
object PlaceSearch {
    @Suppress("DEPRECATION")
    fun onDevice(context: Context, query: String, max: Int = 5): List<Place> = runCatching {
        if (query.isBlank() || !Geocoder.isPresent()) return@runCatching emptyList<Place>()
        Geocoder(context, Locale.getDefault()).getFromLocationName(query.trim(), max).orEmpty().mapNotNull { a ->
            val name = a.locality ?: a.featureName ?: a.subAdminArea ?: return@mapNotNull null
            val detail = listOfNotNull(a.adminArea, a.countryName).filter { it != name }.joinToString(", ")
            Place(name, detail, a.latitude, a.longitude)
        }.distinctBy { "${it.name}|${it.detail}" }
    }.getOrDefault(emptyList())
}
