package com.snapkode.nivolauncher.ui.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A scalloped outline: a circle whose radius swells and dips [lobes] times. [depth] is how far (0..0.3)
 * the dips cut in. This is the family behind Material 3 Expressive's Cookie, Flower and Sunny shapes.
 */
class ScallopShape(private val lobes: Int, private val depth: Float, private val rotationDeg: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val steps = 240
        for (i in 0..steps) {
            val t = i * 2.0 * Math.PI / steps
            val radius = r * (1f - depth * (1f - cos(lobes * t).toFloat()) / 2f)
            val a = t + Math.toRadians(rotationDeg.toDouble())
            val x = cx + (radius * cos(a)).toFloat()
            val y = cy + (radius * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** The expressive shape vocabulary from the design system. */
object NivoShapes {
    val Cookie9 = ScallopShape(lobes = 9, depth = 0.09f)
    val Cookie4 = ScallopShape(lobes = 4, depth = 0.16f, rotationDeg = 45f)
    val Flower6 = ScallopShape(lobes = 6, depth = 0.12f)
    val Sunny12 = ScallopShape(lobes = 12, depth = 0.06f)
    val Circle: Shape = CircleShape
    val Arch: Shape = RoundedCornerShape(CornerSize(50), CornerSize(50), CornerSize(24.dp), CornerSize(24.dp))
    val Pill: Shape = RoundedCornerShape(percent = 50)
    val Card: Shape = RoundedCornerShape(NivoRadius.card)
    val Sheet: Shape = RoundedCornerShape(topStart = NivoRadius.sheet, topEnd = NivoRadius.sheet)
    /** Large outer corners, tight inner ones: used for stacked list groups. */
    val GroupTop: Shape = RoundedCornerShape(topStart = NivoRadius.card, topEnd = NivoRadius.card, bottomStart = NivoRadius.inner, bottomEnd = NivoRadius.inner)
    val GroupMiddle: Shape = RoundedCornerShape(NivoRadius.inner)
    val GroupBottom: Shape = RoundedCornerShape(topStart = NivoRadius.inner, topEnd = NivoRadius.inner, bottomStart = NivoRadius.card, bottomEnd = NivoRadius.card)
    val GroupSingle: Shape = RoundedCornerShape(NivoRadius.card)

    /** Each row is its own card now; the index arguments are kept so callers don't change. */
    @Suppress("UNUSED_PARAMETER")
    fun group(index: Int, count: Int): Shape = GroupSingle
}
