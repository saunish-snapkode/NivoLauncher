package com.snapkode.nivolauncher.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.NotificationState
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val drawer = remember { DrawerState() }
    val scope = rememberCoroutineScope()
    val motion = com.snapkode.nivolauncher.ui.design.LocalMotion.current
    drawer.stiffness = motion.drawerStiffness
    drawer.damping = 1f
    var showSettings by remember { mutableStateOf(false) }
    var settingsRoute by remember { mutableStateOf<String?>(null) }

    // Registered before the settings screen's own handler, so settings wins when visible.
    PredictiveBackHandler(enabled = drawer.isOpen && !showSettings) { events ->
        try {
            events.collect { drawer.scrubTo(1f - it.progress * 0.4f) }
            drawer.animateTo(0f)
        } catch (_: CancellationException) {
            drawer.animateTo(1f)
        }
    }

    val settings by viewModel.settings.collectAsState()
    val notificationCounts by NotificationState.counts.collectAsState()
    val badgeColor = settings.badgeColor.color()

    // Keep system-bar icons readable: dark over light surfaces (settings, open drawer), as chosen over the wallpaper.
    val view = androidx.compose.ui.platform.LocalView.current
    val surfaceIsLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val overSurface = showSettings || !settings.onboarded || drawer.isMostlyOpen
    LaunchedEffect(overSurface, surfaceIsLight, settings.darkStatusIcons) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = if (overSurface) surfaceIsLight else settings.darkStatusIcons
        controller.isAppearanceLightNavigationBars = if (overSurface) surfaceIsLight else false
    }
    val sharedDrag = androidx.compose.runtime.remember { DragState() }
    val selection = androidx.compose.runtime.remember { SelectionState() }
    CompositionLocalProvider(
        LocalLabelSize provides settings.labelSize,
        LocalLabelLines provides settings.labelLines,
        LocalHomeTextColor provides if (settings.darkHomeText) Color(0xFF202124) else Color.White,
        LocalHomeLocked provides settings.lockHome,
        LocalBadgeStyle provides BadgeStyle(settings.notificationDots, settings.notificationCount, badgeColor, notificationCounts),
        LocalFolderStyle provides FolderStyle(settings.folderShape, settings.folderTileOpacity, settings.folderTint),
        LocalDragState provides sharedDrag,
        LocalSelection provides selection,
        com.snapkode.nivolauncher.ui.lasso.LocalLasso provides viewModel.lasso,
    ) {
    Box(Modifier.fillMaxSize()) {
        HomeScreen(viewModel, drawer, onOpenSettings = { route -> settingsRoute = route; showSettings = true }, barsOwnedElsewhere = showSettings || !settings.onboarded)
        CompositionLocalProvider(LocalLabelSize provides settings.drawerLabelSize) { AppDrawer(viewModel, drawer) }
        SelectionBar(viewModel, selection, drawer)
        // Settings glide in from the side and settle without overshoot (no wallpaper sliver at the edge); with reduced
        // motion they only cross-fade.
        AnimatedVisibility(
            visible = showSettings,
            enter = if (motion.reduced) fadeIn(tween(motion.duration(220)))
            else fadeIn(tween(220)) + slideInHorizontally(tween<IntOffset>(300, easing = FastOutSlowInEasing)) { it / 5 },
            exit = if (motion.reduced) fadeOut(tween(motion.duration(180)))
            else fadeOut(tween(180)) + slideOutHorizontally(tween<IntOffset>(200, easing = FastOutLinearInEasing)) { it / 5 },
        ) { SettingsScreen(viewModel, initialRoute = settingsRoute, onClose = { showSettings = false }) }
        if (!settings.onboarded) OnboardingFlow(viewModel)
        // A cold start reads every installed app first; the mark breathes until that is done, then fades away.
        val ready by viewModel.appsReady.collectAsState()
        androidx.compose.animation.AnimatedVisibility(
            visible = !ready,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(0)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(motion.duration(350))),
        ) {
            com.snapkode.nivolauncher.ui.design.NivoLoadingScreen(dark = androidx.compose.foundation.isSystemInDarkTheme())
        }
    }
    }
}
