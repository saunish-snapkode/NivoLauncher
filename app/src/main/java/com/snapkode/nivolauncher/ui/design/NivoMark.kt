package com.snapkode.nivolauncher.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The brand colours of the mark: the ivory tile and its ink. */
object NivoBrand {
    val IvoryTop = Color(0xFFFFFFFF)
    val IvoryBottom = Color(0xFFECE9E4)
    val Ink = Color(0xFF22252B)
    val InkNight = Color(0xFF16181C)
    val IvoryNight = Color(0xFFF3F1EC)
}

/**
 * The Nivo mark, drawn from the same geometry as the app icon (a 512-unit canvas): an ivory rounded tile, a rounded square
 * and a dash. With [breathing] the dash shortens and returns every 1.8 s, as in the loading animation; reduced motion holds it.
 * [tile] false draws only the square and dash, in [ink].
 */
@Composable
fun NivoMark(size: Dp, modifier: Modifier = Modifier, breathing: Boolean = false, tile: Boolean = true, ink: Color = NivoBrand.Ink) {
    val reduced = LocalMotion.current.reduced
    val t by rememberInfiniteTransition(label = "nivoMark").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)), RepeatMode.Reverse), label = "nivoDash",
    )
    val squeeze = if (breathing && !reduced) t else 0f
    Canvas(modifier.size(size).semantics { contentDescription = "Nivo" }) {
        val u = this.size.minDimension / 512f
        if (tile) drawRoundRect(
            Brush.linearGradient(listOf(NivoBrand.IvoryTop, NivoBrand.IvoryBottom), Offset(16 * u, 16 * u), Offset(496 * u, 496 * u)),
            Offset(16 * u, 16 * u), Size(480 * u, 480 * u), CornerRadius(126 * u),
        )
        drawRoundRect(ink, Offset(139 * u, 139 * u), Size(234 * u, 234 * u), CornerRadius(53 * u), style = Stroke(25 * u))
        // From 213–299 to 234–278 and back.
        val x0 = 213f + 21f * squeeze; val x1 = 299f - 21f * squeeze
        drawLine(ink, Offset(x0 * u, 256 * u), Offset(x1 * u, 256 * u), 27 * u, cap = StrokeCap.Round)
    }
}

/** Shown while the launcher reads the installed apps on a cold start: the mark breathing on the brand colour. */
@Composable
fun NivoLoadingScreen(dark: Boolean, modifier: Modifier = Modifier) {
    val bg = if (dark) NivoBrand.InkNight else Color(0xFFF6F4F0)
    val ink = if (dark) NivoBrand.IvoryNight else NivoBrand.Ink
    Box(modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NivoMark(120.dp, breathing = true, tile = false, ink = ink)
            Text(
                "nivo", Modifier.offset(y = (-14).dp),
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp, color = ink.copy(alpha = 0.7f)),
            )
        }
    }
}
