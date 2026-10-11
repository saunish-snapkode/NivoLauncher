package com.snapkode.nivolauncher.lasso

import kotlin.math.abs

/**
 * Turns a second finger's horizontal swipes into page turns while the first finger keeps holding the selection.
 *
 * Each swipe that travels [thresholdPx] sideways (and is clearly more sideways than vertical) yields one turn:
 * -1 for a swipe to the right (previous page), +1 for a swipe to the left (next page). After a turn the finger's
 * origin is re-armed where it is; with [oncePerTouch] (the default) a touch turns one page only.
 */
class PageTurn(
    private val thresholdPx: Float,
    private val dominance: Float = 1.5f,
    /** One turn per touch: lift the second finger and swipe again for the next page, so a flick can't overshoot. */
    private val oncePerTouch: Boolean = true,
) {
    private var spent = false
    private var id: Long = NONE
    private var originX = 0f
    private var originY = 0f

    /**
     * Feed the second finger's current position. Returns -1, 0 or +1.
     * [accept] says whether a turn in that direction is possible right now (the page may not exist yet for a frame);
     * when it isn't, the swipe keeps accumulating and is retried on the next sample instead of being dropped.
     */
    fun move(pointerId: Long, x: Float, y: Float, accept: (Int) -> Boolean = { true }): Int {
        if (pointerId != id) { id = pointerId; originX = x; originY = y; spent = false; return 0 }
        if (spent) return 0
        val dx = x - originX
        val dy = y - originY
        if (abs(dx) <= thresholdPx || abs(dx) <= abs(dy) * dominance) return 0
        val dir = if (dx < 0) 1 else -1
        if (!accept(dir)) return 0
        originX = x; originY = y
        if (oncePerTouch) spent = true
        return dir
    }

    /** The second finger lifted. */
    fun release() { id = NONE; spent = false }

    private companion object { const val NONE = Long.MIN_VALUE }
}
