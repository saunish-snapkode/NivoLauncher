package com.snapkode.nivolauncher.pagelet

/**
 * Whether a Pagelet should be doing any work. Animation and data collection run only while its page is the current one,
 * the screen is on and the launcher is resumed; everything else is paused immediately.
 */
data class PageletVisibility(
    val onCurrentPage: Boolean,
    val screenOn: Boolean,
    val resumed: Boolean,
    val batterySaver: Boolean = false,
    val lowRam: Boolean = false,
) {
    /** Run animations and collect data. */
    val active: Boolean get() = onCurrentPage && screenOn && resumed

    /** Motion that is nice to have: halved or dropped on battery saver and on low-RAM devices. */
    val reducedLoad: Boolean get() = batterySaver || lowRam

    companion object {
        val Inactive = PageletVisibility(onCurrentPage = false, screenOn = true, resumed = true)
    }
}

/**
 * How far a page is from being the current one: 1 when it fills the screen, 0 when a full page away or more. Used for the
 * "fade the dock as this page scrolls in" behaviour and for waking a Pagelet's animation as it starts to appear.
 */
fun pageCloseness(pagePosition: Float, pageIndex: Int): Float =
    (1f - kotlin.math.abs(pagePosition - pageIndex)).coerceIn(0f, 1f)

/** The strongest closeness among the pages that want the dock hidden: 0 if none do. */
fun dockHideFraction(pagePosition: Float, hidingPages: Collection<Int>): Float =
    hidingPages.maxOfOrNull { pageCloseness(pagePosition, it) } ?: 0f
