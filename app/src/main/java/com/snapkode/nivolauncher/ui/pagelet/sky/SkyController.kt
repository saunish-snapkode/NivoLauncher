package com.snapkode.nivolauncher.ui.pagelet.sky

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snapkode.nivolauncher.pagelet.PageletTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A phase + weather combination to draw. */
class SkyLook(val phaseKind: TimePhase, val layer: WeatherLayer) {
    val phase: PhaseStyle = SkyPalettes.of(phaseKind)
    val weather: WeatherStyle = SkyWeatherStyles.of(layer)

    override fun equals(other: Any?) = other is SkyLook && other.phaseKind == phaseKind && other.layer == layer
    override fun hashCode() = phaseKind.hashCode() * 31 + layer.hashCode()
}

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * All the time-varying state of one Sky Pagelet: the data, which look is showing (and the cross-fade from the previous
 * one), and the entrance animations. Lives as long as the Pagelet is composed; the entrance plays once per process the
 * first time the page becomes visible.
 */
class SkyController(private val scope: CoroutineScope, private val reducedMotion: Boolean) {
    var data by mutableStateOf<SkyData?>(null)
        private set

    var to by mutableStateOf(SkyLook(TimePhase.Night, WeatherLayer.Clear))
        private set
    var from by mutableStateOf(to)
        private set

    /** 0 → 1 while [from] fades into [to]. */
    val mix = Animatable(1f)

    // Entrance (all 0 → 1). With reduced motion they are simply 1 from the start.
    val rise = Animatable(if (reducedMotion) 1f else 0f)
    val arcDraw = Animatable(if (reducedMotion) 1f else 0f)
    val travel = Animatable(if (reducedMotion) 1f else 0f)
    val segments = Animatable(if (reducedMotion) 1f else 0f)
    val counts = Animatable(if (reducedMotion) 1f else 0f)
    val goalBar = Animatable(if (reducedMotion) 1f else 0f)
    val ground = Animatable(if (reducedMotion) 1f else 0f)

    /** The arc's radius in px once the clock and weather block above it have been measured; 0 until then. */
    var arcRadiusPx by mutableFloatStateOf(0f)

    private var entered = reducedMotion
    private var haveScene = false

    fun update(newData: SkyData?, look: SkyLook) {
        data = newData
        if (!haveScene) {
            haveScene = true
            from = look; to = look
            scope.launch { mix.snapTo(1f) }
            return
        }
        if (look == to) return
        // Start the cross-fade from whatever is on screen right now: if one is in progress, its target becomes the origin.
        from = if (mix.value >= 0.5f) to else from
        to = look
        scope.launch {
            mix.snapTo(0f)
            mix.animateTo(
                1f,
                tween(if (reducedMotion) PageletTokens.REDUCED_CROSSFADE_MS else PageletTokens.SCENE_CROSSFADE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
        }
    }

    /** Plays the entrance once. Safe to call on every visibility change; later calls do nothing. */
    fun enterIfNeeded() {
        if (entered) return
        entered = true
        scope.launch {
            listOf(
                async { rise.animateTo(1f, tween(PageletTokens.ENTER_RISE_MS, easing = EmphasizedDecelerate)) },
                async { arcDraw.animateTo(1f, tween(PageletTokens.ARC_DRAW_MS, easing = Emphasized)) },
                async { travel.animateTo(1f, tween(PageletTokens.ARC_TRAVEL_MS, easing = Emphasized)) },
                async {
                    delay(PageletTokens.SEGMENT_START_MS.toLong())
                    segments.animateTo(1f, tween(1500, easing = Emphasized))
                },
                async { counts.animateTo(1f, tween(PageletTokens.COUNT_UP_MS, easing = EaseOutCubic)) },
                async {
                    delay(PageletTokens.GOAL_BAR_DELAY_MS.toLong())
                    goalBar.animateTo(1f, tween(PageletTokens.GOAL_BAR_MS, easing = Emphasized))
                },
                async {
                    delay(240)
                    ground.animateTo(1f, tween(PageletTokens.ENTER_RISE_MS, easing = EmphasizedDecelerate))
                },
            ).awaitAll()
        }
    }
}
