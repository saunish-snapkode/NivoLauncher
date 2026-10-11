package com.snapkode.nivolauncher.ui.design

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.snapkode.nivolauncher.data.MotionStyle

/**
 * Nivo Motion: one place that decides how fast and how bouncy everything moves.
 * Every animation in the launcher asks this object for its spec instead of hard-coding numbers.
 */
@Immutable
class NivoMotion(
    private val stiffness: Float,
    private val damping: Float,
    /** Scale an icon shrinks to while pressed; 1f disables the effect. */
    val pressScale: Float,
    /** Icons pop in one after another on first display. */
    val stagger: Boolean,
    /** Neighbouring pages recede while swiping. */
    val parallax: Boolean,
    val reduced: Boolean,
    /** Stiffness of the drawer open/close spring. */
    val drawerStiffness: Float,
) {
    /** Spring for movement; [weight] > 1 is stiffer/faster, < 1 softer/slower. */
    fun <T> spring(weight: Float = 1f): FiniteAnimationSpec<T> =
        if (reduced) tween(110) else spring(dampingRatio = damping, stiffness = stiffness * weight)

    /** Plain fade/slide duration in ms. */
    fun duration(base: Int): Int = if (reduced) base / 3 else base

    companion object {
        val Default = NivoMotion(stiffness = 700f, damping = 0.85f, pressScale = 0.9f, stagger = true, parallax = true, reduced = false, drawerStiffness = 500f)
        val Smooth = NivoMotion(stiffness = 380f, damping = 1f, pressScale = 0.93f, stagger = true, parallax = true, reduced = false, drawerStiffness = 320f)
        val Snappy = NivoMotion(stiffness = 1300f, damping = 0.9f, pressScale = 0.88f, stagger = false, parallax = true, reduced = false, drawerStiffness = 1100f)
        val Calm = NivoMotion(stiffness = 220f, damping = 1f, pressScale = 0.95f, stagger = true, parallax = false, reduced = false, drawerStiffness = 220f)
        val Reduced = NivoMotion(stiffness = 1500f, damping = 1f, pressScale = 1f, stagger = false, parallax = false, reduced = true, drawerStiffness = 1500f)

        fun of(style: MotionStyle): NivoMotion = when (style) {
            MotionStyle.Default -> Default
            MotionStyle.Smooth -> Smooth
            MotionStyle.Snappy -> Snappy
            MotionStyle.Calm -> Calm
            MotionStyle.Reduced -> Reduced
        }
    }
}

val LocalMotion = compositionLocalOf { NivoMotion.Default }

/** The chosen preset, forced to Reduced when the system animation scale is turned off. */
@Composable
fun rememberMotion(style: MotionStyle): NivoMotion {
    val context = LocalContext.current
    val systemOff = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    return if (systemOff) NivoMotion.Reduced else NivoMotion.of(style)
}

/** Whether tactile feedback is allowed (user setting). */
val LocalHapticsEnabled = compositionLocalOf { true }
