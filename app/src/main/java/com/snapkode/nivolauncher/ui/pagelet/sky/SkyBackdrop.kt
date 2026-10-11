package com.snapkode.nivolauncher.ui.pagelet.sky

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Seeded, preallocated layout of everything that drifts, twinkles or falls. Nothing here is allocated per frame, and the
 * same seed always yields the same sky, so screenshots are stable.
 */
internal class SkyParticles(seed: Int = 7) {
    private val rnd = Random(seed)

    // Clouds: up to 8 washes.
    val cloudY = FloatArray(8) { 30f + rnd.nextFloat() * 330f }          // dp from the top
    val cloudSize = FloatArray(8) { rnd.nextFloat() }                     // 0..1 between the layer's min and max width
    val cloudPhase = FloatArray(8) { rnd.nextFloat() }                    // starting offset along the drift
    val cloudPeriod = FloatArray(8) { 110f + rnd.nextFloat() * 90f }      // seconds per pass

    // Stars: 28 in the top 400 dp.
    val starX = FloatArray(28) { rnd.nextFloat() }
    val starY = FloatArray(28) { rnd.nextFloat() * 400f }
    val starR = FloatArray(28) { 0.8f + rnd.nextFloat() * 0.9f }
    val starA = FloatArray(28) { 0.35f + rnd.nextFloat() * 0.55f }
    val starPeriod = FloatArray(28) { 3f + rnd.nextFloat() * 4f }
    val starPhase = FloatArray(28) { rnd.nextFloat() }

    // Rain: up to 56 hairline streaks.
    val rainX = FloatArray(56) { rnd.nextFloat() }
    val rainLen = FloatArray(56) { 16f + rnd.nextFloat() * 18f }
    val rainSpeed = FloatArray(56) { rnd.nextFloat() }                    // 0..1 between the layer's min and max duration
    val rainPhase = FloatArray(56) { rnd.nextFloat() }

    // Fog bands.
    val fogY = floatArrayOf(320f, 400f, 450f)
    val fogH = floatArrayOf(90f + rnd.nextFloat() * 20f, 90f + rnd.nextFloat() * 20f, 90f + rnd.nextFloat() * 20f)
    val fogA = floatArrayOf(0.22f + rnd.nextFloat() * 0.08f, 0.22f + rnd.nextFloat() * 0.08f, 0.22f + rnd.nextFloat() * 0.08f)
    val fogPeriod = floatArrayOf(16f + rnd.nextFloat() * 8f, 16f + rnd.nextFloat() * 8f, 16f + rnd.nextFloat() * 8f)
}

/** A 256×256 tiling film-grain bitmap, made once for the whole process. */
internal object SkyGrain {
    val bitmap: ImageBitmap by lazy {
        val rnd = Random(1234)
        val size = 256
        val px = IntArray(size * size) {
            val v = 90 + rnd.nextInt(76)                       // mid grey, so Overlay only nudges brightness
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Built once: the shader and paint are reused by every frame of every Sky. */
    val paint: Paint by lazy {
        Paint().apply {
            shader = ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)
            blendMode = BlendMode.Overlay
            alpha = 0.07f
        }
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The page-sized sky: gradient, weather light, stars, clouds, rain, fog, the horizon bloom and hairline, grain and
 * vignette. Ambient motion runs on one frame clock and only while [active]; [lowLoad] halves rain and drops twinkle and
 * drift; [reducedMotion] draws the still final state.
 */
@Composable
fun SkyBackdrop(
    ctl: SkyController,
    active: Boolean,
    reducedMotion: Boolean,
    lowLoad: Boolean,
    /** False while the page is sliding: the cloud blur is the costliest thing here and isn't needed on a moving page. */
    blurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val particles = remember { SkyParticles() }
    val clock = remember { mutableLongStateOf(0L) }
    val animate = active && !reducedMotion
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameNanosCompat { it }
        // About 30 fps: the sky drifts a few pixels a second, so every display frame (up to 120) only costs GPU and battery.
        while (true) {
            kotlinx.coroutines.delay(SKY_FRAME_MS)
            withFrameNanosCompat { clock.longValue = it - start }
        }
    }
    val dp = density.density

    Box(modifier.fillMaxSize()) {
        // 1. gradient, weather light and stars
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val t = clock.longValue / 1e9f
            val mix = ctl.mix.value
            if (mix < 1f) drawLook(ctl.from, 1f, w, h, dp, t, particles, lowLoad, reducedMotion, ctl)
            drawLook(ctl.to, if (mix < 1f) mix else 1f, w, h, dp, t, particles, lowLoad, reducedMotion, ctl)
        }
        // 2. cloud washes (softly blurred on API 31+)
        val blur = if (Build.VERSION.SDK_INT >= 31 && !lowLoad && blurEnabled) with(density) { ctl.to.weather.cloudBlurDp.dp.toPx() } else 0f
        Canvas(
            Modifier.fillMaxSize().then(
                if (blur > 0f) Modifier.graphicsLayer { renderEffect = BlurEffect(blur, blur, TileMode.Decal) } else Modifier
            )
        ) {
            val w = size.width; val h = size.height
            val t = clock.longValue / 1e9f
            val mix = ctl.mix.value
            if (mix < 1f) drawClouds(ctl.from.weather, 1f - mix, w, h, dp, t, particles, lowLoad, reducedMotion)
            drawClouds(ctl.to.weather, if (mix < 1f) mix else 1f, w, h, dp, t, particles, lowLoad, reducedMotion)
        }
        // 2b. the illustrated horizon: hills, city, pines. Opaque, so the old look is painted first and the new one fades in over it.
        val landscape = remember { LandscapeCache() }
        Canvas(Modifier.fillMaxSize()) {
            val geo = landscape.get(size.width, size.height, this.density)
            val t = clock.longValue / 1e9f
            val mix = ctl.mix.value
            val flash = if (ctl.to.weather.flash && !reducedMotion) stormFlash(t) else 0f
            if (mix < 1f) drawLandscape(geo, ctl.from, 1f, t, if (ctl.from.weather.flash && !reducedMotion) stormFlash(t) else 0f, reducedMotion, lowLoad)
            drawLandscape(geo, ctl.to, if (mix < 1f) mix else 1f, t, flash, reducedMotion, lowLoad)
        }
        // 3. rain, fog, flash, bloom, grain, vignette
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val t = clock.longValue / 1e9f
            val mix = ctl.mix.value
            if (mix < 1f) drawAtmosphere(ctl.from, 1f - mix, w, h, dp, t, particles, lowLoad, reducedMotion, ctl)
            drawAtmosphere(ctl.to, if (mix < 1f) mix else 1f, w, h, dp, t, particles, lowLoad, reducedMotion, ctl)
            drawFinish(w, h)
        }
    }
}

private suspend fun <T> withFrameNanosCompat(block: (Long) -> T): T = androidx.compose.runtime.withFrameNanos(block)

// ---- layer 1 ---------------------------------------------------------------------------------------------------

private fun DrawScope.drawLook(
    look: SkyLook, alpha: Float, w: Float, h: Float, dp: Float, t: Float, p: SkyParticles,
    lowLoad: Boolean, reduced: Boolean, ctl: SkyController,
) {
    val ph = look.phase
    val gradient = Brush.verticalGradient(
        0f to ph.sky0, 0.36f to ph.sky1, SkyScenes.HORIZON to ph.sky2, SkyScenes.GROUND_START to ph.ground, 1f to ph.groundEnd,
    )
    drawRect(gradient, alpha = alpha)

    val ws = look.weather
    if (ws.overlayTop != null && ws.overlayMid != null) {
        drawRect(
            Brush.verticalGradient(0f to ws.overlayTop, 0.55f to ws.overlayMid, 0.62f to Color.Transparent, 1f to Color.Transparent),
            alpha = alpha,
        )
    }

    if (ws.textScrim > 0f) {
        drawRect(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = ws.textScrim), 0.36f to Color.Black.copy(alpha = ws.textScrim), 0.46f to Color.Transparent),
            alpha = alpha,
        )
    }

    // Stars: only under Clear and Fog, and only while the phase has any.
    val starStrength = ph.stars
    if (ws.showsStars && starStrength > 0f) {
        val twinkle = !lowLoad && !reduced
        for (i in 0 until 28) {
            val x = p.starX[i] * w
            val y = p.starY[i] * dp
            if (y > h * 0.5f) continue
            val tw = if (twinkle) 0.65f + 0.35f * sin(((t / p.starPeriod[i]) + p.starPhase[i]) * 2f * PI.toFloat()) else 1f
            drawCircle(Color.White, p.starR[i] * dp, Offset(x, y), alpha = (starStrength * p.starA[i] * tw * alpha).coerceIn(0f, 1f))
        }
    }
}

// ---- layer 2 ---------------------------------------------------------------------------------------------------

private fun DrawScope.drawClouds(
    ws: WeatherStyle, alpha: Float, w: Float, h: Float, dp: Float, t: Float, p: SkyParticles,
    lowLoad: Boolean, reduced: Boolean,
) {
    if (ws.cloudCount == 0) return
    val drift = !lowLoad && !reduced
    for (i in 0 until ws.cloudCount) {
        val cw = lerpF(ws.cloudMinWidthDp.toFloat(), ws.cloudMaxWidthDp.toFloat(), p.cloudSize[i]) * dp
        val ch = cw * ws.cloudHeightRatio
        val travel = w + 2f * cw
        val pos = ((if (drift) t / p.cloudPeriod[i] else 0f) + p.cloudPhase[i]) % 1f
        val cx = -cw + pos * travel + cw / 2f
        val cy = p.cloudY[i] * dp * (h / (844f * dp)).coerceIn(0.7f, 1.4f)
        val r = cw / 2f
        // A soft elliptical wash: a radial gradient drawn as a circle, squashed to the cloud's aspect.
        withTransform({ scale(1f, ch / cw, pivot = Offset(cx, cy)) }) {
            drawCircle(
                Brush.radialGradient(
                    0f to ws.cloudFill, 1f to Color.Transparent, center = Offset(cx, cy), radius = r,
                ),
                radius = r, center = Offset(cx, cy), alpha = alpha,
            )
        }
    }
}

// ---- layer 3 ---------------------------------------------------------------------------------------------------

private fun DrawScope.drawAtmosphere(
    look: SkyLook, alpha: Float, w: Float, h: Float, dp: Float, t: Float, p: SkyParticles,
    lowLoad: Boolean, reduced: Boolean, ctl: SkyController,
) {
    val ws = look.weather
    val ph = look.phase
    val horizonY = h * SkyScenes.HORIZON

    // Fog bands sway slowly.
    if (ws.fogBands > 0) {
        for (i in 0 until ws.fogBands) {
            val sway = if (reduced || lowLoad) 0f else sin(t / p.fogPeriod[i] * 2f * PI.toFloat() + i) * 30f * dp
            val y = p.fogY[i] * dp * (h / (844f * dp)).coerceIn(0.7f, 1.4f)
            val bh = p.fogH[i] * dp
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent, 0.5f to Color(0xFFDDE2EA).copy(alpha = p.fogA[i]), 1f to Color.Transparent,
                    startY = y - bh / 2f, endY = y + bh / 2f,
                ),
                topLeft = Offset(-40f * dp + sway, y - bh / 2f), size = androidx.compose.ui.geometry.Size(w + 80f * dp, bh), alpha = alpha,
            )
        }
    }

    // Rain: hairline streaks, slanted 10°, falling only through the sky.
    if (ws.rainStreaks > 0 && !reduced) {
        val count = if (lowLoad) ws.rainStreaks / 2 else ws.rainStreaks
        val slant = tan(10f * PI.toFloat() / 180f)
        val fall = h * 0.62f
        val color = Color(0xFFDCE6FF)
        for (i in 0 until count) {
            val dur = lerpF(ws.rainMinSec, ws.rainMaxSec, p.rainSpeed[i])
            val prog = ((t / dur) + p.rainPhase[i]) % 1f
            val len = p.rainLen[i] * dp
            val y = prog * (fall + len) - len
            val x = p.rainX[i] * (w + fall * slant) - fall * slant * prog
            drawLine(
                Brush.linearGradient(
                    0f to Color.Transparent, 1f to color.copy(alpha = 0.5f),
                    start = Offset(x, y), end = Offset(x + len * slant, y + len),
                ),
                start = Offset(x, y), end = Offset(x + len * slant, y + len), strokeWidth = 1f * dp, alpha = alpha,
            )
        }
    }

    // A distant flash every ~8 s: a glow, never a bolt.
    if (ws.flash && !reduced) {
        val f = stormFlash(t)
        if (f > 0f) {
            val c = Offset(w * 0.7f, h * 0.25f)
            val rx = w * 0.7f
            withTransform({ scale(1f, (h * 0.5f) / rx, pivot = c) }) {
                drawCircle(
                    Brush.radialGradient(0f to Color(0xFFDCE4FF).copy(alpha = 0.9f), 1f to Color.Transparent, center = c, radius = rx),
                    radius = rx, center = c, alpha = f * alpha,
                )
            }
        }
    }

    // Horizon bloom: a wide glow on the horizon and a tight reflection just below it, under the sun or moon.
    val hour = ctl.data?.let { it.nowMin / 60f } ?: 12f
    val travelled = hour * ctl.travel.value
    val (mx, _) = SkyArc.point(travelled, w / 2f, horizonY, ctl.arcRadiusPx.takeIf { it > 0f } ?: ((w - 2f * PageletTokensDp.ARC_MARGIN * dp) / 2f))
    val moon = ph.marker == Marker.Moon
    val breathe = if (reduced || lowLoad) 1f else 0.9f + 0.1f * sin(t / 9f * 2f * PI.toFloat())
    val bloomAlpha = ph.bloom * (if (moon) 1f else max(0.25f, ws.sunOpacity)) * breathe * alpha
    run {
        val rx = w * 0.62f
        withTransform({ scale(1f, (h * 0.15f) / rx, pivot = Offset(mx, horizonY)) }) {
            drawCircle(
                Brush.radialGradient(0f to ph.glow, 0.72f to Color.Transparent, 1f to Color.Transparent, center = Offset(mx, horizonY), radius = rx),
                radius = rx, center = Offset(mx, horizonY), alpha = bloomAlpha,
            )
        }
        val rx2 = w * 0.16f
        val c2 = Offset(mx, horizonY + 30f * dp)
        withTransform({ scale(1f, (h * 0.08f) / rx2, pivot = c2) }) {
            drawCircle(
                Brush.radialGradient(0f to ph.glow, 1f to Color.Transparent, center = c2, radius = rx2),
                radius = rx2, center = c2, alpha = bloomAlpha,
            )
        }
    }

    // (The horizon itself is now the illustrated landscape, so there is no hairline.)
}

private object PageletTokensDp { const val ARC_MARGIN = 30f }

/** Film grain (Overlay, 7%) and the vignette; identical in every scene so they are drawn once, on top. */
private fun DrawScope.drawFinish(w: Float, h: Float) {
    drawIntoCanvas { canvas -> canvas.drawRect(0f, 0f, w, h, SkyGrain.paint) }
    val c = Offset(w * 0.5f, h * 0.38f)
    val rx = w * 1.3f
    withTransform({ scale(1f, (h * 0.75f) / rx, pivot = c) }) {
        drawCircle(
            Brush.radialGradient(0f to Color.Transparent, 0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f), center = c, radius = rx),
            radius = rx, center = c,
        )
    }
}

/** The distant lightning's brightness (0..1) at [t] seconds: two quick pulses every eight seconds. */
internal fun stormFlash(t: Float): Float {
    val cyc = (t % 8f)
    return when {
        cyc < 0.1f -> lerpF(0f, 0.35f, cyc / 0.1f)
        cyc < 0.2f -> lerpF(0.35f, 0.04f, (cyc - 0.1f) / 0.1f)
        cyc < 0.35f -> lerpF(0.04f, 0.25f, (cyc - 0.2f) / 0.15f)
        cyc < 0.5f -> lerpF(0.25f, 0f, (cyc - 0.35f) / 0.15f)
        else -> 0f
    }
}

/** Time between redraws of the moving sky. */
internal const val SKY_FRAME_MS = 28L
