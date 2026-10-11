package com.snapkode.nivolauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.draw.drawBehind
import com.snapkode.nivolauncher.ui.design.LocalMotion
import kotlin.math.roundToInt

/**
 * Where the thing that was long-pressed sits on screen (root coordinates). A menu opened while this is set
 * is drawn as a spotlight: the screen dims around the item and the card is attached to it.
 */
val LocalMenuAnchor = compositionLocalOf<Rect?> { null }

/**
 * Long-press menu tied to its item. The background dims with a rounded cut-out around [anchor] so the item stays
 * lit, and the card sits against it with a small pointer. Everything springs out from the item and back into it.
 */
@Composable
fun SpotlightMenu(anchor: Rect, expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val motion = LocalMotion.current
    val progress = remember { Animatable(0f) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            show = true
            progress.animateTo(1f, motion.spring(1f))
        } else if (show) {
            progress.animateTo(0f, motion.spring(1.4f))
            show = false
        }
    }
    if (!show) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The platform dims behind dialogs on its own; the spotlight draws its own dimming instead.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        val scheme = MaterialTheme.colorScheme
        val density = LocalDensity.current
        val p = progress.value.coerceIn(0f, 1.2f)
        val pClamped = p.coerceIn(0f, 1f)

        val margin = 16.dp
        val gap = 16.dp
        val inflate = 10.dp
        var cardH by remember { mutableStateOf(0) }
        var cardWpx by remember { mutableStateOf(0) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            // Until the card has been measured once it is invisible, so a rough width is fine.
            val cardW = if (cardWpx > 0) cardWpx.toFloat() else with(density) { 200.dp.toPx() }
            val m = with(density) { margin.toPx() }
            val g = with(density) { gap.toPx() }
            val pad = with(density) { inflate.toPx() }
            val lit = Rect(anchor.left - pad, anchor.top - pad, anchor.right + pad, anchor.bottom + pad)

            val roomBelow = screenH - lit.bottom - g - m
            val below = cardH == 0 || roomBelow >= cardH || roomBelow >= lit.top - g - m
            val x = (anchor.center.x - cardW / 2f).coerceIn(m, (screenW - cardW - m).coerceAtLeast(m))
            val y = if (below) lit.bottom + g else lit.top - g - cardH
            val caretX = anchor.center.x.coerceIn(x + with(density) { 28.dp.toPx() }, x + cardW - with(density) { 28.dp.toPx() })

            // Scrim with a rounded hole around the item, plus a soft ring so the item reads as lifted.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }, onPress = { }) }
                    .drawBehind {
                        drawRect(Color.Black.copy(alpha = 0.62f * pClamped))
                        val grow = 1f - 0.25f * (1f - pClamped)
                        val holeW = lit.width * grow
                        val holeH = lit.height * grow
                        val topLeft = Offset(lit.center.x - holeW / 2f, lit.center.y - holeH / 2f)
                        val radius = CornerRadius(22.dp.toPx())
                        drawRoundRect(Color.Black, topLeft, Size(holeW, holeH), radius, blendMode = BlendMode.Clear)
                        drawRoundRect(
                            Color.White.copy(alpha = 0.28f * pClamped), topLeft, Size(holeW, holeH), radius,
                            style = Stroke(1.5.dp.toPx()),
                        )
                    },
            )

            // Pointer joining the card to the item.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pClamped }
                    .drawBehind {
                        if (cardH == 0) return@drawBehind
                        val half = 9.dp.toPx()
                        val depth = 8.dp.toPx()
                        val path = Path().apply {
                            if (below) {
                                moveTo(caretX - half, y + 1f); lineTo(caretX, y - depth); lineTo(caretX + half, y + 1f)
                            } else {
                                moveTo(caretX - half, y + cardH - 1f); lineTo(caretX, y + cardH + depth); lineTo(caretX + half, y + cardH - 1f)
                            }
                            close()
                        }
                        drawPath(path, scheme.surfaceContainerHigh)
                    },
            )

            // The card itself grows out of the pointer.
            val pivotX = ((caretX - x) / cardW).coerceIn(0f, 1f)
            Column(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    // Hug the content, between a sensible minimum and maximum.
                    .widthIn(min = 120.dp, max = 280.dp)
                    .width(IntrinsicSize.Max)
                    .onSizeChanged { cardH = it.height; cardWpx = it.width }
                    .graphicsLayer {
                        val s = 0.55f + 0.45f * p
                        scaleX = s; scaleY = s
                        alpha = if (cardH == 0 || cardWpx == 0) 0f else pClamped
                        transformOrigin = TransformOrigin(pivotX, if (below) 0f else 1f)
                    }
                    .shadow(16.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(20.dp))
                    .then(Modifier.drawBehind { drawRect(scheme.surfaceContainerHigh) })
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}
