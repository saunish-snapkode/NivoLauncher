package com.snapkode.nivolauncher.ui.pagelet.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The illustrated horizon: layered hills, a city skyline with lit windows and pine trees, painted as vector silhouettes
 * that take their colours from the time of day and their mood from the weather. The shapes are generated from a fixed seed
 * (the same scene every time, on every phone), built once per page size, and only their colours and a few small lights
 * change from frame to frame.
 */

/** Seeded shapes, as plain numbers: no drawing here, so the tests can check them. */
internal object LandscapeGen {
    /** [points] heights in 0..1 along the width: a ridge made of three sine waves with seeded phases. */
    fun ridge(seed: Int, points: Int, wiggle: Float): FloatArray {
        val r = Random(seed)
        val p1 = r.nextFloat() * 2f * PI.toFloat()
        val p2 = r.nextFloat() * 2f * PI.toFloat()
        val p3 = r.nextFloat() * 2f * PI.toFloat()
        return FloatArray(points) { i ->
            val x = i / (points - 1f)
            val tau = 2f * PI.toFloat()
            (0.5f + 0.28f * sin(tau * x + p1) + 0.16f * sin(tau * 2.3f * x + p2) + 0.08f * sin(tau * 5.1f * wiggle * x + p3)).coerceIn(0f, 1f)
        }
    }

    class Building(val x01: Float, val widthDp: Float, val heightDp: Float, val cols: Int, val rows: Int, val windowSeed: Int)

    /** Buildings from [fromX01] to the right edge, taller towards the right, with small gaps. */
    fun skyline(seed: Int, screenWidthDp: Float, fromX01: Float): List<Building> {
        val r = Random(seed)
        val out = ArrayList<Building>()
        var x = fromX01 * screenWidthDp
        while (x < screenWidthDp) {
            val w = 16f + r.nextFloat() * 20f
            val lean = ((x / screenWidthDp - fromX01) / (1f - fromX01)).coerceIn(0f, 1f)        // 0 at the left of the city, 1 at the right
            val hgt = 22f + r.nextFloat() * 34f + lean * (18f + r.nextFloat() * 30f)
            val cols = (w / 7f).toInt().coerceIn(2, 5)
            val rows = (hgt / 9f).toInt().coerceIn(2, 12)
            out += Building(x / screenWidthDp, w, hgt, cols, rows, r.nextInt())
            x += w + 1.5f + r.nextFloat() * 4f
        }
        return out
    }

    class Tree(val x01: Float, val heightDp: Float, val tiers: Int)

    /** Pines standing on the front hill, bunched near the left and right edges and clear of the middle. */
    fun trees(seed: Int, count: Int): List<Tree> {
        val r = Random(seed)
        return List(count) { i ->
            val side = if (i % 2 == 0) 0f else 1f
            val edge = r.nextFloat() * 0.24f
            Tree(if (side == 0f) edge else 1f - edge, 26f + r.nextFloat() * 34f, 3 + r.nextInt(2))
        }.sortedBy { it.x01 }
    }

    /** Whether window [index] of a building is lit, given how lit the city is (0..1). Stable for a given seed. */
    fun lit(windowSeed: Int, index: Int, litFraction: Float): Boolean {
        if (litFraction <= 0f) return false
        return Random(windowSeed * 31 + index).nextFloat() < litFraction
    }

    /** A handful of windows flicker slowly; which ones is fixed. */
    fun twinkles(windowSeed: Int, index: Int): Boolean = Random(windowSeed * 17 + index + 99).nextFloat() < 0.12f
}

/** The colours and mood of the landscape for one look (phase × weather). Pure. */
internal class LandscapeColors(
    val far: Color, val mid: Color, val city: Color, val front: Color,
    /** The thin light on the tops of the ridges: sunlit at the ends of the day, moonlit at night. */
    val rim: Color,
    val windowLit: Color,
    /** Share of windows that are lit, 0..1. */
    val litFraction: Float,
    val birds: Boolean,
) {
    companion object {
        fun of(look: SkyLook): LandscapeColors {
            val ph = look.phase
            val phase = look.phaseKind
            var far = lerp(ph.sky2, ph.sky1, 0.5f)
            var mid = lerp(ph.sky1, ph.ground, 0.5f)
            var city = lerp(ph.sky1, ph.ground, 0.78f)
            val front = ph.ground
            when (look.layer) {
                WeatherLayer.Fog -> {
                    val haze = Color(0xFFC9D0DC)
                    far = lerp(far, haze, 0.55f); mid = lerp(mid, haze, 0.35f); city = lerp(city, haze, 0.18f)
                }
                WeatherLayer.Cloudy -> {
                    val grey = Color(0xFF8A93A3)
                    far = lerp(far, grey, 0.22f); mid = lerp(mid, grey, 0.14f)
                }
                WeatherLayer.Rain -> { far = lerp(far, Color.Black, 0.28f); mid = lerp(mid, Color.Black, 0.28f); city = lerp(city, Color.Black, 0.25f) }
                WeatherLayer.Storm -> { far = lerp(far, Color.Black, 0.45f); mid = lerp(mid, Color.Black, 0.42f); city = lerp(city, Color.Black, 0.35f) }
                WeatherLayer.Clear -> Unit
            }
            val sunlit = phase == TimePhase.Dawn || phase == TimePhase.Golden || phase == TimePhase.Day
            val rim = when {
                look.layer == WeatherLayer.Storm || look.layer == WeatherLayer.Rain -> Color.Transparent
                phase == TimePhase.Golden || phase == TimePhase.Dawn -> ph.sunC.copy(alpha = 0.55f)
                phase == TimePhase.Day -> ph.sunC.copy(alpha = 0.22f)
                phase == TimePhase.Dusk -> ph.glow.copy(alpha = 0.35f)
                else -> Color(0xFFBFD0FF).copy(alpha = 0.16f)
            }
            val gloomy = look.layer == WeatherLayer.Rain || look.layer == WeatherLayer.Storm || look.layer == WeatherLayer.Fog
            val lit = when (phase) {
                TimePhase.Night -> 0.58f
                TimePhase.Dusk -> 0.5f
                TimePhase.Dawn -> 0.3f
                TimePhase.Golden -> 0.12f
                TimePhase.Day -> 0f
            }.let { if (gloomy && phase != TimePhase.Night) it + 0.25f else it }.coerceIn(0f, 0.8f)
            return LandscapeColors(
                far, mid, city, front, rim, Color(0xFFFFD58F), lit,
                birds = sunlit && (look.layer == WeatherLayer.Clear || look.layer == WeatherLayer.Cloudy),
            )
        }
    }
}

internal class Win(val x: Float, val y: Float, val size: Float, val lit: Boolean, val twinkles: Boolean, val phase: Float)

/** All the paths of the landscape for one page size. Built once; the draw only fills them. */
internal class LandscapeGeometry(val w: Float, val h: Float, val dp: Float) {
    val far = Path(); val farRim = Path()
    val mid = Path(); val midRim = Path()
    val city = Path()
    val front = Path(); val frontRim = Path()
    val trees = Path()
    private val windowsFor = HashMap<Int, List<Win>>()          // by look's lit fraction bucket
    private val buildings: List<LandscapeGen.Building>
    private val baseY: Float

    val hy = h * SkyScenes.HORIZON

    init {
        fun ridge(path: Path, rim: Path?, seed: Int, baseline: Float, amplitudeDp: Float, wiggle: Float) {
            val n = 64
            val hs = LandscapeGen.ridge(seed, n, wiggle)
            path.moveTo(0f, h)
            for (i in 0 until n) {
                val x = w * i / (n - 1f)
                val y = baseline - (hs[i] - 0.5f) * 2f * amplitudeDp * dp
                path.lineTo(x, y)
                if (rim != null) { if (i == 0) rim.moveTo(x, y) else rim.lineTo(x, y) }
            }
            path.lineTo(w, h); path.close()
        }
        ridge(far, farRim, 11, hy - 0.006f * h, 26f, 0.8f)
        ridge(mid, midRim, 23, hy + 0.012f * h, 20f, 1.2f)
        baseY = hy + 0.034f * h
        val wDp = w / dp
        buildings = LandscapeGen.skyline(5, wDp, 0.34f)
        buildings.forEach { b ->
            val x = b.x01 * w; val bw = b.widthDp * dp; val bh = b.heightDp * dp
            city.addRect(Rect(x, baseY - bh, x + bw, h))
        }
        ridge(front, frontRim, 37, hy + 0.062f * h, 12f, 0.6f)
        // Pines on the front hill: stacked triangles on a short trunk.
        val frontHs = LandscapeGen.ridge(37, 64, 0.6f)
        LandscapeGen.trees(9, 8).forEach { t ->
            val i = (t.x01 * 63f).toInt().coerceIn(0, 63)
            val gy = hy + 0.062f * h - (frontHs[i] - 0.5f) * 2f * 12f * dp
            val x = t.x01 * w
            val th = t.heightDp * dp
            val tw = th * 0.42f
            val tier = th / (t.tiers + 0.6f)
            for (k in 0 until t.tiers) {
                val top = gy - th + k * tier * 0.72f
                val bottom = top + tier * 1.5f
                val half = tw * (0.45f + 0.55f * (k + 1f) / t.tiers) / 2f
                trees.moveTo(x, top); trees.lineTo(x + half, bottom); trees.lineTo(x - half, bottom); trees.close()
            }
            trees.addRect(Rect(x - tw * 0.05f, gy - tier * 0.4f, x + tw * 0.05f, gy + 3f * dp))
        }
    }

    /** Window rectangles for a given lit fraction (bucketed so a cross-fade doesn't rebuild every frame). */
    fun windows(litFraction: Float): List<Win> {
        val bucket = (litFraction * 20f).toInt()
        return windowsFor.getOrPut(bucket) {
            val out = ArrayList<Win>()
            val frac = bucket / 20f
            buildings.forEachIndexed { bi, b ->
                val bx = b.x01 * w; val bw = b.widthDp * dp; val bh = b.heightDp * dp
                val cell = bw / b.cols
                val rowH = (bh - 8f * dp) / b.rows
                for (r in 0 until b.rows) for (c in 0 until b.cols) {
                    val idx = r * b.cols + c
                    val s = (cell * 0.34f).coerceAtMost(3.4f * dp)
                    val x = bx + c * cell + (cell - s) / 2f
                    val y = baseY - bh + 6f * dp + r * rowH + (rowH - s) / 2f
                    out += Win(x, y, s, LandscapeGen.lit(b.windowSeed, idx, frac), LandscapeGen.twinkles(b.windowSeed, idx), (bi * 7 + idx) * 0.37f)
                }
            }
            out
        }
    }
}

/** Caches the geometry for the size it was last asked for. */
internal class LandscapeCache {
    private var geo: LandscapeGeometry? = null
    fun get(w: Float, h: Float, dp: Float): LandscapeGeometry {
        val g = geo
        if (g != null && g.w == w && g.h == h && g.dp == dp) return g
        return LandscapeGeometry(w, h, dp).also { geo = it }
    }
}

/** Paints the landscape for [look] at [alpha]. [flash] (0..1) lights the silhouettes during a storm's lightning. */
internal fun DrawScope.drawLandscape(
    geo: LandscapeGeometry, look: SkyLook, alpha: Float, t: Float, flash: Float, reduced: Boolean, lowLoad: Boolean,
) {
    val c = LandscapeColors.of(look)
    val dp = geo.dp
    fun lit(color: Color) = if (flash > 0f) lerp(color, Color(0xFFB8C6F0), flash * 0.35f) else color

    drawPath(geo.far, lit(c.far), alpha = alpha)
    drawPath(geo.farRim, c.rim, alpha = alpha * 0.6f, style = Stroke(1f * dp))
    drawPath(geo.mid, lit(c.mid), alpha = alpha)
    drawPath(geo.midRim, c.rim, alpha = alpha * 0.8f, style = Stroke(1.2f * dp))
    drawPath(geo.city, lit(c.city), alpha = alpha)

    // Windows: lit ones glow warm, a few flicker slowly; by day they just catch a little of the sky.
    val windows = geo.windows(c.litFraction)
    val still = reduced || lowLoad
    for (win in windows) {
        if (win.lit) {
            val flicker = if (win.twinkles && !still) 0.55f + 0.45f * (0.5f + 0.5f * sin(t * 0.9f + win.phase * 6.28f)) else 1f
            drawRect(c.windowLit, Offset(win.x, win.y), androidx.compose.ui.geometry.Size(win.size, win.size * 1.25f), alpha = alpha * 0.9f * flicker)
        } else if (c.litFraction == 0f && win.twinkles) {
            drawRect(Color.White, Offset(win.x, win.y), androidx.compose.ui.geometry.Size(win.size, win.size * 1.25f), alpha = alpha * 0.10f)
        }
    }

    drawPath(geo.front, c.front, alpha = alpha)
    drawPath(geo.frontRim, c.rim, alpha = alpha * 0.45f, style = Stroke(1f * dp))
    drawPath(geo.trees, c.front, alpha = alpha)

    // A few birds by day.
    if (c.birds && !still) {
        val wing = Color.White
        for (i in 0 until 3) {
            val speed = 0.012f + i * 0.004f
            val x = ((t * speed + i * 0.37f) % 1.3f - 0.15f) * geo.w
            val y = geo.hy - geo.h * (0.075f + 0.035f * i) + sin(t * 0.7f + i * 2f) * 5f * dp
            val flap = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 5f + i * 1.7f))
            val s = (7f + i * 1.5f) * dp
            val p = Path().apply {
                moveTo(x - s, y - s * 0.5f * flap); quadraticTo(x - s * 0.5f, y - s * 0.55f * flap, x, y)
                quadraticTo(x + s * 0.5f, y - s * 0.55f * flap, x + s, y - s * 0.5f * flap)
            }
            drawPath(p, wing, alpha = alpha * 0.55f, style = Stroke(1.3f * dp, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}
