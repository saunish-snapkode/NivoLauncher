package com.snapkode.nivolauncher.pagelet

import java.util.UUID

/**
 * The kinds of Pagelet. A Pagelet is a first-party widget that fills a whole home page; it is stored as an ordinary
 * [com.snapkode.nivolauncher.data.HomeItem] whose id is `nivo:pagelet:<TYPE>:<uuid8>`, so the workspace already knows how to
 * place, page, remove, back up and restore it.
 */
enum class PageletType(
    val title: String,
    val tagline: String,
    /** False while the Pagelet isn't built yet: the gallery lists it as "Coming soon" instead of adding a blank page. */
    val available: Boolean,
) {
    SKY("Today", "Time, weather and wellbeing under a living sky", available = true),
    AGENDA("Agenda", "Your day, hour by hour", available = true),
    NOW_PLAYING("Now Playing", "Whatever is playing, full page", available = true),
    HEADLINES("Headlines", "The news you follow", available = true),
    MOVE("Move", "Steps, sleep and heart rate", available = true),
    FOCUS("Focus", "Quiet time", available = false),
    ;

    /** True when the picture is drawn by the full-screen layers behind and above the pager; false when the page draws itself inside the pager. */
    val isLayered: Boolean get() = this == SKY
}

const val PAGELET_PREFIX = "nivo:pagelet:"

fun isPageletId(id: String) = id.startsWith(PAGELET_PREFIX)

fun newPageletId(type: PageletType) = "$PAGELET_PREFIX${type.name}:${UUID.randomUUID().toString().take(8)}"

/** The type encoded in a Pagelet id, or null if [id] isn't one (or names a type this version doesn't know). */
fun pageletTypeOf(id: String): PageletType? {
    if (!isPageletId(id)) return null
    val name = id.removePrefix(PAGELET_PREFIX).substringBefore(':')
    return PageletType.entries.firstOrNull { it.name == name }
}
