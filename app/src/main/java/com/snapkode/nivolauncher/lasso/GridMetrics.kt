package com.snapkode.nivolauncher.lasso

import kotlin.math.floor

/**
 * The workspace grid in pixels. Cell sizes use integer division on purpose: `WorkspacePage` places an icon at
 * `col * cellW` with `cellW = width / columns`, so this maps pixels to cells exactly as the screen is drawn.
 */
class GridMetrics(val width: Int, val height: Int, val columns: Int, val rows: Int) {
    val cellW: Int = if (columns > 0) width / columns else 0
    val cellH: Int = if (rows > 0) height / rows else 0

    /** Column containing x, or -1 / [columns] when outside the grid (so callers can tell "off the left" from "on the grid"). */
    fun colAt(x: Float): Int = if (cellW <= 0) 0 else floor(x / cellW).toInt()

    fun rowAt(y: Float): Int = if (cellH <= 0) 0 else floor(y / cellH).toInt()

    fun inBounds(col: Int, row: Int) = col in 0 until columns && row in 0 until rows

    fun cellBox(col: Int, row: Int, spanX: Int = 1, spanY: Int = 1) =
        Box((col * cellW).toFloat(), (row * cellH).toFloat(), ((col + spanX) * cellW).toFloat(), ((row + spanY) * cellH).toFloat())

    /** Same grid but for a differently sized workspace (a rotation or a changed padding). */
    fun resized(width: Int, height: Int) = GridMetrics(width, height, columns, rows)
}
