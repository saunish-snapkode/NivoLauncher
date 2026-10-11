package com.snapkode.nivolauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.snapkode.nivolauncher.ui.design.NivoShapes
import com.snapkode.nivolauncher.ui.design.hsl
import com.snapkode.nivolauncher.ui.design.hueOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.snapkode.nivolauncher.ui.design.NivoSpace
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.snapkode.nivolauncher.data.ClockStyle
import com.snapkode.nivolauncher.data.LauncherSettings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The signature Nivo clock. Ticks once a minute (once a second only when seconds are shown) and
 * stops entirely while the launcher is in the background, so it costs nothing when idle.
 */
@Composable
fun NivoClock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    if (settings.clockStyle == ClockStyle.Off) return
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(settings.clockSeconds, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val step = if (settings.clockSeconds && settings.clockStyle != ClockStyle.Compact) 1000L else 60_000L
            while (true) {
                now = System.currentTimeMillis()
                delay(step - now % step) // wake exactly on the next minute / second boundary
            }
        }
    }

    val date = Date(now)
    val is24 = DateFormat.is24HourFormat(context)
    val pattern = when {
        is24 && settings.clockSeconds -> "HH:mm:ss"
        is24 -> "HH:mm"
        settings.clockSeconds -> "h:mm:ss"
        else -> "h:mm"
    }
    val time = SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    val meridiem = if (is24) "" else SimpleDateFormat("a", Locale.getDefault()).format(date)
    val dateLine = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(date)
    val shortDate = dateLine

    // Honour the user's font size, but stop the giant numerals growing past what the screen can hold.
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val clockCap = if (fontScale > 1.15f) 1.15f / fontScale else 1f
    val color = LocalHomeTextColor.current
    val scheme = MaterialTheme.colorScheme
    // Over a light-on-dark wallpaper the seed colour is lifted so the hour numerals stay vivid.
    val accent = if (color == Color.White) hsl(hueOf(scheme.primary), 1f, 0.83f) else scheme.primary
    val shadow = if (color == Color.White) Shadow(Color(0x66000000), Offset(0f, 3f), 10f) else null
    val family = LocalAppFont.current ?: FontFamily.SansSerif
    // Tabular figures: every digit is the same width, so the numerals never shuffle sideways as the time ticks over.
    fun style(size: Int, weight: FontWeight, tracking: Double = 0.0, c: Color = color, line: Double = 1.05) =
        TextStyle(
            fontFamily = family, fontSize = size.sp, fontWeight = weight, letterSpacing = tracking.sp, color = c,
            shadow = shadow, lineHeight = (size * line).sp, fontFeatureSettings = "tnum",
        )
    // One spoken sentence for the whole clock (no seconds, so it doesn't change under a screen reader every tick).
    val spoken = buildString {
        if (settings.clockGreeting) append(greeting(now)).append(", ")
        append(SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(date))
        append(", ").append(dateLine)
    }

    val hh = time.substringBefore(':')
    val rest = time.substringAfter(':')
    val mm = rest.take(2)
    val tail = rest.drop(2) // ":ss" when seconds are on

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = NivoSpace.xl, vertical = NivoSpace.xs)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        if (settings.clockGreeting) {
            Text(greeting(now), style = style(15, FontWeight.Medium, 0.4, color.copy(alpha = 0.85f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (settings.clockStyle) {
            ClockStyle.Hero -> {
                // Stacked numerals: heavy hours in the seed colour over hairline minutes.
                val size = (128 * clockCap).toInt()
                Text(hh, style = style(size, FontWeight(600), -4.0, accent, 0.86), maxLines = 1, modifier = Modifier.padding(top = NivoSpace.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(mm + tail, style = style(size, FontWeight.Thin, -4.0, color, 0.86))
                    if (meridiem.isNotEmpty()) Text(meridiem, style = style(18, FontWeight.Medium, 0.0, color.copy(alpha = 0.8f)), maxLines = 1, modifier = Modifier.padding(start = NivoSpace.s, bottom = 28.dp))
                }
            }
            ClockStyle.Minimal -> {
                // A tonal pill with a thin time and a dotted colon.
                val size = (104 * clockCap).toInt()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = NivoSpace.s)
                        .background(scheme.primaryContainer, NivoShapes.Pill)
                        .padding(vertical = NivoSpace.m),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val on = scheme.onPrimaryContainer
                    Text(hh, style = style(size, FontWeight.Thin, -3.0, on, 1.0).copy(shadow = null))
                    ColonDots(scheme.primary, Modifier.padding(horizontal = 6.dp).size(width = (size * 0.12f).dp, height = (size * 0.38f).dp))
                    Text(mm, style = style(size, FontWeight.Thin, -3.0, on, 1.0).copy(shadow = null))
                    if (meridiem.isNotEmpty()) Text(meridiem, style = style(16, FontWeight.Medium, 0.0, on.copy(alpha = 0.8f)).copy(shadow = null), modifier = Modifier.padding(start = 6.dp, top = 24.dp))
                }
            }
            ClockStyle.Compact -> {
                Text(
                    "$time${if (meridiem.isNotEmpty()) " $meridiem" else ""}  ·  $shortDate",
                    style = style(22, FontWeight.Normal, 0.0), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            ClockStyle.Analog -> {
                AnalogFace(now, settings.clockSeconds, scheme, Modifier.size(148.dp))
            }
            ClockStyle.Off -> Unit
        }
        if (settings.clockStyle != ClockStyle.Compact && !settings.smartspace) {
            Text(
                dateLine, style = style(24, FontWeight.Normal, 0.0, color), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = NivoSpace.xs),
            )
        }
    }
}

/** Two stacked dots standing in for the colon. */
@Composable
private fun ColonDots(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.width / 2f
        drawCircle(color, r, Offset(size.width / 2f, size.height * 0.28f))
        drawCircle(color, r, Offset(size.width / 2f, size.height * 0.72f))
    }
}

/** An analog face in a cookie-shaped tile, with two soft hands and no ticks. */
@Composable
private fun AnalogFace(now: Long, seconds: Boolean, scheme: androidx.compose.material3.ColorScheme, modifier: Modifier) {
    val face = scheme.primaryContainer
    val hands = scheme.onPrimaryContainer
    val accent = scheme.primary
    Canvas(modifier.clip(NivoShapes.Cookie9)) {
        drawRect(face)
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val h = (c.get(Calendar.HOUR) + c.get(Calendar.MINUTE) / 60f)
        val m = c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60f
        val s = c.get(Calendar.SECOND).toFloat()
        val r = size.minDimension / 2f
        val center = Offset(r, r)
        fun hand(angleDeg: Float, length: Float, width: Float, tint: Color) {
            val a = Math.toRadians(angleDeg - 90.0)
            drawLine(tint, center, center + Offset((cos(a) * length).toFloat(), (sin(a) * length).toFloat()), strokeWidth = width, cap = StrokeCap.Round)
        }
        hand(h * 30f, r * 0.42f, r * 0.11f, hands)
        hand(m * 6f, r * 0.64f, r * 0.08f, hands.copy(alpha = 0.85f))
        if (seconds) hand(s * 6f, r * 0.7f, r * 0.03f, accent)
        drawCircle(hands, radius = r * 0.08f, center = center)
    }
}

private fun greeting(now: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}
