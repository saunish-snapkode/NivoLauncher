package com.snapkode.nivolauncher.ui.pagelet

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.pageCloseness
import com.snapkode.nivolauncher.ui.MenuIcons
import com.snapkode.nivolauncher.ui.NivoMenu
import com.snapkode.nivolauncher.ui.NivoMenuItem
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyBackdrop
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyContent
import com.snapkode.nivolauncher.ui.pagelet.sky.SkyController
import com.snapkode.nivolauncher.ui.pagelet.sky.skyLookFor
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

/** How close to fully shown (0..1) a page must be before its ambient motion runs. */
private const val SETTLED = 0.995f

/** A Pagelet on the workspace: which pager page it is on and how it is configured. */
class PageletPlacement(val id: String, val pagerIndex: Int, val config: PageletConfig)

/** Owns the per-Pagelet controllers so the backdrop (below the dock) and the content (above it) share one state. */
class PageletRuntime(
    private val scope: CoroutineScope,
    private val reducedMotion: () -> Boolean,
    val provider: com.snapkode.nivolauncher.pagelet.weather.LiveSkyProvider,
) {
    private val controllers = HashMap<String, SkyController>()

    /** Drawn at once, from the cache, before any network. */
    fun sky(id: String): SkyController = controllers.getOrPut(id) {
        SkyController(scope, reducedMotion()).also {
            val data = provider.snapshot()
            it.update(data, skyLookFor(data))
        }
    }

    fun forget(keep: Set<String>) { controllers.keys.retainAll(keep) }
}

/** Resumed + power conditions every Pagelet obeys. */
@Composable
fun rememberPageletEnvironment(): PageletEnvironment {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var saver by remember { mutableStateOf(false) }
    val lowRam = remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true }
    DisposableEffect(owner) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            saver = power?.isPowerSaveMode == true
        }
        owner.lifecycle.addObserver(observer)
        saver = power?.isPowerSaveMode == true
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return PageletEnvironment(resumed, saver, lowRam)
}

class PageletEnvironment(val resumed: Boolean, val batterySaver: Boolean, val lowRam: Boolean) {
    val lowLoad: Boolean get() = batterySaver || lowRam
}

/**
 * Draws the sky behind the pager (and behind the dock) for every Pagelet page near the one being shown. It slides with the
 * pager so the page swipes like any other, and is drawn only while some part of it is on screen.
 */
@Composable
fun PageletBackdropLayer(
    runtime: PageletRuntime,
    placements: List<PageletPlacement>,
    pagePosition: () -> Float,
    stride: () -> Float,
    layerAlpha: () -> Float,
    env: PageletEnvironment,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalMotion.current.reduced
    placements.forEach { p ->
        key(p.id) {
            val closeness by remember(p.pagerIndex) { derivedStateOf { pageCloseness(pagePosition(), p.pagerIndex) } }
            val near by remember(p.pagerIndex) { derivedStateOf { abs(pagePosition() - p.pagerIndex) < 1.05f } }
            // Ambient motion and the blur wait until the page has stopped: a swipe is then just layers sliding.
            val settled by remember(p.pagerIndex) { derivedStateOf { pageCloseness(pagePosition(), p.pagerIndex) > SETTLED } }
            if (near) {
                val ctl = runtime.sky(p.id)
                Box(
                    modifier.fillMaxSize().graphicsLayer {
                        translationX = (p.pagerIndex - pagePosition()) * stride()
                        alpha = layerAlpha()
                    }.clipToBounds()
                ) {
                    SkyBackdrop(ctl, active = env.resumed && settled, reducedMotion = reduced, lowLoad = env.lowLoad, blurEnabled = settled)
                }
            }
        }
    }
}

/** The Sky's text, arc and numbers, above the dock. Plays the entrance once, the first time the page is mostly in view. */
@Composable
fun PageletContentLayer(
    runtime: PageletRuntime,
    placements: List<PageletPlacement>,
    pagePosition: () -> Float,
    stride: () -> Float,
    layerAlpha: () -> Float,
    env: PageletEnvironment,
    onOptions: (String) -> Unit,
    onActivityLog: (() -> Unit)? = null,
    /** Height of the dock when it is showing; a Pagelet that keeps the dock makes room for it. */
    dockHeight: androidx.compose.ui.unit.Dp = 0.dp,
    /** Whether the page dots strip is showing; the page only makes room for it then. */
    dotsShown: Boolean = true,
    iconFor: (String?) -> androidx.compose.ui.graphics.ImageBitmap? = { null },
    modifier: Modifier = Modifier,
) {
    val reduced = LocalMotion.current.reduced
    val context = LocalContext.current
    // Permission prompts on the page ask right there: the system dialog, or Settings where there is no dialog.
    var calendarRefused by remember { mutableStateOf(false) }
    val askCalendar = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) calendarRefused = true
        runtime.provider.kick()
    }
    val askSteps = androidx.activity.compose.rememberLauncherForActivityResult(com.snapkode.nivolauncher.pagelet.steps.StepsSource.requestContract()) { runtime.provider.kick() }
    val steps = remember { com.snapkode.nivolauncher.pagelet.steps.StepsSource(context.applicationContext) }
    val allow: (com.snapkode.nivolauncher.pagelet.SkySection) -> Unit = { section ->
        when (section) {
            com.snapkode.nivolauncher.pagelet.SkySection.WELLBEING -> com.snapkode.nivolauncher.pagelet.UsageAccess.open(context)
            com.snapkode.nivolauncher.pagelet.SkySection.CALENDAR ->
                if (calendarRefused) openAppSettings(context) else askCalendar.launch(android.Manifest.permission.READ_CALENDAR)
            com.snapkode.nivolauncher.pagelet.SkySection.STEPS ->
                if (steps.status() == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) askSteps.launch(com.snapkode.nivolauncher.pagelet.steps.StepsSource.permissions)
                else openHealthConnectStore(context)
            else -> Unit
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The page dots sit in a 32 dp strip above the navigation bar.
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (dotsShown) 32.dp else 0.dp)
    placements.forEach { p ->
        key(p.id) {
            val near by remember(p.pagerIndex) { derivedStateOf { abs(pagePosition() - p.pagerIndex) < 1.05f } }
            if (near) {
                val ctl = runtime.sky(p.id)
                val settled by remember(p.pagerIndex) { derivedStateOf { pageCloseness(pagePosition(), p.pagerIndex) > SETTLED } }
                val active = env.resumed && settled
                // Fresh numbers while visible; nothing is collected while the page is away.
                LaunchedEffect(p.id, active) {
                    if (!active) return@LaunchedEffect
                    runtime.provider.state.collect { st ->
                        val d = (st as? com.snapkode.nivolauncher.pagelet.PageletUiState.Ready)?.data ?: return@collect
                        ctl.update(d, skyLookFor(d))
                    }
                }
                // Coming back to the launcher (e.g. after unlock) re-checks the forecast; it still obeys Expires.
                LaunchedEffect(env.resumed) { if (env.resumed) runtime.provider.kick() }
                // Debug scene overrides re-evaluate the look immediately.
                val override = com.snapkode.nivolauncher.ui.pagelet.sky.SkyDebug
                LaunchedEffect(override.phase, override.layer) { ctl.data?.let { ctl.update(it, skyLookFor(it)) } }
                // Starts as soon as the page begins to slide in, so a swipe never shows an empty sky. A boolean derived state:
                // reading the continuous closeness here would recompose the whole layer on every frame of a swipe.
                val entering by remember(p.pagerIndex) { derivedStateOf { pageCloseness(pagePosition(), p.pagerIndex) > 0.04f } }
                LaunchedEffect(entering) { if (entering) ctl.enterIfNeeded() }
                Box(
                    modifier.fillMaxSize().graphicsLayer {
                        translationX = (p.pagerIndex - pagePosition()) * stride()
                        alpha = layerAlpha()
                    }.clipToBounds()
                ) {
                    SkyContent(
                        ctl = ctl, config = p.config, active = active, reducedMotion = reduced, lowLoad = env.lowLoad,
                        onOptions = { onOptions(p.id) },
                        onActivityLog = onActivityLog,
                        onAllow = allow,
                        iconFor = iconFor,
                        onAttribution = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.met.no/doc/License")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        topInset = top, bottomInset = if (p.config.hideDock) bottom else bottom + dockHeight,
                    )
                }
            }
        }
    }
}

/** "Pagelet removed · Undo", shown for a few seconds after a removal. */
@Composable
fun PageletUndoPill(visible: Boolean, onUndo: () -> Unit, onExpire: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(visible) { if (visible) { kotlinx.coroutines.delay(6000); onExpire() } }
    androidx.compose.animation.AnimatedVisibility(visible, modifier = modifier, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        androidx.compose.material3.Surface(
            // Announced by a screen reader as it appears, so Undo isn't missed.
            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 6.dp,
        ) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(start = 20.dp, end = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text("Pagelet removed", color = androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface)
                androidx.compose.material3.TextButton(onClick = onUndo) {
                    androidx.compose.material3.Text("Undo", color = androidx.compose.material3.MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}

/** Debug builds only: step through every time of day and weather without waiting for them. */
@Composable
fun PageletDebugControls() {
    val context = LocalContext.current
    if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
    val dbg = com.snapkode.nivolauncher.ui.pagelet.sky.SkyDebug
    androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        androidx.compose.material3.Text("Debug scene", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        androidx.compose.material3.Text(
            "${dbg.phase ?: "real time"} · ${dbg.layer ?: "real weather"}",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            val one = Modifier.weight(1f)
            androidx.compose.material3.OutlinedButton(onClick = { dbg.nextPhase() }, modifier = one, contentPadding = pad) { androidx.compose.material3.Text("Next time", maxLines = 1) }
            androidx.compose.material3.OutlinedButton(onClick = { dbg.nextLayer() }, modifier = one, contentPadding = pad) { androidx.compose.material3.Text("Next weather", maxLines = 1) }
            androidx.compose.material3.OutlinedButton(onClick = { dbg.clear() }, modifier = one, contentPadding = pad) { androidx.compose.material3.Text("Reset", maxLines = 1) }
        }
    }
}

/** The wash behind a self-drawn Pagelet's page: the colour its top carries, fading into the surface. Painted full-screen. */
@Composable
internal fun SurfaceWash(type: com.snapkode.nivolauncher.pagelet.PageletType, modifier: Modifier = Modifier) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val brush: Brush = when (type) {
        com.snapkode.nivolauncher.pagelet.PageletType.AGENDA ->
            Brush.verticalGradient(0f to androidx.compose.ui.graphics.lerp(scheme.surface, scheme.primaryContainer, 0.35f), 0.35f to scheme.surface)
        com.snapkode.nivolauncher.pagelet.PageletType.MOVE ->
            Brush.verticalGradient(0f to androidx.compose.ui.graphics.lerp(scheme.surface, Color(0xFF22C55E), 0.12f), 0.4f to scheme.surface)
        com.snapkode.nivolauncher.pagelet.PageletType.NOW_PLAYING -> nowPlayingWash()
        else -> Brush.verticalGradient(0f to scheme.surface, 1f to scheme.surface)
    }
    Box(modifier.fillMaxSize().background(brush))
}

/**
 * Behind the pager (and behind the status bar and dock): each self-drawn Pagelet's wash, sliding with the pages. The page
 * itself is transparent, so the colour runs unbroken from the top edge of the screen, as Today's sky does.
 */
@Composable
fun SurfaceBackdropLayer(
    placements: List<PageletPlacement>,
    pagePosition: () -> Float,
    stride: () -> Float,
    layerAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    placements.forEach { p ->
        key(p.id) {
            val type = com.snapkode.nivolauncher.pagelet.pageletTypeOf(p.id)
            val near by remember(p.pagerIndex) { derivedStateOf { abs(pagePosition() - p.pagerIndex) < 1.05f } }
            if (near && type != null) {
                Box(
                    modifier.fillMaxSize().graphicsLayer {
                        translationX = (p.pagerIndex - pagePosition()) * stride()
                        alpha = layerAlpha()
                    }.clipToBounds()
                ) { SurfaceWash(type) }
            }
        }
    }
}
