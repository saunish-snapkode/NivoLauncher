package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.data.AppInfo

val LocalLabelSize = androidx.compose.runtime.compositionLocalOf { 12f }
/** How notification badges look; [counts] is keyed by NotificationState.badgeKey. */
data class BadgeStyle(
    val enabled: Boolean = false,
    val showCount: Boolean = false,
    val color: Color = Color(0xFF3F51B5),
    val counts: Map<String, Int> = emptyMap(),
)

val LocalBadgeStyle = androidx.compose.runtime.compositionLocalOf { BadgeStyle() }

fun BadgeStyle.countFor(app: AppInfo): Int =
    if (!enabled) 0 else counts[com.snapkode.nivolauncher.NotificationState.badgeKey(app.packageName, app.user.hashCode())] ?: 0

/** Dot or number pill in the corner of an icon. */
@Composable
fun NotificationBadge(count: Int, modifier: Modifier = Modifier) {
    val style = LocalBadgeStyle.current
    if (count <= 0) return
    if (style.showCount) {
        androidx.compose.foundation.layout.Box(
            modifier
                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                .background(style.color, androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 10.sp) }
    } else {
        androidx.compose.foundation.layout.Box(
            modifier.size(12.dp).background(style.color, androidx.compose.foundation.shape.CircleShape)
        )
    }
}

/** Colour of labels drawn over the wallpaper. */
val LocalHomeTextColor = androidx.compose.runtime.compositionLocalOf { Color.White }

/** True when the home layout is locked against edits. */
val LocalHomeLocked = androidx.compose.runtime.compositionLocalOf { false }

val LocalLabelLines = androidx.compose.runtime.compositionLocalOf { 1 }

private val LabelShadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f)

/** Icon with optional label. [onWallpaper] picks white-with-shadow text for use over the wallpaper. */
@Composable
fun AppIconView(
    app: AppInfo,
    scale: Float,
    showLabel: Boolean,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size((56 * scale).dp)) {
            Image(
                bitmap = app.icon,
                // The visible label already names the app; only describe the icon when there is no label.
                contentDescription = if (showLabel) null else app.label,
                modifier = Modifier.size((56 * scale).dp),
            )
            NotificationBadge(LocalBadgeStyle.current.countFor(app), Modifier.align(Alignment.TopEnd))
        }
        if (showLabel) {
            // Large system fonts need room: allow two lines and a wider label.
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val bigFont = fontScale > 1.3f
            // Icon labels sit in a narrow cell: grow with the system font, but never past ~150%.
            val labelCap = if (fontScale > 1.5f) 1.5f / fontScale else 1f
            Text(
                text = app.label,
                maxLines = if (bigFont) maxOf(2, LocalLabelLines.current) else LocalLabelLines.current,
                overflow = TextOverflow.Ellipsis,
                fontSize = (LocalLabelSize.current * labelCap).sp,
                color = if (onWallpaper) LocalHomeTextColor.current else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = if (onWallpaper && LocalHomeTextColor.current == Color.White) LabelShadow else null,
                    fontFamily = LocalAppFont.current,
                ),
                modifier = Modifier.width(((if (bigFont) 80 else 64) * scale).dp.coerceAtLeast(56.dp)),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

fun Modifier.hiddenWhen(hidden: Boolean): Modifier = if (hidden) alpha(0f) else this

/**
 * Fades and scales a view in or out with the launcher's spring instead of snapping, so icons pop back into place
 * (a touch of overshoot) and lift away softly.
 */
@Composable
fun Modifier.springVisible(visible: Boolean, hiddenScale: Float = 0.78f): Modifier {
    val v by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f,
        com.snapkode.nivolauncher.ui.design.LocalMotion.current.spring(1.1f),
        label = "springVisible",
    )
    return graphicsLayer {
        alpha = v.coerceIn(0f, 1f)
        val s = hiddenScale + (1f - hiddenScale) * v
        scaleX = s; scaleY = s
    }
}
