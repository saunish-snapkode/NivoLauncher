package com.snapkode.nivolauncher.pagelet.weather

/** Where the last forecast and the "don't ask before" bookkeeping live. A real one in SharedPreferences; a fake in tests. */
interface WeatherStore {
    class Entry(
        /** `lat,lon` rounded to two decimals: a different key means a different place. */
        val key: String,
        val body: String,
        val expiresMs: Long,
        val lastModified: String?,
        val fetchedAtMs: Long,
        val place: String,
    )

    fun load(): Entry?
    fun save(e: Entry)
    fun clear()

    /** Earliest time any request may be made, after a failure. */
    var notBeforeMs: Long

    /** Random extra delay chosen at fetch time so installs don't all refresh the instant a forecast expires. */
    var jitterMs: Long
}
