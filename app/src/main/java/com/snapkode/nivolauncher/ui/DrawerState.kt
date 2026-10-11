package com.snapkode.nivolauncher.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Open amount of the app drawer: 0 = closed, 1 = fully open. Driven by drags and flings. */
class DrawerState {
    var progress by mutableFloatStateOf(0f)
        private set
    var heightPx by mutableFloatStateOf(1f)

    /** Spring feel, set from the active motion preset. */
    var stiffness: Float = 500f
    var damping: Float = 1f

    /** Derived, so readers recompose only when the drawer opens or closes, not on every animation frame. */
    val isOpen: Boolean by derivedStateOf { progress > 0f }

    /** True once the drawer covers most of the screen (system-bar icons switch colour here). */
    val isMostlyOpen: Boolean by derivedStateOf { progress > 0.6f }

    /** True while an icon is being dragged out of the drawer: keeps its content alive after it slides closed. */
    var keepComposed by mutableStateOf(false)

    /** Set when the drawer is opened from the home search bar, so the field takes focus. */
    var focusSearch by mutableStateOf(false)

    /** Used by the predictive back gesture to scrub the drawer closed. */
    fun scrubTo(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }

    /** [deltaPx] > 0 drags the drawer down (closing). */
    fun dragBy(deltaPx: Float) {
        progress = (progress - deltaPx / heightPx).coerceIn(0f, 1f)
    }

    suspend fun animateTo(target: Float, velocityPxPerSec: Float = 0f) {
        animate(
            initialValue = progress,
            targetValue = target,
            initialVelocity = -velocityPxPerSec / heightPx,
            animationSpec = spring(dampingRatio = damping, stiffness = stiffness),
        ) { value, _ -> progress = value }
        progress = target
    }

    /** Animates to the nearest end, favouring the fling direction. */
    suspend fun settle(velocityY: Float) {
        val target = when {
            velocityY > FLING -> 0f
            velocityY < -FLING -> 1f
            progress > 0.5f -> 1f
            else -> 0f
        }
        animateTo(target, velocityY)
    }

    private companion object {
        const val FLING = 700f
    }
}
