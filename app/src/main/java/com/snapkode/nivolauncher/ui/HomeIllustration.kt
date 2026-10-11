package com.snapkode.nivolauncher.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.data.ClockStyle
import com.snapkode.nivolauncher.data.DockStyle
import com.snapkode.nivolauncher.data.LauncherSettings

/**
 * A static, hand-drawn picture of a home screen. Unlike [HomePreview] it never scales the real
 * launcher, so every edge sits on the dp grid and the shapes stay crisp at any density. It follows
 * the grid, icon size, labels, dock and clock settings.
 */
@Composable
fun HomeIllustration(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tints = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer)
    val shape = RoundedCornerShape(32.dp)

    Box(
        modifier
            .size(width = 176.dp, height = 352.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.surfaceContainerLow, scheme.tertiaryContainer.copy(alpha = 0.45f))))
            .border(4.dp, scheme.outlineVariant, shape)
            // A picture, not controls: one description instead of the fake clock's digits being read out.
            .clearAndSetSemantics { contentDescription = "Preview of your home screen" }
    ) {
        Column(Modifier.fillMaxSize().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // status bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (!settings.hideStatusBar) {
                    Box(Modifier.size(width = 18.dp, height = 5.dp).background(scheme.onSurface.copy(alpha = 0.5f), CircleShape))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) { Box(Modifier.size(5.dp).background(scheme.onSurface.copy(alpha = 0.5f), CircleShape)) }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(if (settings.clockStyle == ClockStyle.Off) 8.dp else 84.dp).animateContentSize(),
                contentAlignment = Alignment.Center,
            ) { ClockArt(settings.clockStyle) }
            // app grid
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                repeat(settings.rows) { r ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(settings.columns) { c ->
                            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                val labelRoom = if (settings.showLabels) 7.dp else 0.dp
                                val tile = (minOf(maxWidth, maxHeight - labelRoom) * 0.68f * settings.iconScale).coerceIn(8.dp, 30.dp)
                                val tint = tints[(r * settings.columns + c) % tints.size]
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.size(tile).background(tint.copy(alpha = if (tint == scheme.primaryContainer) 0.9f else 0.75f), RoundedCornerShape(tile * 0.34f)))
                                    if (settings.showLabels) Box(Modifier.size(width = tile * 0.8f, height = 3.dp).background(scheme.onSurface.copy(alpha = 0.35f), CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            if (settings.showPageDots) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.size(5.dp).background(scheme.onSurface.copy(alpha = if (it == 0) 0.8f else 0.3f), CircleShape)) }
                }
            }
            Box(Modifier.height(if (settings.showDock) 70.dp else 22.dp))
        }
        if (settings.showDock) DockArt(settings.dockStyle, settings.dockSlots.coerceIn(3, 6), Modifier.align(Alignment.BottomCenter))
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(width = 52.dp, height = 4.dp)
                .background(scheme.onSurface.copy(alpha = 0.45f), CircleShape)
        )
    }
}

@Composable
private fun ClockArt(style: ClockStyle) {
    val scheme = MaterialTheme.colorScheme
    // The mock phone is a fixed-size drawing, so its text is sized in dp: a large system font must not burst the frame.
    val density = LocalDensity.current
    fun px(dp: Float) = with(density) { dp.dp.toSp() }
    when (style) {
        ClockStyle.Off -> Unit
        ClockStyle.Hero -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("10:08", fontSize = px(40f), fontWeight = FontWeight.Light, color = scheme.onSurface, letterSpacing = (-1).sp, maxLines = 1)
            Text("Sat, 11 Oct", fontSize = px(11f), color = scheme.onSurfaceVariant, maxLines = 1)
        }
        ClockStyle.Minimal -> Text("10:08", fontSize = px(22f), fontWeight = FontWeight.Medium, color = scheme.onSurface, maxLines = 1)
        ClockStyle.Compact -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("10:08", fontSize = px(20f), fontWeight = FontWeight.Medium, color = scheme.onSurface, maxLines = 1)
            Box(Modifier.width(1.dp).height(18.dp).background(scheme.outline))
            Text("Sat\n11 Oct", fontSize = px(10f), lineHeight = px(12f), color = scheme.onSurfaceVariant, maxLines = 2)
        }
        ClockStyle.Analog -> {
            val face = scheme.onSurface
            val accent = scheme.primary
            Canvas(Modifier.size(72.dp)) {
                val c = center
                drawCircle(face.copy(alpha = 0.12f), radius = size.minDimension / 2)
                drawCircle(face.copy(alpha = 0.35f), radius = size.minDimension / 2, style = Stroke(2.dp.toPx()))
                // hour hand towards 10, minute hand towards 2
                drawLine(face, c, Offset(c.x - 12.dp.toPx(), c.y - 17.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
                drawLine(accent, c, Offset(c.x + 17.dp.toPx(), c.y - 21.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(accent, radius = 3.5.dp.toPx(), center = c)
            }
        }
    }
}

@Composable
private fun DockArt(style: DockStyle, slots: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val icons = @Composable {
        repeat(slots) { Box(Modifier.size(if (slots > 4) 22.dp else 26.dp).background(scheme.primary.copy(alpha = 0.85f - it * 0.07f), RoundedCornerShape(8.dp))) }
    }
    when (style) {
        DockStyle.Floating -> Row(
            modifier.padding(start = 12.dp, end = 12.dp, bottom = 20.dp).fillMaxWidth()
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.9f), RoundedCornerShape(24.dp)).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) { icons() }
        DockStyle.Solid -> Row(
            modifier.fillMaxWidth()
                .background(scheme.surfaceContainerHighest, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) { icons() }
        DockStyle.Minimal -> Row(
            modifier.padding(start = 12.dp, end = 12.dp, bottom = 22.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) { icons() }
    }
}
