package com.snapkode.nivolauncher.ui.pagelet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.MediaProgress
import com.snapkode.nivolauncher.NowPlaying
import com.snapkode.nivolauncher.NowPlayingState
import com.snapkode.nivolauncher.ui.design.LocalMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Whatever is playing, on a page of its own, coloured by its cover. */
@Composable
fun NowPlayingPage(viewModel: LauncherViewModel, onOptions: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val media by NowPlayingState.current.collectAsState()
    // Whether the system lets this launcher see media sessions; checked again whenever the page comes back.
    var listening by remember { mutableStateOf(isListenerEnabled(context)) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) listening = isListenerEnabled(context) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    NowPlayingBody(media, listening, media?.let { appLabel(context, it.packageName) }, onOptions) { viewModel.openNotificationAccessSettings() }
}

/** The page for a given track (or none): the loader above feeds it; the screenshot test feeds it a fixed one. */
@Composable
internal fun NowPlayingBody(media: NowPlaying?, listening: Boolean, app: String?, onOptions: () -> Unit, onAllow: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = media?.art?.let { remember(it) { CoverColors.of(it) } }
    val accent = colors?.accent ?: scheme.primary
    val base = colors?.base ?: scheme.primaryContainer
    // The page takes on the cover's colour at the top and settles into the surface below, so the bars around it stay calm.
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (media?.playing == true) Equalizer(accent, Modifier.padding(end = 10.dp))
            Text(
                when {
                    media == null -> "NOW PLAYING"
                    app != null -> (if (media.playing) "PLAYING ON " else "PAUSED · ") + app.uppercase()
                    else -> if (media.playing) "PLAYING" else "PAUSED"
                },
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
                color = if (media != null) lerp(accent, scheme.onSurface, 0.5f) else scheme.primary, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOptions) { Icon(Icons.Outlined.Tune, "Customise Now playing", tint = scheme.onSurfaceVariant) }
        }
        when {
            media != null -> Player(media, accent)
            !listening -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                Record(Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally), spinning = false)
                AskCard(
                    "Let Nivo see what's playing",
                    "Music controls come from the system's media sessions, which need notification access. Nivo only reads what is playing; nothing leaves your phone.",
                    "Open settings",
                ) { onAllow() }
            }
            else -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Record(spinning = false)
                Text("Silence, for now", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface, modifier = Modifier.padding(top = 24.dp))
                Text(
                    "Start a song, a podcast or an audiobook in any app and it plays here, with its cover.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun Player(m: NowPlaying, accent: Color = MaterialTheme.colorScheme.primary) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // The position moves only while something plays, twice a second.
    LaunchedEffect(m.playing, m.positionAtMs) { while (m.playing) { tick = System.currentTimeMillis(); delay(500) }; tick = System.currentTimeMillis() }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: MediaProgress.fraction(m, tick)
    // The cover sits back a little when paused, and comes forward when playing.
    val coverScale by animateFloatAsState(if (m.playing || motion.reduced) 1f else 0.9f, motion.spring(0.8f), label = "coverScale")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val coverSide = minOf(maxWidth - 64.dp, maxHeight * 0.48f)
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(coverSide).scale(coverScale), contentAlignment = Alignment.Center) {
                // A soft glow in the cover's colour, as if it lit the page.
                Box(Modifier.matchParentSize().drawBehind {
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)), size.minDimension * 0.75f, Offset(size.width / 2f, size.height * 0.58f))
                })
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.45f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (m.art != null) Image(m.art.asImageBitmap(), "Cover of ${m.album.ifBlank { m.title }}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(coverSide / 3))
                }
            }
            Spacer(Modifier.weight(0.5f))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    m.title, Modifier.fillMaxWidth().basicMarquee(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
                    color = scheme.onSurface, maxLines = 1,
                )
                val sub = listOf(m.artist, m.album).filter { it.isNotBlank() }.joinToString(" — ")
                if (sub.isNotBlank()) Text(sub, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (m.durationMs > 0) {
                WaveProgress(
                    fraction, m.playing && !motion.reduced, accent,
                    onSeekPreview = { dragging = it },
                    onSeek = { f -> NowPlayingState.seekTo((f * m.durationMs).toLong()); dragging = null },
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(36.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(MediaProgress.clock((fraction * m.durationMs).toLong()), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text("-" + MediaProgress.clock(((1f - fraction) * m.durationMs).toLong()), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            } else Spacer(Modifier.height(24.dp))
            Row(Modifier.padding(top = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Transport(Icons.Filled.SkipPrevious, "Previous track") { NowPlayingState.previous() }
                PlayButton(m.playing, accent)
                Transport(Icons.Filled.SkipNext, "Next track") { NowPlayingState.next() }
            }
            Spacer(Modifier.weight(0.4f))
        }
    }
}

/** The play button morphs between a circle (paused) and a softer square (playing), like the state it controls. */
@Composable
private fun PlayButton(playing: Boolean, accent: Color) {
    val motion = LocalMotion.current
    val corner by animateDpAsState(if (playing) 28.dp else 44.dp, motion.spring(1f), label = "playCorner")
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier.size(88.dp).clip(RoundedCornerShape(corner)).background(accent)
            .clickable(role = Role.Button, onClickLabel = if (playing) "Pause" else "Play") { NowPlayingState.playPause() },
        contentAlignment = Alignment.Center,
    ) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = onAccent, modifier = Modifier.size(42.dp)) }
}

@Composable
private fun Transport(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(scheme.surfaceContainerHigh.copy(alpha = 0.8f)).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = scheme.onSurface, modifier = Modifier.size(30.dp)) }
}

/**
 * The track position as a line that ripples while it plays (and lies flat when paused): played part in the cover's
 * colour, the rest as a quiet hairline. Drag or tap anywhere on it to move through the track.
 */
@Composable
private fun WaveProgress(fraction: Float, moving: Boolean, color: Color, onSeekPreview: (Float) -> Unit, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val rest = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "wavePhase")
    val amp by animateFloatAsState(if (moving) 1f else 0f, tween(400), label = "waveAmp")
    var latest by remember { mutableStateOf(fraction) }
    Canvas(
        modifier
            .semantics {
                contentDescription = "Track position"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { v -> onSeek(v.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onSeek(latest) },
                    onDragCancel = { onSeek(latest) },
                ) { change, _ -> latest = (change.position.x / size.width).coerceIn(0f, 1f); onSeekPreview(latest) }
            },
    ) {
        val w = size.width; val cy = size.height / 2f
        val x = w * fraction
        val a = 3.5.dp.toPx() * amp
        val wl = 26.dp.toPx()
        if (x > 0f) {
            val p = Path().apply {
                moveTo(0f, cy)
                var px = 0f
                while (px < x) { lineTo(px, cy + a * sin(px / wl * 2f * PI.toFloat() - phase)); px += 2f }
                lineTo(x, cy + a * sin(x / wl * 2f * PI.toFloat() - phase))
            }
            drawPath(p, color, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        drawLine(rest, Offset(x + 6.dp.toPx(), cy), Offset(w, cy), 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(x, cy - 10.dp.toPx()), Offset(x, cy + 10.dp.toPx()), 5.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Three bars that dance while something plays. */
@Composable
private fun Equalizer(color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalMotion.current.reduced
    val t by rememberInfiniteTransition(label = "eq").animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "eqT")
    Canvas(modifier.size(14.dp, 12.dp)) {
        val bw = size.width / 5f
        for (i in 0 until 3) {
            val f = if (reduced) 0.6f else 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1f + i * 0.37f) + i * 1.9f))
            val h = size.height * f
            drawRoundRect(color, Offset(i * bw * 2f, size.height - h), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2f))
        }
    }
}

/** A vinyl record: the picture for "nothing playing". */
@Composable
private fun Record(modifier: Modifier = Modifier, spinning: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val groove = scheme.onSurface.copy(alpha = 0.08f); val disc = scheme.surfaceContainerHighest; val label = scheme.primary; val shine = Color.White.copy(alpha = 0.18f)
    Canvas(modifier.size(160.dp)) {
        val r = size.minDimension / 2f; val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(disc, r, c)
        for (i in 1..6) drawCircle(groove, r * (0.42f + i * 0.09f), c, style = Stroke(1.dp.toPx()))
        drawArc(shine, -60f, 50f, false, Offset(c.x - r * 0.8f, c.y - r * 0.8f), androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
        drawCircle(label, r * 0.32f, c)
        drawCircle(disc, r * 0.06f, c)
    }
}

/** A colour that reads on [bg]: [c] itself if it contrasts enough, otherwise pushed darker or lighter. */
private fun readable(c: Color, bg: Color): Color {
    val light = bg.luminance() > 0.5f
    return if (light) lerp(c, Color.Black, if (c.luminance() > 0.45f) 0.45f else 0.1f) else lerp(c, Color.White, if (c.luminance() < 0.25f) 0.45f else 0.1f)
}

private fun appLabel(context: android.content.Context, pkg: String): String? = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrNull()

internal fun isListenerEnabled(context: android.content.Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** The colours a page takes from a cover: a vivid accent and a calmer base. Pure apart from reading the pixels. */
internal object CoverColors {
    class Picked(val accent: Color, val base: Color)

    fun of(b: android.graphics.Bitmap): Picked {
        val small = android.graphics.Bitmap.createScaledBitmap(b, 24, 24, true)
        val px = IntArray(24 * 24); small.getPixels(px, 0, 24, 0, 0, 24, 24)
        return pick(px)
    }

    /** From ARGB pixels: the accent is the most colourful well-lit pixel, the base is the average. */
    fun pick(px: IntArray): Picked {
        var r = 0L; var g = 0L; var bl = 0L
        var best = 0; var bestScore = -1f
        val hsv = FloatArray(3)
        for (p in px) {
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF
            android.graphics.Color.colorToHSV(p, hsv)
            val score = hsv[1] * (1f - kotlin.math.abs(hsv[2] - 0.7f))
            if (score > bestScore) { bestScore = score; best = p }
        }
        val n = px.size.coerceAtLeast(1)
        val base = Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
        android.graphics.Color.colorToHSV(best, hsv)
        // A grey cover gives a grey accent; nudge it to at least a little colour and a usable brightness.
        hsv[1] = hsv[1].coerceAtLeast(0.35f); hsv[2] = hsv[2].coerceIn(0.45f, 0.85f)
        return Picked(Color(android.graphics.Color.HSVToColor(hsv)), base)
    }
}

/** The colour wash behind the Now Playing page, from the cover (or the theme when nothing plays). Animated. */
@Composable
internal fun nowPlayingWash(): Brush {
    val scheme = MaterialTheme.colorScheme
    val media by NowPlayingState.current.collectAsState()
    val colors = media?.art?.let { remember(it) { CoverColors.of(it) } }
    // Washed from the accent rather than the average colour: averages of covers come out muddy.
    val top by androidx.compose.animation.animateColorAsState(
        lerp(scheme.surface, colors?.accent ?: scheme.primaryContainer, if (media != null) 0.34f else 0.25f), tween(600), label = "wash",
    )
    return Brush.verticalGradient(0f to top, 0.7f to scheme.surface)
}
