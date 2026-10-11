package com.snapkode.nivolauncher.ui.pagelet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.IconButton
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.snapkode.nivolauncher.pagelet.MoveSection
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.move.HeartStats
import com.snapkode.nivolauncher.pagelet.move.MoveData
import com.snapkode.nivolauncher.pagelet.move.MoveFormat
import com.snapkode.nivolauncher.pagelet.move.MoveResult
import com.snapkode.nivolauncher.pagelet.move.MoveSource
import com.snapkode.nivolauncher.ui.design.LocalMotion
import kotlinx.coroutines.delay
import java.util.Locale

/** Steps, sleep and heart rate from Health Connect, as three calm cards. */
@Composable
fun MovePage(config: PageletConfig, onOptions: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val source = remember { MoveSource(context.applicationContext) }
    var result by remember { mutableStateOf<MoveResult?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(MoveSource.requestContract()) { reload++ }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reload++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    // Fresh numbers when the page comes back, and every five minutes while it is on screen.
    LaunchedEffect(reload) {
        result = source.read(System.currentTimeMillis())
        delay(5 * 60_000L); reload++
    }
    MoveBody(config, result, onOptions, onAsk = { ask.launch(MoveSource.permissions) }, onRetry = { reload++ })
}

/** The Move page for a given result: the loader above feeds it; the screenshot test feeds it fixed data. */
@Composable
internal fun MoveBody(config: PageletConfig, result: MoveResult?, onOptions: () -> Unit, onAsk: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("MOVE · " + java.text.SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(java.util.Date()).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold), color = readableOn(StepsGreen, scheme.surface))
                Text("Your day in motion", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), color = scheme.onSurface)
            }
            IconButton(onClick = onOptions) { Icon(Icons.Outlined.Tune, "Customise Move", tint = scheme.onSurfaceVariant) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val r = result) {
                null -> Unit
                MoveResult.NotInstalled -> AskCard("Get Health Connect", "Steps, sleep and heart rate come from Health Connect, which isn't installed on this phone.", "Get Health Connect") { openHealthConnectStore(context) }
                MoveResult.Unsupported -> EmptyState(Icons.Filled.DirectionsWalk, "Not available here", "This phone can't run Health Connect.")
                is MoveResult.Failed -> AskCard("Couldn't read your health data", "Health Connect didn't answer. Try again in a moment.", "Try again") { onRetry() }
                is MoveResult.Ready -> {
                    val d = r.data
                    val wantsSteps = config.has(MoveSection.STEPS); val wantsSleep = config.has(MoveSection.SLEEP); val wantsHeart = config.has(MoveSection.HEART)
                    val missing = listOfNotNull("steps".takeIf { wantsSteps && !d.canReadSteps }, "sleep".takeIf { wantsSleep && !d.canReadSleep }, "heart rate".takeIf { wantsHeart && !d.canReadHeart })
                    if (missing.isNotEmpty()) AskCard("Connect Health Connect", "To show your " + missing.joinToString(", ") + ". Read-only; it stays on this phone.", "Connect") { onAsk() }
                    if ((wantsSteps && d.canReadSteps) || (wantsSleep && d.canReadSleep)) RingsHero(d, config, wantsSteps && d.canReadSteps, wantsSleep && d.canReadSleep)
                    if (wantsSteps && d.canReadSteps && d.week != null) WeekCard(d.week, config.stepGoal.toLong())
                    if (wantsSleep && d.canReadSleep) SleepCard(d.sleepMin, config.sleepGoalMin)
                    if (wantsHeart && d.canReadHeart) HeartCard(d.heart)
                    if (!wantsSteps && !wantsSleep && !wantsHeart) EmptyState(Icons.Filled.DirectionsWalk, "Nothing switched on", "Choose what to show in this Pagelet's settings.")
                }
            }
        }
    }
}

private val StepsGreen = Color(0xFF22C55E)
private val SleepIndigo = Color(0xFF7C6CF6)
private val HeartRed = Color(0xFFFF4D5E)

private fun readableOn(c: Color, bg: Color): Color = if (bg.luminance() > 0.5f) lerp(c, Color.Black, 0.35f) else lerp(c, Color.White, 0.15f)

/** Rings grow to their value the first time the page is seen. */
@Composable
private fun rememberGrow(): Float {
    val reduced = LocalMotion.current.reduced
    val a = remember { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))) }
    return a.value
}

/** Two rings, steps outside and sleep inside, with the steps number in the middle and the details beside them. */
@Composable
private fun RingsHero(d: MoveData, config: PageletConfig, showSteps: Boolean, showSleep: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val grow = rememberGrow()
    val steps = d.steps ?: 0L
    val stepP = MoveFormat.progress(steps, config.stepGoal.toLong())
    val sleepP = d.sleepMin?.let { MoveFormat.progress(it.toLong(), config.sleepGoalMin.toLong()) } ?: 0f
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, RoundedCornerShape(32.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 16.dp.toPx(); val gap = 4.dp.toPx()
                    fun ring(i: Int, color: Color, p: Float) {
                        val inset = sw / 2f + i * (sw + gap)
                        val box = Size(size.width - 2 * inset, size.height - 2 * inset)
                        drawArc(color.copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(sw))
                        if (p > 0f) drawArc(
                            Brush.sweepGradient(listOf(lerp(color, Color.White, 0.25f), color, color), center = Offset(size.width / 2f, size.height / 2f)),
                            -90f, 360f * p * grow, false, Offset(inset, inset), box, style = Stroke(sw, cap = StrokeCap.Round),
                        )
                    }
                    var i = 0
                    if (showSteps) ring(i++, StepsGreen, stepP)
                    if (showSleep) ring(i, SleepIndigo, sleepP)
                }
                if (showSteps) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DirectionsWalk, null, tint = StepsGreen, modifier = Modifier.size(18.dp))
                    Text("${(stepP * 100 * grow).roundToInt()}%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
                }
            }
            Column(Modifier.padding(start = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (showSteps) Legend(StepsGreen, "%,d".format((steps * grow).toLong()), "of %,d steps".format(config.stepGoal))
                if (showSleep) Legend(SleepIndigo, d.sleepMin?.let { MoveFormat.duration(it) } ?: "—", "of ${MoveFormat.duration(config.sleepGoalMin)} sleep")
            }
        }
        if (showSteps && steps > 0) {
            Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.5f)))
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("%.1f km".format(MoveFormat.distanceKm(steps)), "distance, est.")
                MiniStat("${MoveFormat.kcal(steps)} kcal", "burned, est.")
                MiniStat(if (stepP >= 1f) "Done" else "%,d".format(config.stepGoal - steps), if (stepP >= 1f) "goal reached" else "steps to go")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 36.dp).background(color, RoundedCornerShape(2.dp)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Seven days of steps: bars against a dashed goal line, today in full colour with its number on top. */
@Composable
private fun WeekCard(week: List<Long>, goal: Long) {
    val scheme = MaterialTheme.colorScheme
    val grow = rememberGrow()
    val bars = MoveFormat.bars(week, goal)
    val top = maxOf(week.maxOrNull() ?: 0L, goal, 1L).toFloat()
    val avg = MoveFormat.average(week)
    val weekday = remember { java.text.SimpleDateFormat("EEEEE", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, RoundedCornerShape(28.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("This week", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface, modifier = Modifier.weight(1f))
            if (avg != null) Text("avg %,d a day".format(avg), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        val track = StepsGreen.copy(alpha = 0.22f); val goalC = scheme.onSurfaceVariant.copy(alpha = 0.5f)
        Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(120.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = bars.size; val gap = 10.dp.toPx()
                val bw = (size.width - gap * (n - 1)) / n
                val gy = size.height * (1f - goal / top)
                drawLine(goalC, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
                bars.forEachIndexed { i, f ->
                    val h = (size.height * f * grow).coerceAtLeast(6.dp.toPx())
                    val x = i * (bw + gap)
                    val today = i == n - 1
                    val met = week[i] >= goal && goal > 0
                    drawRoundRect(
                        if (today) Brush.verticalGradient(listOf(lerp(StepsGreen, Color.White, 0.2f), StepsGreen), startY = size.height - h, endY = size.height)
                        else Brush.verticalGradient(listOf(if (met) StepsGreen.copy(alpha = 0.6f) else track, if (met) StepsGreen.copy(alpha = 0.6f) else track)),
                        Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2.6f),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth()) {
            week.indices.forEach { i ->
                val day = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, i - (week.size - 1)) }.time
                Text(
                    weekday.format(day), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (i == week.lastIndex) FontWeight.Bold else FontWeight.Normal),
                    color = if (i == week.lastIndex) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Last night, on a small night sky of its own. */
@Composable
private fun SleepCard(sleepMin: Int?, goalMin: Int) {
    val grow = rememberGrow()
    val night = Brush.linearGradient(listOf(Color(0xFF1B1F4B), Color(0xFF2E2A6B), Color(0xFF4B3C86)))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(night)) {
        // Stars and a crescent moon.
        Canvas(Modifier.matchParentSize()) {
            val stars = listOf(0.62f to 0.18f, 0.78f to 0.34f, 0.9f to 0.14f, 0.7f to 0.6f, 0.95f to 0.52f, 0.55f to 0.42f, 0.84f to 0.78f)
            stars.forEachIndexed { i, (x, y) -> drawCircle(Color.White.copy(alpha = 0.35f + (i % 3) * 0.2f), (1.2f + (i % 2)).dp.toPx(), Offset(size.width * x, size.height * y)) }
            val c = Offset(size.width * 0.84f, size.height * 0.3f); val r = 22.dp.toPx()
            drawCircle(Color(0xFFFFE9B0).copy(alpha = 0.18f), r * 1.9f, c)
            val disc = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
            val bite = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.35f, c.y - r * 1.2f, c.x + r * 1.5f, c.y + r * 0.7f)) }
            drawPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, bite), Color(0xFFFFE9B0))
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, null, tint = Color(0xFFC9C2FF), modifier = Modifier.size(16.dp))
                Text("LAST NIGHT", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = Color(0xFFC9C2FF))
            }
            if (sleepMin == null) {
                Text("No sleep recorded", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Wear your watch to bed, or log sleep in a sleep app that writes to Health Connect.", Modifier.padding(top = 4.dp).fillMaxWidth(0.75f), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            } else {
                Text(MoveFormat.duration(sleepMin), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light), color = Color.White)
                val p = MoveFormat.progress(sleepMin.toLong(), goalMin.toLong())
                Box(Modifier.padding(top = 12.dp).fillMaxWidth(0.7f).height(6.dp).background(Color.White.copy(alpha = 0.16f), CircleShape)) {
                    Box(Modifier.fillMaxWidth(p * grow).height(6.dp).background(Brush.horizontalGradient(listOf(Color(0xFF9D8CFF), Color(0xFFC9C2FF))), CircleShape))
                }
                Text(MoveFormat.sleepNote(sleepMin, goalMin) + "  Goal ${MoveFormat.duration(goalMin)}.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

/** Heart rate with a live trace beating at the last reading, and the day's range with where average and now sit in it. */
@Composable
private fun HeartCard(h: HeartStats?) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, RoundedCornerShape(28.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = HeartRed, modifier = Modifier.size(16.dp))
            Text("HEART RATE", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = readableOn(HeartRed, scheme.surface))
        }
        if (h == null) {
            Text("No readings in the last day.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            return@Column
        }
        val bpm = (h.latest ?: h.average ?: 60).coerceIn(30, 220)
        val beatMs = 60_000 / bpm
        val t by rememberInfiniteTransition(label = "ecg").animateFloat(0f, 1f, infiniteRepeatable(tween(beatMs * 3, easing = LinearEasing)), label = "ecgT")
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text((h.latest ?: h.average).toString(), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light), color = scheme.onSurface)
            Text(" bpm", Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            // The trace: three heartbeats across, scrolling.
            Canvas(Modifier.weight(1f).height(56.dp).padding(bottom = 6.dp)) {
                val w = size.width; val mid = size.height * 0.6f
                fun yAt(u: Float): Float {
                    val f = ((u % 1f) + 1f) % 1f
                    return when {
                        f < 0.40f -> 0f
                        f < 0.45f -> -0.12f * sin((f - 0.40f) / 0.05f * PI.toFloat())
                        f < 0.50f -> 0f
                        f < 0.53f -> 0.15f * ((f - 0.50f) / 0.03f)
                        f < 0.57f -> 0.15f - 1.15f * ((f - 0.53f) / 0.04f)
                        f < 0.61f -> -1f + 1.3f * ((f - 0.57f) / 0.04f)
                        f < 0.64f -> 0.3f - 0.3f * ((f - 0.61f) / 0.03f)
                        f < 0.80f -> -0.2f * sin((f - 0.64f) / 0.16f * PI.toFloat())
                        else -> 0f
                    } * size.height * 0.5f
                }
                val shift = if (reduced) 0f else t * 3f
                val p = androidx.compose.ui.graphics.Path()
                var x = 0f
                while (x <= w) {
                    val y = mid + yAt(x / w * 3f + shift)
                    if (x == 0f) p.moveTo(x, y) else p.lineTo(x, y)
                    x += 2f
                }
                drawPath(p, Brush.horizontalGradient(listOf(HeartRed.copy(alpha = 0f), HeartRed, HeartRed)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        // The day's range: a bar from lowest to highest, with average and now marked on it.
        val lo = h.min; val hi = h.max
        if (lo != null && hi != null && hi > lo) {
            val track = scheme.surfaceContainerHighest; val dot = scheme.onSurface
            Canvas(Modifier.padding(top = 16.dp).fillMaxWidth().height(16.dp)) {
                val cy = size.height / 2f
                drawLine(track, Offset(0f, cy), Offset(size.width, cy), 8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Brush.horizontalGradient(listOf(Color(0xFFFFB3BA), HeartRed)), Offset(0f, cy), Offset(size.width, cy), 8.dp.toPx(), cap = StrokeCap.Round)
                h.average?.let { a -> val x = size.width * MoveFormat.position(a, lo, hi); drawCircle(Color.White, 7.dp.toPx(), Offset(x, cy)); drawCircle(dot, 4.dp.toPx(), Offset(x, cy)) }
                h.latest?.let { n -> val x = size.width * MoveFormat.position(n, lo, hi); drawCircle(HeartRed, 7.dp.toPx(), Offset(x, cy)); drawCircle(Color.White, 3.dp.toPx(), Offset(x, cy)) }
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HeartStat("Lowest", h.min); HeartStat("Average", h.average, centered = true); HeartStat("Highest", h.max, end = true)
        }
    }
}

@Composable
private fun HeartStat(label: String, value: Int?, centered: Boolean = false, end: Boolean = false) {
    Column(horizontalAlignment = if (end) Alignment.End else if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(value?.toString() ?: "–", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
