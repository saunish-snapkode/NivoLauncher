package com.snapkode.nivolauncher.lasso

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** An axis-aligned rectangle in workspace-local pixels. Immutable; created once per layout, never per touch event. */
class Box(
    @JvmField val left: Float,
    @JvmField val top: Float,
    @JvmField val right: Float,
    @JvmField val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height

    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    fun intersects(o: Box) = left < o.right && right > o.left && top < o.bottom && bottom > o.top

    fun translate(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)

    fun inflate(by: Float) = Box(left - by, top - by, right + by, bottom + by)

    override fun toString() = "Box($left, $top, $right, $bottom)"
}

/**
 * A freehand stroke. Points live in two growing float arrays, so drawing a long stroke allocates only when the
 * arrays double, never per touch event. Points closer than [minStep] to the previous one are dropped, which both
 * removes sensor jitter and keeps hit testing cheap.
 */
class LassoPath(initialCapacity: Int = 256, private val minStep: Float = 4f) {
    var xs = FloatArray(initialCapacity)
        private set
    var ys = FloatArray(initialCapacity)
        private set

    /** Number of points currently held. */
    var size = 0
        private set

    /** Length of the stroke itself, not counting the logical closing chord. */
    var length = 0f
        private set

    var minX = 0f
        private set
    var minY = 0f
        private set
    var maxX = 0f
        private set
    var maxY = 0f
        private set

    fun clear() {
        size = 0
        length = 0f
    }

    /** Appends a point unless it is within [minStep] of the last one. Returns whether it was kept. */
    fun add(x: Float, y: Float): Boolean {
        if (size > 0) {
            val dx = x - xs[size - 1]
            val dy = y - ys[size - 1]
            val d2 = dx * dx + dy * dy
            if (d2 < minStep * minStep) return false
            length += sqrt(d2)
        }
        if (size == xs.size) {
            xs = xs.copyOf(size * 2)
            ys = ys.copyOf(size * 2)
        }
        xs[size] = x
        ys[size] = y
        if (size == 0) {
            minX = x; maxX = x; minY = y; maxY = y
        } else {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        size++
        return true
    }

    /** Area of the polygon the stroke encloses once it is closed by a straight chord (shoelace formula). */
    fun area(): Float {
        if (size < 3) return 0f
        var sum = 0.0
        var j = size - 1
        for (i in 0 until size) {
            sum += (xs[j].toDouble() + xs[i]) * (ys[j].toDouble() - ys[i])
            j = i
        }
        return abs(sum / 2.0).toFloat()
    }

    /**
     * How loop-like the stroke is: enclosed area divided by length squared. A circle scores about 0.08, a straight
     * line or back-and-forth scribble scores about 0.
     */
    fun closedness(): Float = if (length < 1f) 0f else area() / (length * length)

    fun boundsIntersect(box: Box): Boolean =
        size > 0 && minX < box.right && maxX > box.left && minY < box.bottom && maxY > box.top

    fun copyPoints(): Pair<FloatArray, FloatArray> = xs.copyOf(size) to ys.copyOf(size)
}

object Polygon {
    /**
     * Even-odd point-in-polygon test over the first [n] points of [xs]/[ys]. The polygon is closed implicitly, so an
     * open stroke is treated as if its end were joined to its start.
     */
    fun contains(xs: FloatArray, ys: FloatArray, n: Int, px: Float, py: Float): Boolean {
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val yi = ys[i]
            val yj = ys[j]
            if ((yi > py) != (yj > py)) {
                val x = (xs[j] - xs[i]) * (py - yi) / (yj - yi) + xs[i]
                if (px < x) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Fraction of [box] inside the closed stroke, estimated on a [samples] x [samples] grid of sample points. */
    fun coverage(path: LassoPath, box: Box, samples: Int): Float {
        if (path.size < 3 || !path.boundsIntersect(box) || box.width <= 0f || box.height <= 0f) return 0f
        var hits = 0
        val sx = box.width / samples
        val sy = box.height / samples
        for (iy in 0 until samples) {
            val py = box.top + (iy + 0.5f) * sy
            for (ix in 0 until samples) {
                val px = box.left + (ix + 0.5f) * sx
                if (contains(path.xs, path.ys, path.size, px, py)) hits++
            }
        }
        return hits / (samples * samples).toFloat()
    }

    /** Whether the segment (x0,y0)-(x1,y1) touches [box] (Liang-Barsky clipping, allocation free). */
    fun segmentIntersectsBox(x0: Float, y0: Float, x1: Float, y1: Float, box: Box): Boolean {
        var t0 = 0f
        var t1 = 1f
        val dx = x1 - x0
        val dy = y1 - y0

        // left
        var p = -dx; var q = x0 - box.left
        if (p == 0f) { if (q < 0f) return false } else {
            val r = q / p
            if (p < 0f) { if (r > t1) return false; if (r > t0) t0 = r } else { if (r < t0) return false; if (r < t1) t1 = r }
        }
        // right
        p = dx; q = box.right - x0
        if (p == 0f) { if (q < 0f) return false } else {
            val r = q / p
            if (p < 0f) { if (r > t1) return false; if (r > t0) t0 = r } else { if (r < t0) return false; if (r < t1) t1 = r }
        }
        // top
        p = -dy; q = y0 - box.top
        if (p == 0f) { if (q < 0f) return false } else {
            val r = q / p
            if (p < 0f) { if (r > t1) return false; if (r > t0) t0 = r } else { if (r < t0) return false; if (r < t1) t1 = r }
        }
        // bottom
        p = dy; q = box.bottom - y0
        if (p == 0f) { if (q < 0f) return false } else {
            val r = q / p
            if (p < 0f) { if (r > t1) return false; if (r > t0) t0 = r } else { if (r < t0) return false; if (r < t1) t1 = r }
        }
        return t0 <= t1
    }

    /** Whether the open polyline passes within [radius] of [box]. A single point counts as a zero-length segment. */
    fun polylineTouches(path: LassoPath, box: Box, radius: Float): Boolean {
        if (path.size == 0) return false
        val grown = box.inflate(radius)
        if (path.size == 1) return grown.contains(path.xs[0], path.ys[0])
        if (!path.boundsIntersect(grown)) return false
        for (i in 1 until path.size) {
            if (segmentIntersectsBox(path.xs[i - 1], path.ys[i - 1], path.xs[i], path.ys[i], grown)) return true
        }
        return false
    }
}

/** Display smoothing. Hit testing always uses the raw points; only what is drawn is smoothed. */
object Smoothing {
    /**
     * One round of Chaikin corner cutting. Returns new arrays holding `2n - 2` points (the first and last points are
     * kept so the stroke still starts and ends where the finger did). Used when a stroke is finished, never per frame.
     */
    fun chaikin(xs: FloatArray, ys: FloatArray, n: Int): Pair<FloatArray, FloatArray> {
        if (n < 3) return xs.copyOf(n) to ys.copyOf(n)
        val outX = FloatArray(2 * n - 2)
        val outY = FloatArray(2 * n - 2)
        var k = 0
        outX[k] = xs[0]; outY[k] = ys[0]; k++
        for (i in 0 until n - 1) {
            val ax = xs[i]; val ay = ys[i]; val bx = xs[i + 1]; val by = ys[i + 1]
            if (i > 0) { outX[k] = 0.75f * ax + 0.25f * bx; outY[k] = 0.75f * ay + 0.25f * by; k++ }
            if (i < n - 2) { outX[k] = 0.25f * ax + 0.75f * bx; outY[k] = 0.25f * ay + 0.75f * by; k++ }
        }
        outX[k] = xs[n - 1]; outY[k] = ys[n - 1]; k++
        return outX.copyOf(k) to outY.copyOf(k)
    }

    fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
}
