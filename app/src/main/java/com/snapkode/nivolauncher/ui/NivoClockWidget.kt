package com.snapkode.nivolauncher.ui

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.snapkode.nivolauncher.data.ClockWeight
import com.snapkode.nivolauncher.ui.DigitGlyphs.ASPECT
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val GAP = 0.10f    // between the two digits of a pair, in digit heights
private const val COLON = 0.30f  // width of the colon block, in digit heights

/**
 * Nivo's clock widget, drawn straight onto the wallpaper. Each digit is a single line; when the time changes the line
 * itself bends from the old shape into the new one (an 8 opens into a 9), with a spring so it settles softly. The colon
 * breathes and a hairline sweeps the seconds. [weight] picks thin or heavy strokes.
 *
 * [onTap] runs for a plain tap; the caller decides when a tap is really the end of a long press and ignores it.
 */
@Composable
fun NivoClockWidget(weight: ClockWeight, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ink = LocalHomeTextColor.current

    // Ticks once a second, aligned to the real second boundary, and only while the launcher is on screen.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000L - (now % 1000L) + 15L)
            }
        }
    }

    val is24 = remember { DateFormat.is24HourFormat(context) }
    val cal = remember(now / 1000L) { Calendar.getInstance().apply { timeInMillis = now } }
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val hour = if (is24) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val hourTens: Int? = if (is24 || hour >= 10) hour / 10 else null
    val meridiem = if (is24) "" else if (hour24 < 12) "AM" else "PM"
    val dateText = remember(now / 60_000L) {
        SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(Date(now)).uppercase(Locale.getDefault())
    }
    val textShadow = Shadow(Color(0x55000000), Offset(0f, 2f), 8f)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) {
        val compact = maxHeight < 118.dp
        val padding = if (compact) 8.dp else 12.dp
        val innerW = maxWidth - padding * 2
        val innerH = maxHeight - padding * 2

        // Size the numerals from the space. The row is four digits (each ASPECT*h wide plus a stroke), two gaps and a
        // colon block, so the widest it can be for a given stroke weight is solved exactly, and Bold never overflows.
        val strokeRatio = if (weight == ClockWeight.Bold) 0.15f else 0.055f
        val rowUnits = 4 * ASPECT + 2 * GAP + COLON + 4 * strokeRatio
        val byHeight = innerH * (if (compact) 0.78f else 0.5f)
        val byWidth = innerW / rowUnits
        val digitH: Dp = minOf(byHeight, byWidth).coerceIn(32.dp, 150.dp)
        val strokeW: Dp = digitH * strokeRatio

        val dateStyle = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp, shadow = textShadow)

        if (compact) {
            Row(Modifier.fillMaxSize().padding(horizontal = padding), verticalAlignment = Alignment.CenterVertically) {
                TimeRow(hourTens, hour % 10, minute, second, digitH, strokeW, ink)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (meridiem.isNotEmpty()) Meridiem(meridiem, ink)
                    Text(dateText.substringBefore(" ·"), style = dateStyle, color = ink.copy(alpha = 0.85f), maxLines = 1)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).drawBehind { drawRect(ink) })
                    Text(
                        dateText, style = dateStyle, color = ink.copy(alpha = 0.85f), maxLines = 1,
                        modifier = Modifier.padding(start = 10.dp, end = 8.dp).weight(1f),
                    )
                    if (meridiem.isNotEmpty()) Meridiem(meridiem, ink)
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimeRow(hourTens, hour % 10, minute, second, digitH, strokeW, ink)
                }
                SecondsLine(second, ink)
            }
        }
    }
}

/** "10:42": four morphing digits and a colon. The minutes sit a shade softer than the hours. */
@Composable
private fun TimeRow(hourTens: Int?, hourOnes: Int, minute: Int, second: Int, digitH: Dp, strokeW: Dp, ink: Color) {
    val gap = digitH * GAP
    Row(verticalAlignment = Alignment.CenterVertically) {
        MorphDigit(hourTens, digitH, strokeW, ink)
        Spacer(Modifier.width(gap))
        MorphDigit(hourOnes, digitH, strokeW, ink)
        Colon(ink, digitH, strokeW, second)
        MorphDigit(minute / 10, digitH, strokeW, ink.copy(alpha = 0.7f))
        Spacer(Modifier.width(gap))
        MorphDigit(minute % 10, digitH, strokeW, ink.copy(alpha = 0.7f))
    }
}

/**
 * One digit drawn as a line. When [digit] changes, the points of the current shape glide to the points of the new
 * one. If it changes again mid-way, the new move starts from wherever the line currently is, so it never jumps.
 * A null digit (the unused tens place of a 12-hour clock) folds away and gives its width back.
 */
@Composable
private fun MorphDigit(digit: Int?, height: Dp, strokeWidth: Dp, color: Color) {
    val initial = DigitGlyphs.points[digit ?: 0]
    var from by remember { mutableStateOf(initial) }
    var to by remember { mutableStateOf(initial) }
    val morph = remember { Animatable(1f) }
    val shown = remember { Animatable(if (digit == null) 0f else 1f) }

    LaunchedEffect(digit) {
        if (digit == null) {
            shown.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        } else {
            launch { shown.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            val target = DigitGlyphs.points[digit]
            if (target !== to) {
                from = DigitGlyphs.blend(from, to, morph.value.coerceIn(0f, 1f))
                to = target
                morph.snapTo(0f)
                morph.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 110f))
            }
        }
    }

    val glyphW = height * ASPECT
    val canvasW = glyphW + strokeWidth
    val canvasH = height + strokeWidth
    Box(
        Modifier.width(canvasW * shown.value).height(canvasH),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .requiredSize(canvasW, canvasH)
                .graphicsLayer {
                    alpha = shown.value
                    val s = 0.8f + 0.2f * shown.value
                    scaleX = s; scaleY = s
                },
        ) {
            val t = morph.value
            val sw = strokeWidth.toPx()
            val w = glyphW.toPx()
            val h = height.toPx()
            val path = Path()
            val n = DigitGlyphs.N
            for (i in 0 until n) {
                val x = sw / 2f + (from[2 * i] + (to[2 * i] - from[2 * i]) * t) * w
                val y = sw / 2f + (from[2 * i + 1] + (to[2 * i + 1] - from[2 * i + 1]) * t) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // A faint offset copy underneath lifts the line off a busy wallpaper.
            translate(0f, 1.5.dp.toPx()) { drawPath(path, Color.Black.copy(alpha = 0.18f), style = style) }
            drawPath(path, color, style = style)
        }
    }
}

/** Two dots on the digits' optical centre line that breathe in and out, in step with the seconds. */
@Composable
private fun Colon(color: Color, digitH: Dp, strokeW: Dp, second: Int) {
    // One short breath per second tick, then still: an endless animation kept the whole launcher drawing every frame.
    val breath = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(second) {
        breath.animateTo(0.35f, tween(220, easing = FastOutSlowInEasing))
        breath.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    val alpha by breath.asState()
    Canvas(Modifier.requiredSize(digitH * COLON, digitH + strokeW)) {
        val h = digitH.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = maxOf(strokeW.toPx() * 0.55f, h * 0.036f)
        val dy = h * 0.17f
        drawCircle(Color.Black.copy(alpha = 0.18f * alpha), r, Offset(cx, cy - dy + 1.5.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.18f * alpha), r, Offset(cx, cy + dy + 1.5.dp.toPx()))
        drawCircle(color.copy(alpha = alpha), r, Offset(cx, cy - dy))
        drawCircle(color.copy(alpha = alpha), r, Offset(cx, cy + dy))
    }
}

@Composable
private fun Meridiem(text: String, ink: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
        color = ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, ink.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A hairline track with a bright head that glides through the minute, restarting softly at zero. */
@Composable
private fun SecondsLine(second: Int, ink: Color) {
    val progress = remember { Animatable(second / 60f) }
    LaunchedEffect(second) {
        if (second == 0) progress.snapTo(0f)
        progress.animateTo((second + 1) / 60f, tween(1000, easing = LinearEasing))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .drawBehind {
                val y = size.height / 2f
                val w = 1.5.dp.toPx()
                drawLine(ink.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = w, cap = StrokeCap.Round)
                val x = size.width * progress.value
                if (x > 1f) {
                    val tail = (x - size.width * 0.28f).coerceAtLeast(0f)
                    drawLine(
                        Brush.horizontalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = 0.9f)), startX = tail, endX = x),
                        Offset(tail, y), Offset(x, y), strokeWidth = w, cap = StrokeCap.Round,
                    )
                    drawCircle(ink.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = Offset(x, y))
                    drawCircle(ink, radius = 2.4.dp.toPx(), center = Offset(x, y))
                }
            },
    )
}

/** Opens the system clock (alarms tab) on tap. */
fun openClockApp(context: Context) {
    runCatching {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
