package com.snapkode.nivolauncher.ui

import android.app.WallpaperManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.DisposableEffect
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.pagelet.dockHideFraction
import com.snapkode.nivolauncher.pagelet.isPageletId
import com.snapkode.nivolauncher.pagelet.pageletTypeOf
import com.snapkode.nivolauncher.ui.pagelet.PageletBackdropLayer
import com.snapkode.nivolauncher.ui.pagelet.PageletDebugControls
import com.snapkode.nivolauncher.ui.pagelet.PageletContentLayer
import com.snapkode.nivolauncher.ui.pagelet.PageletGallerySheet
import com.snapkode.nivolauncher.ui.pagelet.PageletOptionsSheet
import com.snapkode.nivolauncher.ui.pagelet.PageletPlacement
import com.snapkode.nivolauncher.ui.pagelet.PageletRuntime
import com.snapkode.nivolauncher.ui.pagelet.PageletUndoPill
import com.snapkode.nivolauncher.ui.pagelet.rememberPageletEnvironment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import com.snapkode.nivolauncher.ui.design.LocalHapticsEnabled
import com.snapkode.nivolauncher.ui.design.LocalMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import com.snapkode.nivolauncher.data.LauncherSettings
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import com.snapkode.nivolauncher.data.DockStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.DropTarget
import com.snapkode.nivolauncher.data.isFolderId
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.ClockStyle
import com.snapkode.nivolauncher.data.GestureAction
import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.isAnyWidgetId
import com.snapkode.nivolauncher.ui.lasso.LassoLayer
import com.snapkode.nivolauncher.ui.lasso.LassoUndoPill
import com.snapkode.nivolauncher.ui.lasso.LocalLasso
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Tracks one in-flight icon drag. Positions are in root coordinates. */
internal class DragState {
    var id by mutableStateOf<String?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var lifted by mutableStateOf(false)
    /** Set when the dragged app is being pulled out of an open folder. */
    var fromFolder: String? = null
    /** The folder currently open and where it was tapped, so it can grow from (and shrink back to) its tile. */
    var openFolderId by mutableStateOf<String?>(null)
    var openFolderOrigin by mutableStateOf<Rect?>(null)
    /** The icon the dragged app would be grouped with if dropped now. */
    var mergeTarget by mutableStateOf<String?>(null)
    /** Where the open folder card sits, so dragging an icon out of it can tell inside from outside. */
    var folderBounds by mutableStateOf<Rect?>(null)
    /** Set when the dragged app comes from the app drawer rather than the home screen. */
    var fromDrawer: Boolean = false
    var onDrop: (id: String, at: Offset, fromFolder: String?, fromDrawer: Boolean) -> Unit = { _, _, _, _ -> }

    fun begin(id: String, at: Offset, fromFolder: String? = null, fromDrawer: Boolean = false) {
        this.id = id; pos = at; lifted = false; this.fromFolder = fromFolder; this.fromDrawer = fromDrawer
    }
    fun cancel() { id = null; lifted = false; fromFolder = null; fromDrawer = false; mergeTarget = null }
    fun finish() {
        val dropped = id
        val at = pos
        val folder = fromFolder
        val drawer = fromDrawer
        cancel()
        if (dropped != null) onDrop(dropped, at, folder, drawer)
    }
}

/** One drag shared by the home screen and the drawer, so an icon can start in one and finish in the other. */
internal val LocalDragState = androidx.compose.runtime.compositionLocalOf<DragState?> { null }

private const val SwipeDownVelocity = 1500f
private val PageEdge = 48.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    drawer: DrawerState,
    onOpenSettings: (String?) -> Unit,
    /** While a full-screen page (Settings, onboarding) is on top it owns the system-bar icon colours. */
    barsOwnedElsewhere: Boolean = false,
) {
    val committedItems by viewModel.items.collectAsState()
    val resizePreview by viewModel.previewItems.collectAsState()
    // During a widget resize the icons are shown where they will land, and glide there as the handle moves.
    val items = resizePreview ?: committedItems
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val appsById = remember(apps) { apps.associateBy { it.id } }
    var showWidgets by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var showOverview by remember { mutableStateOf(false) }
    val editScale by animateFloatAsState(if (editMode || showOverview) 0.9f else 1f, LocalMotion.current.spring(0.9f), label = "editScale")
    var widgetLifted by remember { mutableStateOf(false) }
    val drag = LocalDragState.current ?: remember { DragState() }
    val lasso = viewModel.lasso
    val lassoUndo by viewModel.lassoUndo.collectAsState()
    var openFolderId by drag::openFolderId
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val workspace = items.filter { !it.inDock && (it.id in appsById || it.id in folders || isAnyWidgetId(it.id)) }
    val itemsByPage = remember(items, appsById, folders) { workspace.groupBy { it.page } }
    val dock = items.filter { it.inDock && (it.id in appsById || it.id in folders || isAnyWidgetId(it.id)) }
    val basePages by viewModel.pages.collectAsState()
    val libraryOn = settings.libraryPage
    // While an icon is held, one spare page is offered at the end of the workspace pages.
    val workspaceCount = basePages + (if (drag.lifted || lasso.state == com.snapkode.nivolauncher.lasso.LassoState.DRAGGING) 1 else 0)
    val pageCount = workspaceCount + (if (libraryOn) 1 else 0)
    val pageCountState = rememberUpdatedState(pageCount)
    // Order is always: the workspace pages (Pagelets included), then the Library at the far right.
    val libraryIndex = workspaceCount
    fun pagerIndexOf(workspacePage: Int) = workspacePage
    fun workspacePageAt(pagerPage: Int): Int? = if (libraryOn && pagerPage >= workspaceCount) null else pagerPage
    val pagerState = rememberPagerState(initialPage = pagerIndexOf(settings.defaultPage)) { pageCountState.value }
    val defaultPage by rememberUpdatedState(pagerIndexOf(settings.defaultPage))

    var pagerRect by remember { mutableStateOf(Rect.Zero) }
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    // The dock's own (unscaled) height: steady while the dock's on-screen bounds move with the drawer and edit scale.
    var dockHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var dotsHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // The pager now fills the screen and never changes size; the dots and the dock float over its bottom edge. Workspace pages
    // keep that much room free at the bottom, so their grid is the same size it always was and nothing re-measures on a swipe.
    val dotsShownNow = (basePages + (if (libraryOn) 1 else 0)) > 1 && settings.showPageDots
    val dotsReservePx = if (dotsShownNow) dotsHeightPx else 0
    val chromePx = dotsReservePx + (if (settings.showDock) dockHeightPx else 0)
    var pagerFull by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(pagerFull, chromePx) {
        pagerRect = Rect(pagerFull.left, pagerFull.top, pagerFull.right, (pagerFull.bottom - chromePx).coerceAtLeast(pagerFull.top))
    }
    val dockPageCount = rememberUpdatedState(settings.dockPages)
    val dockPager = rememberPagerState { dockPageCount.value }
    val edgePx = with(density) { PageEdge.toPx() }

    // ---- Pagelets: whole-page widgets drawn behind and in front of the pager ------------------------------------------
    val pageletConfigs by viewModel.pagelets.collectAsState()
    val pageletUndo by viewModel.pageletUndo.collectAsState()
    val pageletEnv = rememberPageletEnvironment()
    val reducedMotionNow = LocalMotion.current.reduced
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val settingsForWeather by rememberUpdatedState(settings)
    // The sections any Sky is showing: nothing is read (usage, calendar, steps) for a section that is switched off.
    val wantedSections = remember { mutableStateOf(emptySet<String>()) }
    val pageletRuntime = remember {
        val resolver = com.snapkode.nivolauncher.pagelet.weather.WeatherLocationResolver(appContext)
        val version = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: "1"
        val client = com.snapkode.nivolauncher.pagelet.weather.MetNorwayClient(
            com.snapkode.nivolauncher.pagelet.weather.UrlConnectionFetcher(),
            com.snapkode.nivolauncher.pagelet.weather.MetNorwayClient.userAgent(version, com.snapkode.nivolauncher.BuildConfig.MET_CONTACT),
        )
        val repo = com.snapkode.nivolauncher.pagelet.weather.SkyWeatherRepository(client, com.snapkode.nivolauncher.pagelet.weather.PrefsWeatherStore(appContext))
        val stepsSource = com.snapkode.nivolauncher.pagelet.steps.StepsSource(appContext)
        PageletRuntime(
            scope, { reducedMotionNow },
            com.snapkode.nivolauncher.pagelet.weather.LiveSkyProvider(
                repo,
                wellbeing = com.snapkode.nivolauncher.pagelet.wellbeing.WellbeingSource(appContext),
                calendar = { now ->
                    if (!com.snapkode.nivolauncher.pagelet.calendar.CalendarToday.hasPermission(appContext)) com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus.NeedsPermission to emptyList()
                    else com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus.Ready to com.snapkode.nivolauncher.pagelet.calendar.CalendarToday.events(appContext, now)
                },
                steps = { start, now ->
                    when (val r = stepsSource.today(start, now)) {
                        is com.snapkode.nivolauncher.pagelet.steps.StepsResult.Ready -> com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus.Ready to r.count
                        com.snapkode.nivolauncher.pagelet.steps.StepsResult.NeedsPermission -> com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus.NeedsPermission to null
                        com.snapkode.nivolauncher.pagelet.steps.StepsResult.NotInstalled -> com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus.NotInstalled to null
                        com.snapkode.nivolauncher.pagelet.steps.StepsResult.Unsupported -> com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus.Unsupported to null
                        is com.snapkode.nivolauncher.pagelet.steps.StepsResult.Failed -> com.snapkode.nivolauncher.ui.pagelet.sky.SectionStatus.Unavailable to null
                    }
                },
                wanted = { wantedSections.value },
                location = { resolver.resolve(settingsForWeather) },
                quickLocation = { resolver.resolve(settingsForWeather, allowGeocode = false) },
            ),
        )
    }
    // A new place (or one just allowed) is picked up straight away, not at the next minute.
    LaunchedEffect(settings.weatherPlace, settings.weatherLat, settings.weatherLon) { pageletRuntime.provider.kick() }
    val allPagelets = workspace.filter { isPageletId(it.id) }.map {
        PageletPlacement(it.id, pagerIndexOf(it.page), pageletConfigs[it.id] ?: PageletConfig.defaultsFor(pageletTypeOf(it.id) ?: PageletType.SKY))
    }
    // Only the layered kind (Today) is drawn by the layers behind and above the pager; the rest draw themselves in the pager.
    val pageletPlacements = allPagelets.filter { pageletTypeOf(it.id)?.isLayered != false }
    val surfacePages by rememberUpdatedState(allPagelets.filter { it !in pageletPlacements }.map { it.pagerIndex })
    val surfaceHidingPages by rememberUpdatedState(allPagelets.filter { it !in pageletPlacements && it.config.hideDock }.map { it.pagerIndex })
    val wantedNow = pageletPlacements.flatMapTo(HashSet()) { it.config.sections }
    SideEffect { wantedSections.value = wantedNow }
    LaunchedEffect(wantedNow) { pageletRuntime.provider.kick() }
    // The launcher's own icon for each app, by package, for the Today page's busiest-apps row.
    val iconByPackage = remember(apps) { apps.groupBy { it.packageName }.mapValues { (_, v) -> v.first().icon } }
    var showActivityLog by remember { mutableStateOf(false) }
    var showPageletGallery by remember { mutableStateOf(false) }
    var showPageletManager by remember { mutableStateOf(false) }
    var galleryStart by remember { mutableStateOf<PageletType?>(null) }
    var optionsPageletId by remember { mutableStateOf<String?>(null) }
    val skyPages by rememberUpdatedState(pageletPlacements.map { it.pagerIndex })
    val hidingPages by rememberUpdatedState(pageletPlacements.filter { it.config.hideDock }.map { it.pagerIndex })
    LaunchedEffect(pageletPlacements.map { it.id }) { pageletRuntime.forget(pageletPlacements.map { it.id }.toSet()) }

    fun resolveTarget(p: Offset): DropTarget? = when {
        dockRect.contains(p) -> {
            val slotW = dockRect.width / settings.dockSlots
            val index = ((p.x - dockRect.left) / slotW).toInt().coerceIn(0, settings.dockSlots - 1)
            DropTarget.Dock(dockPager.currentPage * settings.dockSlots + index)
        }
        pagerRect.contains(p) -> {
            val col = ((p.x - pagerRect.left) / (pagerRect.width / settings.columns)).toInt()
            val row = ((p.y - pagerRect.top) / (pagerRect.height / settings.rows)).toInt()
            workspacePageAt(pagerState.currentPage)?.let { DropTarget.Cell(it, col, row) }
        }
        else -> null
    }

    fun runGesture(action: GestureAction, key: String) {
        if (viewModel.perform(action, key)) return
        when (action) {
            GestureAction.AppDrawer -> scope.launch { drawer.animateTo(1f) }
            GestureAction.Search -> { drawer.focusSearch = true; scope.launch { drawer.animateTo(1f) } }
            GestureAction.Lasso -> { editMode = false; viewModel.lassoStart() }
            else -> Unit
        }
    }
    val latestSettings by rememberUpdatedState(settings)
    var startedClosed by remember { mutableStateOf(true) }

    // Dwelling over another app or folder shows that dropping will group them.
    val latestWorkspace by rememberUpdatedState(workspace)
    val latestDock by rememberUpdatedState(dock)
    val latestResolve by rememberUpdatedState(::resolveTarget)
    val hoverId by remember {
        derivedStateOf {
            val dragged = drag.id
            if (!drag.lifted || dragged == null || isFolderId(dragged) ||
                (drag.fromFolder != null && drag.folderBounds?.contains(drag.pos) == true)) null
            else when (val t = latestResolve(drag.pos)) {
                is DropTarget.Cell -> latestWorkspace.firstOrNull { it.id != dragged && it.occupies(t.page, t.col, t.row) }
                is DropTarget.Dock -> latestDock.firstOrNull { it.id != dragged && it.col == t.slot }
                else -> null
            }?.id?.takeUnless { isAnyWidgetId(it) }
        }
    }
    val hoverHaptics = LocalHapticFeedback.current
    val latestHapticsOn = LocalHapticsEnabled.current
    LaunchedEffect(hoverId) {
        drag.mergeTarget = null
        if (hoverId != null) {
            delay(140)
            drag.mergeTarget = hoverId
            hoverHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    SideEffect {
        drag.onDrop = { id, at, fromFolder, fromDrawer ->
            val target = resolveTarget(at)
            if (target != null && latestHapticsOn) hoverHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (fromDrawer) {
                if (target != null) viewModel.dropFromDrawer(id, target)
            } else if (fromFolder != null) {
                // Dropped back on its own folder: leave everything as it was and keep the folder open.
                if (drag.folderBounds?.contains(at) != true) {
                    openFolderId = null
                    if (target != null) viewModel.dropFromFolder(fromFolder, id, target)
                }
            } else if (target != null) {
                viewModel.drop(id, target)
            }
        }
    }

    // Dragging an icon out of the drawer: slide the drawer away, but keep it composed so the gesture survives.
    SideEffect { drawer.keepComposed = drag.lifted && drag.fromDrawer }
    LaunchedEffect(drag.lifted) { if (drag.lifted && drag.fromDrawer) drawer.animateTo(0f) }

    // Wallpaper follows the page position.
    val view = LocalView.current
    LaunchedEffect(settings.wallpaperScroll) {
        val wallpaper = WallpaperManager.getInstance(view.context)
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction to pageCountState.value }
            .collect { (position, pages) ->
                val offset = if (!settings.wallpaperScroll || pages <= 1) 0.5f else (position / (pages - 1)).coerceIn(0f, 1f)
                runCatching {
                    wallpaper.setWallpaperOffsetSteps(1f / (pages - 1).coerceAtLeast(1), 1f)
                    wallpaper.setWallpaperOffsets(view.windowToken, offset, 0.5f)
                }
            }
    }

    // Home button: close the drawer and return to the first page.
    LaunchedEffect(Unit) {
        viewModel.homePressed.collect {
            if (lasso.active) { viewModel.lassoDone(); return@collect }
            val alreadyHome = !drawer.isOpen && pagerState.currentPage == defaultPage
            drawer.animateTo(0f)
            pagerState.animateScrollToPage(defaultPage.coerceIn(0, (pageCountState.value - 1).coerceAtLeast(0)))
            if (alreadyHome) runGesture(latestSettings.homePress, "homePress")
        }
    }

    // Holding a dragged icon near a screen edge flips pages.
    LaunchedEffect(drag.lifted) {
        if (!drag.lifted) return@LaunchedEffect
        var side = 0
        var dwell = 0
        while (true) {
            delay(100)
            val x = drag.pos.x
            val overDock = dockRect.contains(drag.pos)
            val s = when {
                x < pagerRect.left + edgePx -> -1
                x > pagerRect.right - edgePx -> 1
                else -> 0
            }
            if (s != 0 && s == side) {
                dwell += 100
                if (dwell >= 600) {
                    dwell = 0
                    val state = if (overDock) dockPager else pagerState
                    val count = if (overDock) dockPageCount.value else pageCountState.value
                    val target = state.currentPage + s
                    if (target in 0 until count) state.animateScrollToPage(target)
                }
            } else {
                side = s
                dwell = 0
            }
        }
    }

    // The Library page (right of Home) is full-height: Home's header and dock collapse away as it arrives.
    val libraryFraction: () -> Float = {
        val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
        val library = if (libraryOn) (1f - kotlin.math.abs(pos - libraryIndex)).coerceIn(0f, 1f) else 0f
        // A page that draws itself and uses the whole page (Headlines) takes the dock away just as the Library does.
        maxOf(library, dockHideFraction(pos, surfaceHidingPages))
    }
    // A Pagelet that uses the whole page takes the dock away in proportion to how far it has scrolled in.
    val pageletFraction: () -> Float = { dockHideFraction(pagerState.currentPage + pagerState.currentPageOffsetFraction, hidingPages) }
    val dockFraction: () -> Float = { maxOf(libraryFraction(), pageletFraction()) }
    // A page that draws itself on a surface (Headlines) paints the system-bar areas and the dock's backdrop the same, dock or no dock.
    val surfaceFraction: () -> Float = { dockHideFraction(pagerState.currentPage + pagerState.currentPageOffsetFraction, surfacePages) }
    val barTint: () -> Float = { maxOf(libraryFraction(), surfaceFraction()) }
    // Any Today page, dock or no dock: the sky is always dark, so bar icons and dots go light over it.
    val skyFraction: () -> Float = { dockHideFraction(pagerState.currentPage + pagerState.currentPageOffsetFraction, skyPages) }
    // Over the Library's light/dark surface, system-bar icons must contrast with it, not with the wallpaper.
    val surfaceIsLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    LaunchedEffect(surfaceIsLight, settings.darkStatusIcons, barsOwnedElsewhere) {
        if (barsOwnedElsewhere) return@LaunchedEffect
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        snapshotFlow { (barTint() > 0.5f) to (skyFraction() > 0.5f) }.collect { (onLibrary, onPagelet) ->
            // The sky is always dark: its icons are light, whatever the wallpaper setting says.
            controller.isAppearanceLightStatusBars = if (onPagelet) false else if (onLibrary) surfaceIsLight else settings.darkStatusIcons
            controller.isAppearanceLightNavigationBars = if (onPagelet) false else if (onLibrary) surfaceIsLight else false
        }
    }

    // The second-finger handler is created once; it must always see the page counts of the moment, not of its first frame.
    val turnPageWithSecondFinger = rememberUpdatedState<(Int) -> Boolean> { dir ->
        // A held icon that hasn't been dragged yet is picked up now, so it travels with the page turn. Lifting also offers
        // the spare page on the right, which appears a frame later: this sample is refused and the swipe is retried.
        if (!drag.lifted) drag.lifted = true
        val overDock = dockRect.contains(drag.pos)
        val state = if (overDock) dockPager else pagerState
        val count = if (overDock) dockPageCount.value else pageCountState.value
        val target = state.currentPage + dir
        // An icon can't be carried onto the News feed or the Library.
        if (state.isScrollInProgress || target !in 0 until count || (!overDock && workspacePageAt(target) == null)) false
        else { scope.launch { state.animateScrollToPage(target) }; true }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // Soft gradients keep the clock and dock legible on bright wallpapers.
                if (settings.readabilityScrim && !settings.darkHomeText) {
                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.28f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f)))
                }
            }
            .secondFingerPages(
                thresholdPx = with(density) { 56.dp.toPx() },
                active = { drag.id != null && !lasso.active },
            ) { dir -> turnPageWithSecondFinger.value(dir) }
            .pinchIn { if (!drag.lifted && !drawer.isOpen && !lasso.active) { showOverview = true } }
            .onGloballyPositioned { drawer.heightPx = it.size.height.toFloat() }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    // When swipe-up is remapped, don't let an upward drag pull the drawer open.
                    if (delta > 0f || drawer.isOpen || settings.swipeUp == GestureAction.AppDrawer) drawer.dragBy(delta)
                },
                enabled = !drag.lifted && !lasso.active,
                onDragStarted = { startedClosed = !drawer.isOpen },
                onDragStopped = { velocity ->
                    if (startedClosed && velocity > SwipeDownVelocity && !drawer.isOpen) {
                        runGesture(settings.swipeDown, "swipeDown")
                    } else if (startedClosed && velocity < -SwipeDownVelocity && !drawer.isOpen &&
                        settings.swipeUp != GestureAction.AppDrawer
                    ) {
                        runGesture(settings.swipeUp, "swipeUp")
                    } else {
                        drawer.settle(velocity)
                    }
                },
            )
    ) {
        // The sky is drawn full-screen behind the pager and the dock, sliding with the pages.
        PageletBackdropLayer(
            runtime = pageletRuntime, placements = pageletPlacements,
            pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            stride = { if (pagerRect.width > 0f) pagerRect.width else 1f },
            layerAlpha = { 1f - drawer.progress },
            env = pageletEnv,
        )
        // Behind the pager and the dock: the surface colour of a Headlines page, so the page and the bars around it read as one.
        // Self-drawn Pagelets paint their wash here, edge to edge (status bar and dock included), sliding with the pages.
        com.snapkode.nivolauncher.ui.pagelet.SurfaceBackdropLayer(
            placements = allPagelets.filter { it !in pageletPlacements },
            pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            stride = { if (pagerRect.width > 0f) pagerRect.width else 1f },
            layerAlpha = { 1f - drawer.progress },
        )
        // The dock slides away on its own while the Library or a surface page arrives; under it the same surface takes over, so
        // no band of wallpaper is left where the dock was.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(with(density) { dockHeightPx.toDp() } + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp)
                .graphicsLayer { alpha = libraryFraction() * (1f - drawer.progress) }
                .background(MaterialTheme.colorScheme.surface)
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Home recedes as the drawer rises.
                    val p = drawer.progress
                    val f = (1f - 0.06f * p) * editScale
                    scaleX = f; scaleY = f; alpha = 1f - p
                    // Fade each child instead of rendering the whole page offscreen first: much cheaper per frame.
                }
                .windowInsetsPadding(WindowInsets.systemBars)
                .then(if (lasso.active) Modifier.clearAndSetSemantics { } else Modifier)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = settings.homePaddingH.dp, vertical = settings.homePaddingV.dp)
                    .onGloballyPositioned { pagerFull = it.boundsInRoot() },
                // Two neighbours each side stay ready, so a swipe never builds a page mid-motion; more only cost memory and work.
                beyondViewportPageCount = 2,
                userScrollEnabled = !lasso.active,
            ) { page ->
                val parallax = LocalMotion.current.parallax
                Box(
                    Modifier.graphicsLayer {
                        if (!parallax) return@graphicsLayer
                        // Neighbouring pages recede slightly as you swipe.
                        val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .let { kotlin.math.abs(it) }.coerceIn(0f, 1f)
                        val f = 1f - 0.08f * distance
                        scaleX = f; scaleY = f; alpha = 1f - 0.45f * distance
                        }
                ) {
                val workspacePage = workspacePageAt(page)
                val dotsDp = with(density) { dotsReservePx.toDp() }
                val chromeDp = with(density) { chromePx.toDp() }
                if (workspacePage == null) {
                    // The Library reaches down to the dots: the dock slides away as it arrives.
                    Box(Modifier.fillMaxSize().padding(bottom = dotsDp)) { LibraryPage(viewModel) }
                } else {
                    // A Headlines page that uses the whole page does too; every other page leaves room for the dock.
                    val wholePage = itemsByPage[workspacePage].orEmpty().any { pageletTypeOf(it.id)?.isLayered == false && pageletConfigs[it.id]?.hideDock == true }
                    Box(Modifier.fillMaxSize().padding(bottom = if (wholePage) dotsDp else chromeDp)) { WorkspacePage(
                    // The same list instance per page while nothing on it changed, so untouched pages skip recomposition.
                    items = itemsByPage[workspacePage].orEmpty(),
                    appsById = appsById,
                    folders = folders,
                    onOpenFolder = { id, rect -> drag.openFolderOrigin = rect; openFolderId = id },
                    viewModel = viewModel,
                    drag = drag,
                    columns = settings.columns,
                    rows = settings.rows,
                    iconScale = settings.iconScale,
                    showLabels = settings.showLabels,
                    onLongPressEmpty = {
                        if (settings.longPressEmpty == com.snapkode.nivolauncher.data.EmptyLongPress.Lasso && !settings.lockHome) viewModel.lassoStart()
                        else editMode = true
                    },
                    onLongPressPagelet = { editMode = true },
                    onDoubleTap = { runGesture(settings.doubleTap, "doubleTap") },
                    onPageletOptions = { optionsPageletId = it },
                    // The page is gone: land on the one before it, not wherever the pager's index now points.
                    onPageletRemoved = { removed -> scope.launch { pagerState.animateScrollToPage(pagerIndexOf((removed - 1).coerceAtLeast(0))) } },
                    roundedWidgets = settings.roundedWidgets,
                    onWidgetLift = { widgetLifted = it },
                ) }
                }
                }
            }
            // Dots and dock float over the bottom edge. They slide and fade at draw time only; the pager above never resizes.
            val dockOnSurface by remember { derivedStateOf { surfaceFraction() > 0.5f } }
            val dockInk = if (dockOnSurface) MaterialTheme.colorScheme.onSurface else LocalHomeTextColor.current
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (dotsShownNow) PageDots(
                    pageCount, { pagerState.currentPage }, barTint, skyFraction,
                    modifier = Modifier
                        .onSizeChanged { dotsHeightPx = it.height }
                        .graphicsLayer { translationY = dockHeightPx * dockFraction() },
                )
                CompositionLocalProvider(LocalHomeTextColor provides dockInk) {
                    Column(Modifier.slideAway(dockFraction)) {
                        if (settings.showDock) DockBar(
                            settings = settings,
                            pagerState = dockPager,
                            items = dock,
                            appsById = appsById,
                            folders = folders,
                            onOpenFolder = { id, rect -> drag.openFolderOrigin = rect; openFolderId = id },
                            viewModel = viewModel,
                            drag = drag,
                            modifier = Modifier.onGloballyPositioned { dockRect = it.boundsInRoot() }.onSizeChanged { dockHeightPx = it.height },
                        )
                    }
                }
            }
        }

        PageletContentLayer(
            runtime = pageletRuntime, placements = pageletPlacements,
            pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            stride = { if (pagerRect.width > 0f) pagerRect.width else 1f },
            layerAlpha = { 1f - drawer.progress },
            env = pageletEnv,
            onOptions = { optionsPageletId = it },
            onActivityLog = { showActivityLog = true },
            dockHeight = with(density) { dockHeightPx.toDp() },
            dotsShown = (basePages + (if (libraryOn) 1 else 0)) > 1 && settings.showPageDots,
            iconFor = { pkg -> pkg?.let(iconByPackage::get) },
        )

        // While the Library is showing, tint the system-bar areas so no wallpaper strip shows above or below it.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(with(density) { WindowInsets.statusBars.getTop(density).toDp() })
                .graphicsLayer { alpha = libraryFraction() }
                .background(MaterialTheme.colorScheme.surface)
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(with(density) { WindowInsets.navigationBars.getBottom(density).toDp() })
                .graphicsLayer { alpha = libraryFraction() }
                .background(MaterialTheme.colorScheme.surface)
        )

        // Opening the drawer is a swipe; give screen-reader and switch users an equivalent action.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(48.dp)
                .semantics {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = "Open all apps"
                    onClick { scope.launch { drawer.animateTo(1f) }; true }
                }
        )
        // Lasso selection: owns every touch over the workspace while it is on.
        if (lasso.active) {
            LassoLayer(
                viewModel = viewModel,
                session = lasso,
                pagerRect = pagerRect,
                workspaceItems = workspace,
                appsById = appsById,
                folders = folders,
                settings = settings,
                targetPage = { workspacePageAt(pagerState.targetPage) },
                currentPage = {
                    // Null while the pager is between pages, so a stroke can never be hit-tested against a moving target.
                    if (pagerState.isScrollInProgress || kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.02f) null
                    else workspacePageAt(pagerState.currentPage)
                },
                workspacePageCount = workspaceCount,
                openPage = { page -> scope.launch { pagerState.animateScrollToPage(pagerIndexOf(page)) } },
            )
        }
        // A few seconds after a batch action, an Undo stays within reach even after leaving the mode.
        LaunchedEffect(lassoUndo, lasso.active) {
            if (!lasso.active && lassoUndo != null) { delay(6000); viewModel.lassoClearUndo() }
        }
        if (showActivityLog) {
            com.snapkode.nivolauncher.ui.pagelet.ActivityLogScreen(onBack = { showActivityLog = false }, iconFor = { pkg -> pkg?.let(iconByPackage::get) })
        }
        if (showPageletGallery) {
            PageletGallerySheet(
                checkFeed = { viewModel.checkFeed(it) },
                taken = PageletType.entries.filterTo(HashSet()) { viewModel.hasPagelet(it) },
                initial = galleryStart,
                onAdd = { type, config ->
                    showPageletGallery = false
                    val page = viewModel.addPagelet(type, config)
                    if (page != null) scope.launch { delay(200); pagerState.animateScrollToPage(pagerIndexOf(page)) }
                    else android.widget.Toast.makeText(view.context, if (settings.lockHome) "Home layout is locked" else "That Pagelet is already on home", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showPageletGallery = false },
            )
        }
        if (showPageletManager) {
            com.snapkode.nivolauncher.ui.pagelet.PageletManagerSheet(
                placed = allPagelets.mapNotNull { p -> pageletTypeOf(p.id)?.let { t -> com.snapkode.nivolauncher.ui.pagelet.PlacedPagelet(p.id, t, items.firstOrNull { it.id == p.id }?.page ?: 0) } },
                locked = settings.lockHome,
                onAdd = { type -> showPageletManager = false; galleryStart = type; showPageletGallery = true },
                onCustomise = { id -> showPageletManager = false; optionsPageletId = id },
                onRemove = { id ->
                    val page = items.firstOrNull { it.id == id }?.page
                    viewModel.removePagelet(id)
                    if (page != null) scope.launch { pagerState.animateScrollToPage(pagerIndexOf((page - 1).coerceAtLeast(0))) }
                },
                onDismiss = { showPageletManager = false },
            )
        }
        optionsPageletId?.let { id ->
            val weatherState by pageletRuntime.provider.weatherState.collectAsState()
            val weatherUpdated = (weatherState as? com.snapkode.nivolauncher.pagelet.weather.SkyWeatherState.Ready)?.fetchedAtMs
            val type = pageletTypeOf(id)
            if (type == null) optionsPageletId = null else PageletOptionsSheet(
                type = type, config = pageletConfigs[id] ?: PageletConfig.defaultsFor(type),
                onChange = { c -> viewModel.updatePagelet(id) { c } },
                onDismiss = { optionsPageletId = null },
                weather = { com.snapkode.nivolauncher.ui.pagelet.WeatherLocationCard(viewModel, weatherUpdated) },
                debug = { PageletDebugControls() },
                checkFeed = { viewModel.checkFeed(it) },
                onRemove = if (settings.lockHome) null else ({
                    val page = items.firstOrNull { it.id == id }?.page
                    optionsPageletId = null
                    viewModel.removePagelet(id)
                    if (page != null) scope.launch { pagerState.animateScrollToPage(pagerIndexOf((page - 1).coerceAtLeast(0))) }
                }),
            )
        }
        // Undo pills stack instead of overlapping, just above the dock (or the dots when the dock is away).
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = with(density) { dockHeightPx.toDp() } + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PageletUndoPill(
                visible = pageletUndo != null,
                onUndo = { viewModel.undoRemovePagelet()?.let { p -> scope.launch { pagerState.animateScrollToPage(pagerIndexOf(p)) } } },
                onExpire = { viewModel.clearPageletUndo() },
            )
            LassoUndoPill(label = lassoUndo.takeIf { !lasso.active }, onUndo = { viewModel.lassoUndoLast() })
        }
        // While an icon is still over its own folder, nothing is being removed from home yet.
        val overOwnFolder = drag.fromFolder != null && drag.folderBounds?.contains(drag.pos) == true
        if (drag.lifted && !overOwnFolder) {
            val half = with(density) { (32 * settings.iconScale).dp.toPx() }
            val ghostModifier = Modifier.offset { IntOffset((drag.pos.x - half).toInt(), (drag.pos.y - half).toInt()) }
            val dragged = drag.id
            val lift by animateFloatAsState(1.18f, LocalMotion.current.spring(1.2f), label = "lift")
            if (dragged != null && dragged in folders) {
                FolderIconView(folders.getValue(dragged), appsById, settings.iconScale * lift, false, ghostModifier)
            } else {
                dragged?.let(appsById::get)?.let {
                    AppIconView(it, settings.iconScale * lift, showLabel = false, onWallpaper = true, modifier = ghostModifier)
                }
            }
        }
    }

    val sheetShown = editMode && !showOverview
    // The page under the sheet: a Pagelet page has no icons to select.
    val onPageletPage = workspacePageAt(pagerState.currentPage)?.let { p -> workspace.any { it.page == p && isPageletId(it.id) } } == true
    androidx.compose.animation.AnimatedVisibility(
        visible = sheetShown,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .semantics { contentDescription = "Close editing"; role = androidx.compose.ui.semantics.Role.Button }
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { editMode = false }
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        androidx.compose.animation.AnimatedVisibility(
            visible = sheetShown,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it },
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { it },
        ) {
            EditSheet(
                onWallpaper = { editMode = false; viewModel.pickWallpaper() },
                onWidgets = { editMode = false; showWidgets = true },
                onPages = { showOverview = true },
                onLasso = { editMode = false; viewModel.lassoStart() },
                onPagelet = {
                    editMode = false
                    showPageletManager = true
                },
                onStyle = { editMode = false; onOpenSettings("colours") },
                onSettings = { editMode = false; onOpenSettings(null) },
                onDone = { editMode = false },
                widgetsEnabled = !settings.lockHome && !onPageletPage,
                lassoEnabled = !settings.lockHome && !onPageletPage,
                pageletEnabled = true,
                pagesEnabled = !settings.lockHome,
            )
        }
    }
    if (showOverview) {
        PageOverview(
            viewModel = viewModel,
            appsById = appsById,
            currentPage = workspacePageAt(pagerState.currentPage) ?: -1,
            columns = settings.columns,
            rows = settings.rows,
            onSelect = { page ->
                showOverview = false; editMode = false
                scope.launch { pagerState.animateScrollToPage(pagerIndexOf(page)) }
            },
            onClose = { showOverview = false; editMode = false },
        )
    }

    if (showWidgets) WidgetPicker(viewModel) { showWidgets = false }

    openFolderId?.let { id ->
        val folder = folders[id]
        if (folder == null) openFolderId = null
        else FolderPopup(folder, appsById, viewModel, drag, settings, onDismiss = { openFolderId = null })
    }
}

@Composable
private fun WorkspacePage(
    items: List<HomeItem>,
    appsById: Map<String, AppInfo>,
    folders: Map<String, HomeFolder>,
    onOpenFolder: (String, Rect?) -> Unit,
    viewModel: LauncherViewModel,
    drag: DragState,
    columns: Int,
    rows: Int,
    iconScale: Float,
    showLabels: Boolean,
    onLongPressEmpty: () -> Unit,
    onLongPressPagelet: () -> Unit,
    onDoubleTap: () -> Unit,
    onPageletOptions: (String) -> Unit,
    onPageletRemoved: (Int) -> Unit,
    roundedWidgets: Boolean,
    onWidgetLift: (Boolean) -> Unit,
    ) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { if (items.any { isPageletId(it.id) }) onLongPressPagelet() else onLongPressEmpty() },
                    onDoubleTap = { onDoubleTap() },
                )
            }
    ) {
        val density = LocalDensity.current
        // Icons cascade in the first time a page appears; anything added later (a drop, a dissolved folder) pops in at once.
        var firstShow by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(900); firstShow = false }
        val cellW = constraints.maxWidth / columns
        val cellH = constraints.maxHeight / rows
        val cellWDp = with(density) { cellW.toDp() }
        val cellHDp = with(density) { cellH.toDp() }
        items.forEach { item ->
            if (isPageletId(item.id)) {
                androidx.compose.runtime.key(item.id) {
                    // Every kind but Today draws itself here; Today is drawn by the layers around the pager, so its slot is just the swipeable page.
                    val type = pageletTypeOf(item.id)
                    if (type != null && !type.isLayered) com.snapkode.nivolauncher.ui.pagelet.PageletPage(viewModel, item.id, type) { onPageletOptions(item.id) }
                    else androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
                }
                return@forEach
            }
            if (isAnyWidgetId(item.id)) {
                androidx.compose.runtime.key(item.id) {
                    WidgetCell(
                        item = item,
                        viewModel = viewModel,
                        width = cellWDp * item.spanX,
                        height = cellHDp * item.spanY,
                        cellWidthPx = cellW.toFloat(),
                        cellHeightPx = cellH.toFloat(),
                        rounded = roundedWidgets,
                        onLiftChange = onWidgetLift,
                        modifier = Modifier.offset { IntOffset(item.col * cellW, item.row * cellH) },
                    )
                }
                return@forEach
            }
            androidx.compose.runtime.key(item.id) {
                val target = IntOffset(item.col * cellW, item.row * cellH)
                val animated by animateIntOffsetAsState(target, LocalMotion.current.spring(0.7f), label = "cell")
                // An icon the lasso just dropped is already where it belongs (its ghost animated there), so it must not glide again.
                val lassoSnap = LocalLasso.current?.let { item.id in it.snapIds } == true
                val placed = if (lassoSnap) target else animated
                // Icons pop in one after another the first time a page is shown.
                val motion = LocalMotion.current
                val cascade = firstShow
                // An icon the lasso just dropped on another page is new to this page's composition, but its ghost is already
                // showing it: it must not fade or pop in again.
                val appear = remember { androidx.compose.animation.core.Animatable(if (lassoSnap || !(motion.stagger || !cascade)) 1f else 0f) }
                LaunchedEffect(Unit) {
                    if (!lassoSnap && (motion.stagger || !cascade)) {
                        if (cascade) delay((item.row * columns + item.col) * 22L)
                        appear.animateTo(1f, motion.spring(if (cascade) 0.6f else 1.1f))
                    }
                }
                HomeEntry(
                    id = item.id,
                    appsById = appsById,
                    folders = folders,
                    onOpenFolder = onOpenFolder,
                    viewModel = viewModel,
                    drag = drag,
                    iconScale = iconScale,
                    showLabel = showLabels,
                    modifier = Modifier
                        .offset { placed }
                        .size(cellWDp, cellHDp)
                        .graphicsLayer {
                            val a = appear.value
                            alpha = a.coerceIn(0f, 1f)
                            val sc = 0.8f + 0.2f * a
                            scaleX = sc; scaleY = sc
                        },
                )
            }
        }
    }
}

@Composable
private fun DockBar(
    settings: LauncherSettings,
    pagerState: PagerState,
    items: List<HomeItem>,
    appsById: Map<String, AppInfo>,
    folders: Map<String, HomeFolder>,
    onOpenFolder: (String, Rect?) -> Unit,
    viewModel: LauncherViewModel,
    drag: DragState,
    modifier: Modifier = Modifier,
) {
    val slots = settings.dockSlots
    val tint = settings.dockTint.color()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + settings.dockBottomSpace.dp)
    ) {
        val shape = RoundedCornerShape(settings.dockRadius.dp)
        val dockModifier = when (settings.dockStyle) {
            DockStyle.Floating -> Modifier
                .background(tint.copy(alpha = settings.dockOpacity), shape)
            DockStyle.Solid -> Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f), shape)
            DockStyle.Minimal -> Modifier
        }
        Box(modifier.fillMaxWidth().then(dockModifier)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                beyondViewportPageCount = settings.dockPages,
            ) { page ->
                Row(Modifier.padding(vertical = 8.dp)) {
                    for (index in 0 until slots) {
                        val slot = page * slots + index
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val item = items.firstOrNull { it.col == slot }
                            if (item != null) {
                                HomeEntry(
                                    item.id, appsById, folders, onOpenFolder, viewModel, drag,
                                    settings.iconScale, showLabel = settings.dockLabels, modifier = Modifier,
                                )
                            } else {
                                // An empty dock slot shows a faint dot, so a half-full dock still reads as balanced.
                                Box(Modifier.size((56 * settings.iconScale).dp), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(6.dp).background(LocalHomeTextColor.current.copy(alpha = 0.28f), CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (settings.dockPages > 1) PageDots(settings.dockPages, { pagerState.currentPage })
    }
}

/** Renders either an app or a folder in a draggable, tappable, long-pressable cell. */
@Composable
private fun HomeEntry(
    id: String,
    appsById: Map<String, AppInfo>,
    folders: Map<String, HomeFolder>,
    onOpenFolder: (String, Rect?) -> Unit,
    viewModel: LauncherViewModel,
    drag: DragState,
    iconScale: Float,
    showLabel: Boolean,
    modifier: Modifier,
) {
    val locked = LocalHomeLocked.current
    val selection = LocalSelection.current
    val folder = folders[id]
    val app = appsById[id]
    // The open folder's tile hides while its card is up, but comes back once an icon is dragged out of the card,
    // so the folder never seems to vanish mid-drag. It only changes (or collapses to an icon) when the drag ends.
    val draggedOutOfThis = drag.lifted && drag.fromFolder == id && drag.folderBounds?.contains(drag.pos) != true
    val lasso = LocalLasso.current
    val lassoSelected by remember(id, lasso) { derivedStateOf { lasso != null && id in lasso.selected } }
    val lassoCarried by remember(id, lasso) { derivedStateOf { lasso != null && id in lasso.dragIds } }
    val lassoScale by animateFloatAsState(
        // Carried icons keep their selected scale while hidden, so the hand-off from ghost to icon at drop is the same size.
        if (lassoSelected && !LocalMotion.current.reduced) com.snapkode.nivolauncher.lasso.LassoMotion.SELECTED_SCALE else 1f,
        LocalMotion.current.spring(1.2f), label = "lassoSelectedScale",
    )
    val hidden = Modifier
        .mergeHighlight(drag.mergeTarget == id)
        .hiddenWhen(lassoCarried)
        .graphicsLayer { scaleX = lassoScale; scaleY = lassoScale }
        .springVisible(!((drag.lifted && drag.id == id) || (drag.openFolderId == id && !draggedOutOfThis)))
    when {
        folder != null -> DragCell(
            id, drag, modifier, description = "Folder ${folder.name}, ${folder.appIds.size} apps",
            onTap = { r -> onOpenFolder(folder.id, Rect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())) },
            menu = { expanded, dismiss ->
                NivoMenu(expanded = expanded, onDismiss = dismiss) {
                    NivoMenuItem("Open", MenuIcons.Open, { dismiss(); onOpenFolder(folder.id, null) })
                    if (!locked) {
                        NivoMenuItem("Select", menuIconFor("Select"), { dismiss(); viewModel.lassoStartWith(folder.id) })
                        NivoMenuItem("Dissolve folder", menuIconFor("Move out"), { dismiss(); viewModel.dissolveFolder(folder.id) })
                        NivoMenuItem("Remove", menuIconFor("Remove"), { dismiss(); viewModel.removeFromHome(folder.id) }, destructive = true)
                    }
                }
            },
        ) {
            // While an icon is being pulled out, the tile already shows the folder without it.
            val shown = if (drag.lifted && drag.fromFolder == id) folder.copy(appIds = folder.appIds - setOfNotNull(drag.id)) else folder
            FolderIconView(shown, appsById, iconScale, showLabel, hidden)
        }

        app != null -> DragCell(
            id, drag, modifier, description = app.label,
            onTap = { bounds -> viewModel.launch(app, bounds) },
            menu = { expanded, dismiss ->
                AppMenu(
                    expanded = expanded,
                    app = app,
                    viewModel = viewModel,
                    extraActions = if (locked) emptyList() else listOf(
                        MenuAction("Select") { viewModel.lassoStartWith(app.id) },
                        MenuAction("Remove") { viewModel.removeFromHome(app.id) },
                    ),
                    onDismiss = dismiss,
                )
            },
        ) { AppIconView(app, iconScale, showLabel, onWallpaper = true, modifier = hidden) }
    }
}

@Composable
internal fun DragCell(
    id: String,
    drag: DragState,
    modifier: Modifier,
    description: String = "",
    fromFolder: String? = null,
    fromDrawer: Boolean = false,
    onTap: (android.graphics.Rect) -> Unit,
    menu: @Composable (expanded: Boolean, dismiss: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    // Once the icon is lifted (dragged, or carried by a second finger's page turn) its menu has no business being open.
    LaunchedEffect(drag.lifted) { if (drag.lifted && drag.id == id) menuOpen = false }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // The icon itself (not the whole grid cell), which the long-press spotlight wraps.
    var lit by remember { mutableStateOf(Rect.Zero) }
    val lassoSession = LocalLasso.current
    DisposableEffect(id, lassoSession) { onDispose { lassoSession?.unregisterTile(id) } }
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val slop = LocalViewConfiguration.current.touchSlop
    val currentOnTap by rememberUpdatedState(onTap)
    val locked by rememberUpdatedState(LocalHomeLocked.current)
    val selection = LocalSelection.current
    val mySource = if (fromDrawer) SelectionSource.Drawer else SelectionSource.Home
    // Selecting applies to the screen it started on; icons inside an open folder keep their normal behaviour.
    val selecting by rememberUpdatedState(selection != null && selection.active && selection.source == mySource && fromFolder == null)
    var pressed by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    val pressScale by animateFloatAsState(
        if (pressed) motion.pressScale else 1f,
        motion.spring(1.4f),
        label = "press",
    )

    Box(
        modifier
            .onGloballyPositioned {
                origin = it.positionInRoot()
                bounds = it.boundsInRoot()
            }
            // Touch handling below is raw pointer input, which screen readers can't see, so expose the
            // same actions as semantics.
            .semantics {
                role = androidx.compose.ui.semantics.Role.Button
                if (description.isNotEmpty()) contentDescription = description
                onClick(label = "Open") {
                    currentOnTap(android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
                    true
                }
                onLongClick(label = "More options") { menuOpen = true; true }
            }
            .pointerInput(id) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }, onTap = {
                    if (selecting) selection?.toggle(id) else currentOnTap(
                        android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
                    )
                })
            }
            .pointerInput(id) {
                var moved = 0f
                var toggledOnly = false
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        moved = 0f
                        toggledOnly = selecting
                        if (toggledOnly) { selection?.toggle(id); return@detectDragGesturesAfterLongPress }
                        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!locked) drag.begin(id, origin + offset, fromFolder, fromDrawer)
                        // The menu appears as soon as the hold registers; dragging away from it cancels it.
                        menuOpen = true
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        if (toggledOnly || locked) return@detectDragGesturesAfterLongPress
                        moved += delta.getDistance()
                        drag.pos += delta
                        if (moved > slop) { drag.lifted = true; menuOpen = false }
                    },
                    onDragEnd = {
                        if (toggledOnly || locked) return@detectDragGesturesAfterLongPress
                        // Released without moving: the menu is already open, so just put the icon back.
                        if (drag.lifted) drag.finish() else { drag.cancel(); menuOpen = true }
                    },
                    onDragCancel = { if (!toggledOnly) drag.cancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // While the menu is open the icon lifts a little, as if picked up.
        val lift by animateFloatAsState(if (menuOpen) 1.1f else 1f, motion.spring(1.2f), label = "menuLift")
        Box(
            Modifier
                .onGloballyPositioned {
                    lit = it.boundsInRoot()
                    // The lasso hit-tests against exactly what is on screen, so it needs each home icon's real rectangle.
                    if (lassoSession != null && !fromDrawer && fromFolder == null) {
                        val b = it.boundsInRoot()
                        lassoSession.registerTile(id, com.snapkode.nivolauncher.lasso.Box(b.left, b.top, b.right, b.bottom))
                    }
                }
                .graphicsLayer { scaleX = pressScale * lift; scaleY = pressScale * lift }
        ) {
            content()
            if (selecting) Box(Modifier.matchParentSize()) { SelectionMark(selected = selection != null && id in selection.selected) }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalMenuAnchor provides (if (menuOpen) lit.takeIf { it != Rect.Zero } else null)) {
            menu(menuOpen) { menuOpen = false }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: () -> Int, surfaceFraction: () -> Float = { 0f }, skyFraction: () -> Float = { 0f }, modifier: Modifier = Modifier) {
    val home = LocalHomeTextColor.current
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Colours follow the swipe at draw time only: the dots never recompose while the pages move.
    fun dotColor(): Color {
        val overSky = androidx.compose.ui.graphics.lerp(home, Color.White, skyFraction())
        return androidx.compose.ui.graphics.lerp(overSky, onSurface, surfaceFraction())
    }
    Row(
        modifier.fillMaxWidth()
            // On a full-height surface page (Library, Headlines) the strip is the same surface, so no wallpaper band shows.
            .drawBehind {
                val f = surfaceFraction()
                if (f > 0f) drawRect(surface.copy(alpha = f))
            }
            .padding(top = 14.dp, bottom = 8.dp).semantics(mergeDescendants = true) {
            contentDescription = "Page ${current() + 1} of $count"
        },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current()
            val size by animateDpAsState(if (selected) 10.dp else 8.dp, LocalMotion.current.spring(), label = "dotSize")
            val alpha by animateFloatAsState(if (selected) 1f else 0.4f, label = "dotAlpha")
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .size(size)
                    .drawBehind { drawCircle(dotColor().copy(alpha = alpha)) }
            )
        }
    }
}


/** Slides the dock down and fades it as [fraction] goes 0 → 1. Draw-time only: it never changes the space it takes. */
private fun Modifier.slideAway(fraction: () -> Float): Modifier = this.graphicsLayer {
    val f = fraction()
    alpha = 1f - f
    translationY = size.height * f
}

/** Shrinks the icon inside a soft rounded halo while another icon is held over it to form a folder. */
@Composable
private fun Modifier.mergeHighlight(active: Boolean): Modifier {
    val scale by animateFloatAsState(if (active) 0.78f else 1f, LocalMotion.current.spring(1f), label = "mergeScale")
    val halo by animateFloatAsState(if (active) 1f else 0f, LocalMotion.current.spring(1f), label = "mergeHalo")
    return this
        .drawBehind {
            if (halo > 0.01f) {
                val pad = 10.dp.toPx() * halo
                val topLeft = Offset(-pad, -pad)
                val size = androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad * 2)
                val radius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.22f * halo), topLeft, size, radius)
                drawRoundRect(Color.White.copy(alpha = 0.85f * halo), topLeft, size, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
}
