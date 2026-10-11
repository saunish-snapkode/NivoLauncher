package com.snapkode.nivolauncher.ui.pagelet.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the weather icon shows: derived from MET Norway's `symbol_code` and whether it is night. Pure. */
enum class GlyphKind { Sun, Moon, PartlySun, PartlyMoon, Cloud, Rain, Storm, Snow, Fog }

fun glyphFor(symbolCode: String, night: Boolean): GlyphKind {
    val code = symbolCode.lowercase().removeSuffix("_day").removeSuffix("_night").removeSuffix("_polartwilight")
    return when {
        code.contains("thunder") -> GlyphKind.Storm
        code.contains("snow") -> GlyphKind.Snow
        code.contains("rain") || code.contains("sleet") || code.contains("showers") -> GlyphKind.Rain
        code == "fog" -> GlyphKind.Fog
        code == "clearsky" || code == "fair" -> if (night) GlyphKind.Moon else GlyphKind.Sun
        code == "partlycloudy" -> if (night) GlyphKind.PartlyMoon else GlyphKind.PartlySun
        else -> GlyphKind.Cloud
    }
}

/** The weather as a small vector illustration, drawn at any size from paths. */
@Composable
fun WeatherGlyph(kind: GlyphKind, sunColor: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(kind, sunColor) }
}

private val CloudLight = Color(0xFFF4F7FC)
private val CloudShade = Color(0xFFC9D3E6)
private val CloudDark = Color(0xFF8F9BB5)
private val RainBlue = Color(0xFF8EC5FF)
private val BoltYellow = Color(0xFFFFD84D)

private fun DrawScope.sun(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(Brush.radialGradient(0f to color.copy(alpha = 0.45f), 1f to Color.Transparent, center = Offset(cx, cy), radius = r * 2.1f), r * 2.1f, Offset(cx, cy))
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        drawLine(color, Offset(cx + cos(a) * r * 1.35f, cy + sin(a) * r * 1.35f), Offset(cx + cos(a) * r * 1.8f, cy + sin(a) * r * 1.8f), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(0f to Color.White, 1f to color, center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
}

private fun DrawScope.moon(cx: Float, cy: Float, r: Float) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r * 0.35f, cy - r * 1.15f, cx + r * 1.45f, cy + r * 0.65f)) }
    val crescent = Path.combine(PathOperation.Difference, disc, bite)
    drawCircle(Brush.radialGradient(0f to Color(0x55BFD0FF), 1f to Color.Transparent, center = Offset(cx, cy), radius = r * 2f), r * 2f, Offset(cx, cy))
    drawPath(crescent, Color(0xFFEFF3FF))
}

/** A soft cloud: three round humps on a flat base, lit from the top. [x],[y] is its centre, [w] its width. */
private fun DrawScope.cloud(x: Float, y: Float, w: Float, top: Color = CloudLight, bottom: Color = CloudShade) {
    val h = w * 0.58f
    val left = x - w / 2f
    val base = y + h / 2f
    val p = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(left + w * 0.04f, base - h * 0.58f, left + w * 0.40f, base))
        addOval(androidx.compose.ui.geometry.Rect(left + w * 0.22f, base - h * 1.0f, left + w * 0.70f, base - h * 0.04f))
        addOval(androidx.compose.ui.geometry.Rect(left + w * 0.52f, base - h * 0.66f, left + w * 0.96f, base))
        addRoundRect(androidx.compose.ui.geometry.RoundRect(left + w * 0.1f, base - h * 0.4f, left + w * 0.9f, base, h * 0.2f, h * 0.2f))
    }
    drawPath(p, Brush.verticalGradient(0f to top, 1f to bottom, startY = base - h, endY = base))
}

private fun DrawScope.drops(x: Float, y: Float, w: Float, color: Color) {
    for (i in 0 until 3) {
        val dx = x - w * 0.22f + i * w * 0.22f
        drawLine(color, Offset(dx + w * 0.04f, y + i % 2 * w * 0.05f), Offset(dx - w * 0.03f, y + w * 0.17f + i % 2 * w * 0.05f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawGlyph(kind: GlyphKind, sunColor: Color) {
    val s = size.minDimension
    when (kind) {
        GlyphKind.Sun -> sun(s / 2f, s / 2f, s * 0.2f, sunColor)
        GlyphKind.Moon -> moon(s * 0.5f, s * 0.5f, s * 0.26f)
        GlyphKind.PartlySun -> { sun(s * 0.36f, s * 0.38f, s * 0.15f, sunColor); cloud(s * 0.56f, s * 0.62f, s * 0.7f) }
        GlyphKind.PartlyMoon -> { moon(s * 0.36f, s * 0.36f, s * 0.19f); cloud(s * 0.56f, s * 0.62f, s * 0.7f) }
        GlyphKind.Cloud -> cloud(s * 0.5f, s * 0.5f, s * 0.82f)
        GlyphKind.Rain -> { cloud(s * 0.5f, s * 0.38f, s * 0.78f, CloudShade, CloudDark); drops(s * 0.5f, s * 0.68f, s * 0.7f, RainBlue) }
        GlyphKind.Storm -> {
            cloud(s * 0.5f, s * 0.36f, s * 0.78f, CloudShade, CloudDark)
            val bolt = Path().apply {
                moveTo(s * 0.54f, s * 0.58f); lineTo(s * 0.40f, s * 0.80f); lineTo(s * 0.50f, s * 0.80f)
                lineTo(s * 0.44f, s * 0.97f); lineTo(s * 0.64f, s * 0.72f); lineTo(s * 0.53f, s * 0.72f); close()
            }
            drawPath(bolt, BoltYellow)
        }
        GlyphKind.Snow -> {
            cloud(s * 0.5f, s * 0.36f, s * 0.78f, CloudShade, CloudDark)
            for (i in 0 until 3) {
                val cx = s * (0.34f + i * 0.16f); val cy = s * (0.74f + (i % 2) * 0.08f); val r = s * 0.05f
                for (k in 0 until 3) {
                    val a = k * PI.toFloat() / 3f
                    drawLine(Color.White, Offset(cx - cos(a) * r, cy - sin(a) * r), Offset(cx + cos(a) * r, cy + sin(a) * r), strokeWidth = s * 0.022f, cap = StrokeCap.Round)
                }
            }
        }
        GlyphKind.Fog -> {
            cloud(s * 0.5f, s * 0.34f, s * 0.7f, CloudShade, CloudDark)
            val lines = listOf(0.62f to 0.74f, 0.74f to 0.58f, 0.86f to 0.66f)
            lines.forEach { (y, len) ->
                drawLine(CloudLight.copy(alpha = 0.85f), Offset(s * (0.5f - len / 2f), s * y), Offset(s * (0.5f + len / 2f), s * y), strokeWidth = s * 0.045f, cap = StrokeCap.Round)
            }
        }
    }
}
