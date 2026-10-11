package com.snapkode.nivolauncher.data

import android.content.Intent
import android.provider.Settings
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Matching and lookups used by the drawer search. */
object SearchTools {
    /**
     * Scores how well [label] matches [query]: 3 = prefix, 2 = contains, 1 = letters appear in order
     * (fuzzy), 0 = no match.
     */
    fun score(label: String, query: String, fuzzy: Boolean): Int {
        val l = label.lowercase()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 3
        if (l.startsWith(q) || l.split(' ', '-', '_').any { it.startsWith(q) }) return 3
        if (l.contains(q)) return 2
        if (!fuzzy || q.length < 2) return 0
        var at = 0
        for (c in q) {
            at = l.indexOf(c, at)
            if (at < 0) return 0
            at++
        }
        return 1
    }

    data class SettingsEntry(val title: String, val keywords: String, val action: String)

    val settingsEntries = listOf(
        SettingsEntry("Wi-Fi", "wifi wireless internet network", Settings.ACTION_WIFI_SETTINGS),
        SettingsEntry("Bluetooth", "bluetooth pair devices", Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingsEntry("Display", "display brightness screen dark theme", Settings.ACTION_DISPLAY_SETTINGS),
        SettingsEntry("Sound", "sound volume ringtone vibration", Settings.ACTION_SOUND_SETTINGS),
        SettingsEntry("Battery", "battery power saver", Intent.ACTION_POWER_USAGE_SUMMARY),
        SettingsEntry("Storage", "storage space memory", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingsEntry("Apps", "apps applications permissions", Settings.ACTION_APPLICATION_SETTINGS),
        SettingsEntry("Location", "location gps", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingsEntry("Security", "security lock screen fingerprint", Settings.ACTION_SECURITY_SETTINGS),
        SettingsEntry("Accessibility", "accessibility talkback", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingsEntry("Date and time", "date time clock timezone", Settings.ACTION_DATE_SETTINGS),
        SettingsEntry("Language", "language locale keyboard", Settings.ACTION_LOCALE_SETTINGS),
        SettingsEntry("Mobile network", "mobile data sim cellular network", Settings.ACTION_DATA_ROAMING_SETTINGS),
        SettingsEntry("Airplane mode", "airplane flight", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingsEntry("Notifications", "notifications alerts", Settings.ACTION_APP_NOTIFICATION_SETTINGS),
        SettingsEntry("Default apps", "default apps home browser", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        SettingsEntry("Wallpaper", "wallpaper background", Intent.ACTION_SET_WALLPAPER),
        SettingsEntry("Developer options", "developer options debugging usb", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    )

    fun matchingSettings(query: String): List<SettingsEntry> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return settingsEntries.filter { it.title.lowercase().contains(q) || it.keywords.contains(q) }.take(3)
    }

    /** Type-ahead suggestions from a provider (OpenSearch JSON). Blocking: call off the main thread. */
    fun webSuggestions(provider: SearchProvider, query: String): List<String> {
        val base = when (provider) {
            SearchProvider.Google -> "https://suggestqueries.google.com/complete/search?client=firefox&q="
            SearchProvider.DuckDuckGo -> "https://duckduckgo.com/ac/?type=list&q="
            SearchProvider.Brave -> "https://search.brave.com/api/suggest?q="
            SearchProvider.Bing -> "https://api.bing.com/osjson.aspx?query="
            SearchProvider.Custom -> return emptyList()
        }
        return runCatching {
            val connection = URL(base + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.setRequestProperty("User-Agent", "NivoLauncher")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val list = JSONArray(body).getJSONArray(1)
            List(minOf(list.length(), 4)) { list.getString(it) }
        }.getOrDefault(emptyList())
    }
}
