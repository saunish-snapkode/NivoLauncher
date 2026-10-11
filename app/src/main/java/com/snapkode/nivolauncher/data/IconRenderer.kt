package com.snapkode.nivolauncher.data

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Draws launcher icons masked to the user's chosen [IconShape]. */
object IconRenderer {
    /** Background/foreground colours for themed (monochrome) icons. */
    data class ThemeColors(val background: Int, val foreground: Int)

    fun render(icon: Drawable, size: Int, shape: IconShape, theme: ThemeColors? = null, wrapLegacy: Boolean = false): Bitmap {
        // Adaptive icons exist from Android 8; older phones only ever see plain drawables.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            return renderAdaptive(icon, size, shape, theme)
        }
        if (wrapLegacy && shape != IconShape.Original) return wrapLegacyIcon(icon, size, shape, theme)
        return icon.toBitmap(size, size)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderAdaptive(icon: AdaptiveIconDrawable, size: Int, shape: IconShape, theme: ThemeColors?): Bitmap {
        // The monochrome layer used for themed icons arrived with Android 13.
        val mono = if (theme != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) icon.monochrome else null
        if (mono != null && theme != null) return renderThemed(mono, size, shape, theme)
        if (shape == IconShape.Original) return icon.toBitmap(size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(maskPath(shape, size.toFloat()))
        // Adaptive layers are 108dp with the 72dp keyline in the middle; overscan by 1/6 each side.
        val inset = (size / 6f).toInt()
        icon.background?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(canvas) }
        icon.foreground?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(canvas) }
        return bitmap
    }

    /** Puts an old-style square icon on a plain backing so it matches the chosen shape. */
    private fun wrapLegacyIcon(icon: Drawable, size: Int, shape: IconShape, theme: ThemeColors?): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(maskPath(shape, size.toFloat()))
        canvas.drawColor(theme?.background ?: 0xFFF5F5F5.toInt())
        val inset = (size * 0.17f).toInt()
        icon.setBounds(inset, inset, size - inset, size - inset)
        icon.draw(canvas)
        return bitmap
    }

    private fun renderThemed(mono: Drawable, size: Int, shape: IconShape, theme: ThemeColors): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(maskPath(if (shape == IconShape.Original) IconShape.Squircle else shape, size.toFloat()))
        canvas.drawColor(theme.background)
        val inset = (size / 6f).toInt()
        mono.mutate().apply {
            setTint(theme.foreground)
            setBounds(-inset, -inset, size + inset, size + inset)
            draw(canvas)
        }
        return bitmap
    }

    fun maskPath(shape: IconShape, s: Float): Path = Path().apply {
        when (shape) {
            IconShape.Circle, IconShape.Original -> addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            IconShape.RoundedSquare -> addRoundRect(RectF(0f, 0f, s, s), s * 0.22f, s * 0.22f, Path.Direction.CW)
            IconShape.Teardrop -> {
                val big = s / 2
                val small = s * 0.12f
                // Order: top-left, top-right, bottom-right, bottom-left (x/y per corner).
                val radii = floatArrayOf(big, big, big, big, small, small, big, big)
                addRoundRect(RectF(0f, 0f, s, s), radii, Path.Direction.CW)
            }
            IconShape.Squircle -> {
                val n = 4.0
                val steps = 72
                for (i in 0..steps) {
                    val t = 2 * Math.PI * i / steps
                    val c = cos(t)
                    val si = sin(t)
                    val x = s / 2 + s / 2 * sign(c).toFloat() * abs(c).pow(2 / n).toFloat()
                    val y = s / 2 + s / 2 * sign(si).toFloat() * abs(si).pow(2 / n).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }
}
