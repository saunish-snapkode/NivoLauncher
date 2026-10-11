package com.snapkode.nivolauncher.lasso

import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the radial menu goes: a half ring of buttons whose centre ("pivot") sits just beside the selection, on
 * whichever side has room. [above] means the ring opens upward from the selection's top edge.
 */
class RadialPlacement(val pivotX: Float, val pivotY: Float, val above: Boolean)

object RadialLayout {
    /** Degrees the buttons fan across. Wide enough that neighbouring buttons never touch at the standard radius. */
    const val SPAN_DEGREES = 150f

    /**
     * Picks a spot for the ring that never covers the [selection] and stays clear of the screen edges, or returns
     * null when neither side has room (the caller then falls back to the toolbar).
     *
     * @param topInset space reserved at the top (status bar)
     * @param bottomInset space reserved at the bottom (toolbar, navigation bar)
     * @param extent how far the ring reaches from its pivot, including the button radius and its label
     * @param halfWidth how far the ring reaches sideways from its pivot
     */
    fun place(
        selection: Box,
        screenWidth: Float,
        screenHeight: Float,
        topInset: Float,
        bottomInset: Float,
        extent: Float,
        halfWidth: Float,
        gap: Float,
        margin: Float,
    ): RadialPlacement? {
        if (screenWidth < 2 * (halfWidth + margin)) return null
        val px = selection.centerX.coerceIn(margin + halfWidth, screenWidth - margin - halfWidth)
        val roomAbove = selection.top - gap - (topInset + margin)
        val roomBelow = (screenHeight - bottomInset - margin) - (selection.bottom + gap)
        return when {
            roomAbove >= extent -> RadialPlacement(px, selection.top - gap, above = true)
            roomBelow >= extent -> RadialPlacement(px, selection.bottom + gap, above = false)
            else -> null
        }
    }

    /** Centres of [count] buttons spread evenly over the half ring, in left-to-right order. */
    fun buttonCenters(p: RadialPlacement, count: Int, radius: Float): List<Pair<Float, Float>> {
        if (count <= 0) return emptyList()
        val centre = if (p.above) -90f else 90f           // straight up or straight down, in screen coordinates
        val start = centre - SPAN_DEGREES / 2f
        val step = if (count == 1) 0f else SPAN_DEGREES / (count - 1)
        return List(count) { i ->
            val a = Math.toRadians((start + step * i).toDouble())
            p.pivotX + radius * cos(a).toFloat() to p.pivotY + radius * sin(a).toFloat()
        }
    }

    /** Distance between neighbouring button centres at [radius]; the ring is only usable if this exceeds a button's size. */
    fun spacing(radius: Float, count: Int): Float {
        if (count < 2) return Float.MAX_VALUE
        val step = Math.toRadians((SPAN_DEGREES / (count - 1)).toDouble())
        return (2.0 * radius * sin(step / 2.0)).toFloat()
    }
}
