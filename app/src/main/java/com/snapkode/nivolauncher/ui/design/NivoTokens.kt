package com.snapkode.nivolauncher.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.data.NivoPaletteSpec

/** Spacing scale. Use these instead of ad-hoc dp values. */
object NivoSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii: Material 3 Expressive favours large outer corners with tighter inner ones. */
object NivoRadius {
    val inner = 6.dp
    val control = 14.dp
    val card = 20.dp
    val sheet = 28.dp
    val dock = 28.dp
    val hero = 28.dp
}

/** Elevation steps for the five semantic surface levels. */
object NivoElevation {
    val chrome = 0.dp
    val content = 0.dp
    val interactive = 2.dp
    val modal = 6.dp
}

private fun Long.c() = Color(this)

/** HSL to colour. [h] in degrees (wraps), [s] and [l] in 0..1. */
fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/** Hue (degrees) of a colour; 0 for greys. */
fun hueOf(color: Color): Float {
    val r = color.red; val g = color.green; val b = color.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val d = max - min
    if (d < 1e-4f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return ((h * 60f) + 360f) % 360f
}

/**
 * Nivo's card-based scheme: quiet neutral surfaces, hairline borders and a single accent used sparingly
 * (primary actions, selection, small marks). Dark is near-black with soft graphite cards; light is warm
 * paper with white cards. The accent hue only tints containers lightly, never whole surfaces.
 */
fun nivoColorScheme(p: NivoPaletteSpec, accent: Color): ColorScheme {
    val h = hueOf(accent)
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    return if (p.dark) {
        val amoled = p.canvas == 0xFF000000L
        val canvas = if (amoled) Color.Black else Color(0xFF0B0B0D)
        val low = if (amoled) Color(0xFF0A0A0C) else Color(0xFF101013)
        val card = if (amoled) Color(0xFF111113) else Color(0xFF151518)
        val high = if (amoled) Color(0xFF18181B) else Color(0xFF1C1C20)
        val highest = if (amoled) Color(0xFF212125) else Color(0xFF26262B)
        val primary = hsl(h, 0.70f, 0.74f)
        base.copy(
            primary = primary,
            onPrimary = hsl(h, 0.70f, 0.12f),
            primaryContainer = lerp(card, primary, 0.16f),
            onPrimaryContainer = hsl(h, 0.80f, 0.86f),
            inversePrimary = hsl(h, 0.55f, 0.40f),
            secondary = Color(0xFFB8B8C2),
            onSecondary = Color(0xFF1A1A1F),
            secondaryContainer = highest,
            onSecondaryContainer = Color(0xFFE6E6EA),
            tertiary = hsl(h + 40f, 0.45f, 0.74f),
            onTertiary = hsl(h + 40f, 0.5f, 0.14f),
            tertiaryContainer = lerp(card, hsl(h + 40f, 0.5f, 0.7f), 0.14f),
            onTertiaryContainer = hsl(h + 40f, 0.6f, 0.86f),
            background = canvas, onBackground = Color(0xFFF3F3F5),
            surface = canvas, onSurface = Color(0xFFF3F3F5),
            surfaceVariant = high, onSurfaceVariant = Color(0xFF9C9CA8),
            surfaceContainerLowest = canvas,
            surfaceContainerLow = low,
            surfaceContainer = card,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
            outline = Color(0xFF5C5C66),
            outlineVariant = Color(0xFF26262C),
        )
    } else {
        val primary = hsl(h, 0.55f, 0.36f)
        base.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, hsl(h, 0.7f, 0.55f), 0.14f),
            onPrimaryContainer = hsl(h, 0.6f, 0.22f),
            inversePrimary = hsl(h, 0.70f, 0.78f),
            secondary = Color(0xFF55555F),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDEDF1),
            onSecondaryContainer = Color(0xFF26262B),
            tertiary = hsl(h + 40f, 0.40f, 0.36f),
            onTertiary = Color.White,
            tertiaryContainer = lerp(Color.White, hsl(h + 40f, 0.6f, 0.6f), 0.14f),
            onTertiaryContainer = hsl(h + 40f, 0.6f, 0.2f),
            background = Color(0xFFF4F4F6), onBackground = Color(0xFF111114),
            surface = Color(0xFFF4F4F6), onSurface = Color(0xFF111114),
            surfaceVariant = Color(0xFFEBEBEF), onSurfaceVariant = Color(0xFF6A6A75),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFAFB),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFF0F0F3),
            surfaceContainerHighest = Color(0xFFE8E8ED),
            outline = Color(0xFF8A8A95),
            outlineVariant = Color(0xFFE3E3E8),
        )
    }
}

/** A flat card: tonal fill with a hairline border, the one surface treatment used across the app. */
fun Modifier.nivoCard(
    shape: androidx.compose.ui.graphics.Shape,
    color: Color,
    border: Color,
): Modifier = this
    .background(color, shape)
    .border(1.dp, border, shape)
    .clip(shape)

/** Nivo type scale: light, large numerals for the clock; restrained weights elsewhere. */
fun nivoTypography(family: FontFamily? = null): Typography {
    val f = family ?: FontFamily.SansSerif
    fun style(size: Int, weight: FontWeight, tracking: Double = 0.0, line: Int = (size * 1.3).toInt()) =
        TextStyle(fontFamily = f, fontSize = size.sp, fontWeight = weight, letterSpacing = tracking.sp, lineHeight = line.sp)
    return Typography(
        displayLarge = style(57, FontWeight.Light, -1.0),
        displayMedium = style(45, FontWeight.Light, -0.5),
        displaySmall = style(36, FontWeight.Light, -0.25),
        headlineLarge = style(40, FontWeight.Light, -0.5),
        headlineMedium = style(32, FontWeight.Light, -0.3),
        headlineSmall = style(24, FontWeight.Medium, -0.2),
        titleLarge = style(21, FontWeight.SemiBold, -0.1),
        titleMedium = style(16, FontWeight.SemiBold, 0.0),
        titleSmall = style(14, FontWeight.SemiBold, 0.1),
        bodyLarge = style(16, FontWeight.Normal, 0.15),
        bodyMedium = style(14, FontWeight.Normal, 0.2),
        bodySmall = style(12, FontWeight.Normal, 0.3),
        labelLarge = style(14, FontWeight.Medium, 0.4),
        labelMedium = style(12, FontWeight.Medium, 0.5),
        labelSmall = style(11, FontWeight.Medium, 0.5),
    )
}

/** Fades the left and right edges of a horizontally scrolling row, hinting that there is more to reach. */
fun Modifier.edgeFade(width: androidx.compose.ui.unit.Dp = 20.dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 4f)
        val f = w / size.width
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to Color.Transparent, f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
