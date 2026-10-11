package com.snapkode.nivolauncher.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.ClockStyle
import com.snapkode.nivolauncher.data.DockStyle
import com.snapkode.nivolauncher.ui.design.LocalMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val STEPS = 6

/** True when Nivo is already Android's home app. */
fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
}

/**
 * First-run introduction: six short, skippable steps, each a picture first and a sentence second. Nothing sensitive is
 * asked for except the standard "make this your home app" prompt, and only when the person taps the button.
 */
@Composable
fun OnboardingFlow(viewModel: LauncherViewModel) {
    val settings by viewModel.settings.collectAsState()
    val apps by viewModel.apps.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val motion = LocalMotion.current
    val scheme = MaterialTheme.colorScheme
    BackHandler { if (step > 0) step-- }

    // Each step tints the page a little differently, and the tint glides between them.
    val tints = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer, scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)
    val tint by animateColorAsState(tints[step], tween(motion.duration(500)), label = "onboardingTint")

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to lerp(scheme.surface, tint, 0.7f), 0.55f to scheme.surface)), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                AnimatedContent(
                    targetState = step,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val forward = targetState > initialState
                        val ms = motion.duration(380)
                        (slideInHorizontally(tween(ms)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(ms))) togetherWith
                            (slideOutHorizontally(tween(ms)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(ms / 2)))
                    },
                    label = "onboardingStep",
                ) { current ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
                        when (current) {
                            0 -> WelcomeStep(apps.map { it.icon }.take(10))
                            1 -> LookStep(settings, viewModel)
                            2 -> HomeStep(settings, viewModel)
                            3 -> GesturesStep()
                            4 -> PageletsStep()
                            else -> DefaultStep()
                        }
                    }
                }
                BottomBar(
                    step = step,
                    onBack = { if (step > 0) step-- else viewModel.finishOnboarding() },
                    onNext = { if (step < STEPS - 1) step++ else viewModel.finishOnboarding() },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(step: Int, onBack: () -> Unit, onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        // Progress as segments that fill: you always know how far you are and how far is left.
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEPS) { i ->
                val fill by animateFloatAsState(if (i <= step) 1f else 0f, tween(LocalMotion.current.duration(400)), label = "seg$i")
                Box(Modifier.weight(1f).height(4.dp).background(scheme.outlineVariant.copy(alpha = 0.6f), CircleShape)) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(scheme.primary, CircleShape))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(if (step > 0) "Back" else "Skip", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNext, shape = CircleShape, modifier = Modifier.height(60.dp), contentPadding = ButtonDefaults.ContentPadding) {
                Text(
                    when { step == 0 -> "Get started"; step < STEPS - 1 -> "Continue"; else -> "Start using Nivo" },
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/** The words under each picture: a small overline, a headline, one sentence. */
@Composable
private fun StepText(overline: String, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Text(overline, style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold), color = scheme.primary)
    Text(
        title, Modifier.padding(top = 6.dp).semantics { heading() },
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.6).sp, lineHeight = 44.sp),
        color = scheme.onSurface,
    )
    Text(body, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
}

// ---- 1. Welcome ---------------------------------------------------------------------------------------------------

@Composable
private fun WelcomeStep(icons: List<ImageBitmap>) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { IconOrbit(icons) }
    Spacer(Modifier.height(12.dp))
    StepText("A CALMER HOME SCREEN", "Welcome to Nivo", "Your apps, your pages and your day — arranged quietly, coloured by your wallpaper.")
    Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(Icons.Outlined.Bolt, "Fast")
        Pill(Icons.Outlined.Lock, "Private")
        Pill(Icons.Outlined.Palette, "Yours")
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.background(scheme.surfaceContainerHigh, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = scheme.primary)
        Text(text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
    }
}

/** The phone's own app icons, slowly orbiting a glowing mark: the first thing you see is your own apps. */
@Composable
private fun IconOrbit(icons: List<ImageBitmap>) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val turn by rememberInfiniteTransition(label = "orbit").animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "orbitTurn")
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breatheScale")
    val angle = if (reduced) 20f else turn
    Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        val glow = scheme.primary
        val ring = scheme.outlineVariant
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.32f), Color.Transparent), c, size.minDimension * 0.42f), size.minDimension * 0.42f, c)
            drawCircle(ring.copy(alpha = 0.6f), size.minDimension * 0.30f, c, style = Stroke(1.dp.toPx()))
            drawCircle(ring.copy(alpha = 0.35f), size.minDimension * 0.46f, c, style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 7.dp.toPx()))))
        }
        // The Nivo mark itself, breathing as it does on the loading screen.
        com.snapkode.nivolauncher.ui.design.NivoMark(
            104.dp, Modifier.scale(if (reduced) 1f else breathe).graphicsLayer { shadowElevation = 18f; shape = RoundedCornerShape(26.dp) },
            breathing = true,
        )
        val inner = icons.take(4); val outer = icons.drop(4).take(6)
        // Two rings of icons turning in opposite directions.
        listOf(Triple(inner, 96.dp, 44.dp) to (0f to 1f), Triple(outer, 148.dp, 38.dp) to (30f to -0.6f)).forEach { (ring, motion) ->
            val (list, radius, size) = ring
            val (offsetDeg, dir) = motion
            list.forEachIndexed { i, icon ->
                val a = Math.toRadians((offsetDeg + dir * angle + i * 360.0 / list.size)).toFloat()
                Image(
                    icon, null,
                    Modifier.offset(x = radius * cos(a), y = radius * sin(a)).size(size)
                        .graphicsLayer { shadowElevation = 8f; shape = CircleShape; clip = true },
                )
            }
        }
    }
}

// ---- 2. Look -------------------------------------------------------------------------------------------------------

@Composable
private fun LookStep(settings: com.snapkode.nivolauncher.data.LauncherSettings, viewModel: LauncherViewModel) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ThemeSwatchPreview() }
    Spacer(Modifier.height(20.dp))
    StepText("STEP 1 OF 4", "Choose your look", "Light, dark or with the time of day — and an accent. It changes as you tap.")
    Spacer(Modifier.height(16.dp))
    ThemeModePicker(settings.themeMode) { v -> viewModel.updateSettings { it.copy(themeMode = v) } }
    AccentPicker("Accent", settings.accent) { v ->
        viewModel.updateSettings { it.copy(accent = v, colorStyle = com.snapkode.nivolauncher.data.ColorStyle.Nivo) }
    }
}

/** Three fanned cards in the current colours, so the choice is seen, not described. */
@Composable
private fun ThemeSwatchPreview() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.height(200.dp).width(280.dp), contentAlignment = Alignment.Center) {
        listOf(-14f to scheme.tertiaryContainer, 14f to scheme.secondaryContainer, 0f to scheme.surfaceContainerHigh).forEach { (rot, c) ->
            Box(
                Modifier.size(140.dp, 190.dp).rotate(rot).offset(x = (rot * 3).dp)
                    .clip(RoundedCornerShape(26.dp)).background(c).border(1.dp, scheme.outlineVariant, RoundedCornerShape(26.dp)),
            ) {
                if (rot == 0f) Column(Modifier.padding(16.dp)) {
                    Box(Modifier.size(40.dp).background(scheme.primary, CircleShape))
                    Box(Modifier.padding(top = 14.dp).size(90.dp, 10.dp).background(scheme.onSurface.copy(alpha = 0.75f), CircleShape))
                    Box(Modifier.padding(top = 8.dp).size(64.dp, 8.dp).background(scheme.onSurface.copy(alpha = 0.3f), CircleShape))
                    Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { Box(Modifier.size(24.dp).background(it, CircleShape)) }
                    }
                    Box(Modifier.padding(top = 14.dp).size(108.dp, 30.dp).background(scheme.primary, CircleShape))
                }
            }
        }
    }
}

// ---- 3. Home -------------------------------------------------------------------------------------------------------

@Composable
private fun HomeStep(settings: com.snapkode.nivolauncher.data.LauncherSettings, viewModel: LauncherViewModel) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HomeIllustration(settings) }
    Spacer(Modifier.height(20.dp))
    StepText("STEP 2 OF 4", "Make it home", "Pick a clock and a dock. The phone above is a live preview.")
    Spacer(Modifier.height(12.dp))
    ChipPicker("Clock", listOf(ClockStyle.Hero, ClockStyle.Minimal, ClockStyle.Compact, ClockStyle.Off), settings.clockStyle, { it.label }) { v ->
        viewModel.updateSettings { it.copy(clockStyle = v) }
    }
    ChipPicker("Dock", DockStyle.entries, settings.dockStyle, { it.label }) { v -> viewModel.updateSettings { it.copy(dockStyle = v) } }
}

// ---- 4. Gestures ---------------------------------------------------------------------------------------------------

private enum class Gesture(val title: String, val body: String) {
    Up("Swipe up", "All your apps, with search"),
    Down("Swipe down", "Your notifications"),
    Left("Swipe left", "The App Library, sorted for you"),
    Hold("Press and hold", "Edit home: wallpaper, widgets, Pagelets"),
    Pinch("Pinch", "See and arrange every page"),
}

@Composable
private fun GesturesStep() {
    Spacer(Modifier.height(12.dp))
    StepText("STEP 3 OF 4", "Move with ease", "Five gestures cover almost everything. Each one is shown on its little phone.")
    Spacer(Modifier.height(16.dp))
    Gesture.entries.forEachIndexed { i, g -> GestureRow(g, i) }
}

@Composable
private fun GestureRow(g: Gesture, index: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(scheme.surfaceContainerLow, RoundedCornerShape(22.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GestureDemo(g, index)
        Column(Modifier.padding(start = 16.dp)) {
            Text(g.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
            Text(g.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** A tiny phone with a fingertip acting out the gesture, on a loop. */
@Composable
private fun GestureDemo(g: Gesture, index: Int) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val t by rememberInfiniteTransition(label = "gesture").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, delayMillis = 0, easing = LinearEasing)), label = "gestureT")
    val p = if (reduced) 0.55f else ((t + index * 0.17f) % 1f)
    val frame = scheme.outline; val screen = scheme.surfaceContainerHighest; val finger = scheme.primary
    Canvas(Modifier.size(44.dp, 72.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(screen, Offset.Zero, size, CornerRadius(9.dp.toPx()))
        drawRoundRect(frame, Offset.Zero, size, CornerRadius(9.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        // Motion runs for the first 70% of the loop, then the finger lifts and rests.
        val m = (p / 0.7f).coerceIn(0f, 1f)
        val e = 1f - (1f - m) * (1f - m)
        val a = if (p < 0.75f) 1f else (1f - (p - 0.75f) / 0.25f).coerceIn(0f, 1f)
        val r = 6.dp.toPx()
        fun dot(o: Offset) { drawCircle(finger.copy(alpha = 0.25f * a), r * 1.8f, o); drawCircle(finger.copy(alpha = a), r, o) }
        fun trail(from: Offset, to: Offset) = drawLine(finger.copy(alpha = 0.3f * a), from, to, r * 1.2f, cap = StrokeCap.Round)
        when (g) {
            Gesture.Up -> { val s = Offset(w / 2, h * 0.82f); val n = Offset(w / 2, h * (0.82f - 0.6f * e)); trail(s, n); dot(n) }
            Gesture.Down -> { val s = Offset(w / 2, h * 0.12f); val n = Offset(w / 2, h * (0.12f + 0.55f * e)); trail(s, n); dot(n) }
            Gesture.Left -> { val s = Offset(w * 0.82f, h / 2); val n = Offset(w * (0.82f - 0.62f * e), h / 2); trail(s, n); dot(n) }
            Gesture.Hold -> {
                val c = Offset(w / 2, h / 2)
                drawCircle(finger.copy(alpha = 0.35f * (1f - m) * a), r * (1.2f + 2.4f * m), c, style = Stroke(2.dp.toPx()))
                dot(c)
            }
            Gesture.Pinch -> {
                val d = (0.3f - 0.18f * e) * h
                dot(Offset(w / 2 - d * 0.5f, h / 2 - d)); dot(Offset(w / 2 + d * 0.5f, h / 2 + d))
            }
        }
    }
}

// ---- 5. Pagelets ---------------------------------------------------------------------------------------------------

@Composable
private fun PageletsStep() {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp), contentAlignment = Alignment.TopCenter) { PageletFan() }
    Spacer(Modifier.height(20.dp))
    StepText("STEP 4 OF 4", "Pages that do more", "Pagelets fill a whole page: your day under a living sky, your agenda, the news, your music, your steps. Add one by pressing and holding the home screen.")
}

/** Five little page cards fanned out like a hand of cards. */
@Composable
private fun PageletFan() {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val sway by rememberInfiniteTransition(label = "fan").animateFloat(-1f, 1f, infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "fanSway")
    val cards = listOf(
        Triple("Today", Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFFCF956E), Color(0xFF0E0B0C))), Color.White),
        Triple("Agenda", Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surface)), scheme.onSurface),
        Triple("Headlines", Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surface)), scheme.onSurface),
        Triple("Music", Brush.verticalGradient(listOf(Color(0xFFFFB347), Color(0xFF2B5876))), Color.White),
        Triple("Move", Brush.verticalGradient(listOf(Color(0xFFD9F5E4), scheme.surface)), scheme.onSurface),
    )
    Box(Modifier.size(320.dp, 280.dp), contentAlignment = Alignment.TopCenter) {
        cards.forEachIndexed { i, (name, bg, fg) ->
            val k = i - 2
            val rot = k * 9f + if (reduced) 0f else sway * 1.5f
            Box(
                Modifier.offset(x = (k * 36).dp, y = (20 + kotlin.math.abs(k) * 10).dp).size(100.dp, 170.dp)
                    .graphicsLayer { rotationZ = rot; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.4f); shadowElevation = 10f; shape = RoundedCornerShape(20.dp); clip = true }
                    .background(bg).border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            ) {
                MiniPagelet(name, fg)
                Text(name, Modifier.align(Alignment.BottomStart).padding(10.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = fg)
            }
        }
    }
}

/** A hint of each Pagelet's content, drawn small. */
@Composable
private fun MiniPagelet(name: String, fg: Color) {
    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        when (name) {
            "Today" -> {
                drawArc(Color.White.copy(alpha = 0.4f), 180f, 180f, false, Offset(w * 0.1f, size.height * 0.3f), Size(w * 0.8f, w * 0.8f), style = Stroke(1.dp.toPx()))
                drawCircle(Color(0xFFFFE0A3), 5.dp.toPx(), Offset(w * 0.75f, size.height * 0.36f))
                drawRect(Color(0xFF0E0B0C), Offset(0f, size.height * 0.62f), Size(w, size.height * 0.38f))
            }
            "Agenda" -> for (i in 0 until 3) {
                drawRoundRect(fg.copy(alpha = 0.12f), Offset(0f, size.height * (0.12f + i * 0.22f)), Size(w, size.height * 0.16f), CornerRadius(6.dp.toPx()))
                drawRect(listOf(Color(0xFF4F7CFF), Color(0xFFE89A3C), Color(0xFF34B26F))[i], Offset(0f, size.height * (0.12f + i * 0.22f)), Size(3.dp.toPx(), size.height * 0.16f))
            }
            "Headlines" -> {
                drawRoundRect(fg.copy(alpha = 0.18f), Offset(0f, size.height * 0.08f), Size(w, size.height * 0.34f), CornerRadius(8.dp.toPx()))
                for (i in 0 until 3) drawRoundRect(fg.copy(alpha = 0.25f), Offset(0f, size.height * (0.5f + i * 0.09f)), Size(w * (0.9f - i * 0.2f), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            "Music" -> {
                drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(w * 0.12f, size.height * 0.08f), Size(w * 0.76f, w * 0.76f), CornerRadius(10.dp.toPx()))
                drawCircle(Color.White, 9.dp.toPx(), Offset(w / 2, size.height * 0.72f))
            }
            "Move" -> {
                drawArc(Color(0xFF22C55E).copy(alpha = 0.2f), 0f, 360f, false, Offset(w * 0.18f, size.height * 0.08f), Size(w * 0.64f, w * 0.64f), style = Stroke(6.dp.toPx()))
                drawArc(Color(0xFF22C55E), -90f, 250f, false, Offset(w * 0.18f, size.height * 0.08f), Size(w * 0.64f, w * 0.64f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

// ---- 6. Default home -----------------------------------------------------------------------------------------------

@Composable
private fun DefaultStep() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var isDefault by remember { mutableStateOf(isDefaultHome(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = isDefaultHome(context) }
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val done by animateFloatAsState(if (isDefault) 1f else 0f, tween(500), label = "homeDone")
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            val ring = scheme.primary
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(ring.copy(alpha = 0.25f), Color.Transparent)), size.minDimension / 2f)
                drawArc(ring, -90f, 360f * done, false, Offset(8.dp.toPx(), 8.dp.toPx()), Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
            Box(Modifier.size(104.dp).background(if (isDefault) scheme.primary else scheme.primaryContainer, RoundedCornerShape(34.dp)), contentAlignment = Alignment.Center) {
                Icon(if (isDefault) Icons.Filled.Check else Icons.Filled.Home, null, Modifier.size(52.dp), tint = if (isDefault) scheme.onPrimary else scheme.onPrimaryContainer)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    if (isDefault) StepText("ALL SET", "Nivo is your home", "Press Home any time to come back here. Everything you chose can be changed in Settings.")
    else StepText("LAST STEP", "Make Nivo your home", "Android asks you to confirm. You can switch back to your old home screen whenever you like.")
    Spacer(Modifier.height(20.dp))
    if (!isDefault) Button(
        onClick = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
            else Intent(Settings.ACTION_HOME_SETTINGS)
            runCatching { launcher.launch(intent) }
        },
        shape = CircleShape, modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text("Set Nivo as home", style = MaterialTheme.typography.titleMedium) }
    Row(Modifier.padding(top = 20.dp).fillMaxWidth().background(scheme.surfaceContainerLow, RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(20.dp), tint = scheme.primary)
        Text(
            "Nothing else is asked for now. Contacts search, calendar, health and notification dots each ask for their own permission only when you turn them on — and what they read stays on this phone.",
            Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
    }
}
