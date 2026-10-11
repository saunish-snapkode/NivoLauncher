package com.snapkode.nivolauncher.lasso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {
    private fun square(size: Float = 100f, step: Float = 5f): LassoPath {
        val p = LassoPath(minStep = 1f)
        var x = 0f
        while (x < size) { p.add(x, 0f); x += step }
        var y = 0f
        while (y < size) { p.add(size, y); y += step }
        x = size
        while (x > 0f) { p.add(x, size); x -= step }
        y = size
        while (y > 0f) { p.add(0f, y); y -= step }
        return p
    }

    private fun circle(cx: Float, cy: Float, r: Float, points: Int = 60, sweepDeg: Float = 360f): LassoPath {
        val p = LassoPath(minStep = 1f)
        for (i in 0..points) {
            val a = Math.toRadians((sweepDeg * i / points).toDouble())
            p.add(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        return p
    }

    @Test
    fun jitterBelowTheStepIsDropped() {
        val p = LassoPath(minStep = 4f)
        assertTrue(p.add(0f, 0f))
        assertFalse(p.add(1f, 1f))
        assertFalse(p.add(2f, 2f))
        assertTrue(p.add(10f, 0f))
        assertEquals(2, p.size)
        assertEquals(10f, p.length, 0.001f)
    }

    @Test
    fun bufferGrowsWithoutLosingPoints() {
        val p = LassoPath(initialCapacity = 4, minStep = 1f)
        for (i in 0 until 1000) p.add(i * 2f, 0f)
        assertEquals(1000, p.size)
        assertEquals(1998f, p.xs[999], 0.001f)
    }

    @Test
    fun clearReusesTheBuffer() {
        val p = LassoPath(minStep = 1f)
        for (i in 0 until 50) p.add(i * 3f, i * 3f)
        p.clear()
        assertEquals(0, p.size)
        assertEquals(0f, p.length, 0f)
        p.add(5f, 5f)
        assertEquals(1, p.size)
        assertEquals(5f, p.minX, 0f)
    }

    @Test
    fun shoelaceAreaOfSquare() {
        assertEquals(10_000f, square().area(), 100f)
    }

    @Test
    fun aCircleIsLoopLikeAndALineIsNot() {
        val loop = circle(50f, 50f, 40f).closedness()
        assertEquals(1f / (4f * PI.toFloat()), loop, 0.005f)
        val line = LassoPath(minStep = 1f).also { for (i in 0 until 50) it.add(i * 4f, 0f) }
        assertEquals(0f, line.closedness(), 0.0001f)
    }

    @Test
    fun anOpenCIsClosedByTheChord() {
        // Three quarters of a circle: the logical closing chord still encloses a region.
        val c = circle(50f, 50f, 40f, sweepDeg = 270f)
        assertTrue(c.closedness() > 0.03f)
        assertTrue(Polygon.contains(c.xs, c.ys, c.size, 50f, 50f))
    }

    @Test
    fun pointInConvexPolygon() {
        val s = square()
        assertTrue(Polygon.contains(s.xs, s.ys, s.size, 50f, 50f))
        assertFalse(Polygon.contains(s.xs, s.ys, s.size, 150f, 50f))
        assertFalse(Polygon.contains(s.xs, s.ys, s.size, -1f, 50f))
    }

    @Test
    fun pointInConcavePolygon() {
        // An L shape.
        val xs = floatArrayOf(0f, 100f, 100f, 40f, 40f, 0f)
        val ys = floatArrayOf(0f, 0f, 40f, 40f, 100f, 100f)
        assertTrue(Polygon.contains(xs, ys, 6, 20f, 80f))
        assertTrue(Polygon.contains(xs, ys, 6, 80f, 20f))
        assertFalse("inside the notch", Polygon.contains(xs, ys, 6, 80f, 80f))
    }

    @Test
    fun fewerThanThreePointsEncloseNothing() {
        val p = LassoPath(minStep = 1f)
        p.add(0f, 0f); p.add(50f, 50f)
        assertFalse(Polygon.contains(p.xs, p.ys, p.size, 25f, 25f))
        assertEquals(0f, Polygon.coverage(p, Box(0f, 0f, 50f, 50f), 6), 0f)
    }

    @Test
    fun coverageIsTheFractionInside() {
        val s = square()                                    // covers x,y in 0..100
        assertEquals(1f, Polygon.coverage(s, Box(10f, 10f, 40f, 40f), 6), 0.001f)
        assertEquals(0f, Polygon.coverage(s, Box(150f, 150f, 180f, 180f), 6), 0.001f)
        // A tile straddling the right edge, exactly half inside.
        assertEquals(0.5f, Polygon.coverage(s, Box(85f, 10f, 115f, 40f), 6), 0.001f)
    }

    @Test
    fun segmentVersusBox() {
        val box = Box(10f, 10f, 20f, 20f)
        assertTrue(Polygon.segmentIntersectsBox(0f, 15f, 30f, 15f, box))      // straight through
        assertTrue(Polygon.segmentIntersectsBox(12f, 12f, 14f, 14f, box))     // fully inside
        assertFalse(Polygon.segmentIntersectsBox(0f, 0f, 5f, 5f, box))        // misses
        assertFalse(Polygon.segmentIntersectsBox(0f, 25f, 30f, 25f, box))     // parallel and outside
        assertTrue(Polygon.segmentIntersectsBox(0f, 0f, 30f, 30f, box))       // diagonal
    }

    @Test
    fun polylineTouchRespectsTheRadius() {
        val p = LassoPath(minStep = 1f).also { it.add(0f, 0f); it.add(100f, 0f) }
        val box = Box(40f, 8f, 60f, 28f)
        assertFalse(Polygon.polylineTouches(p, box, 5f))
        assertTrue(Polygon.polylineTouches(p, box, 10f))
    }

    @Test
    fun smoothingKeepsTheEndpointsAndTheShape() {
        val c = circle(50f, 50f, 40f, points = 24)
        val (sx, sy) = Smoothing.chaikin(c.xs, c.ys, c.size)
        assertEquals(c.xs[0], sx.first(), 0f)
        assertEquals(c.ys[c.size - 1], sy.last(), 0f)
        assertEquals(2 * c.size - 2, sx.size)
        // Every smoothed point stays inside the original's bounding box.
        for (i in sx.indices) {
            assertTrue(sx[i] >= c.minX - 0.01f && sx[i] <= c.maxX + 0.01f)
            assertTrue(sy[i] >= c.minY - 0.01f && sy[i] <= c.maxY + 0.01f)
        }
        // And what is clearly inside or outside does not change.
        assertTrue(Polygon.contains(sx, sy, sx.size, 50f, 50f))
        assertFalse(Polygon.contains(sx, sy, sx.size, 120f, 50f))
    }

    @Test
    fun gridMapsPixelsToCellsLikeTheWorkspaceDoes() {
        val g = GridMetrics(width = 1000, height = 1500, columns = 4, rows = 5)
        assertEquals(250, g.cellW)
        assertEquals(300, g.cellH)
        assertEquals(0, g.colAt(0f)); assertEquals(0, g.colAt(249.9f)); assertEquals(1, g.colAt(250f))
        assertEquals(3, g.colAt(999f)); assertEquals(4, g.colAt(1000f))
        assertEquals(-1, g.colAt(-0.1f)); assertEquals(-1, g.rowAt(-5f))
        assertTrue(g.inBounds(3, 4)); assertFalse(g.inBounds(4, 0)); assertFalse(g.inBounds(0, -1))
        val b = g.cellBox(1, 2)
        assertEquals(250f, b.left, 0f); assertEquals(600f, b.top, 0f); assertEquals(500f, b.right, 0f); assertEquals(900f, b.bottom, 0f)
    }

    @Test
    fun gridUsesIntegerCellsSoItMatchesTheDrawnLayout() {
        // 1001 / 4 = 250 (not 250.25): an icon in column 3 is drawn at 750, so that is where it must be hit-tested.
        val g = GridMetrics(1001, 1000, 4, 4)
        assertEquals(250, g.cellW)
        assertEquals(750f, g.cellBox(3, 0).left, 0f)
    }
}
