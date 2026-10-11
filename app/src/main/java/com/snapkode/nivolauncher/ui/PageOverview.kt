package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.draggable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.heading
import com.snapkode.nivolauncher.ui.pagelet.PageletUndoPill
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.isFolderId
import com.snapkode.nivolauncher.data.isAnyWidgetId
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.pressFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Fires [onPinch] once when two fingers move together by about a third. Never consumes events. */
fun Modifier.pinchIn(onPinch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startSpan = 0f
        var fired = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            if (down.size >= 2 && !fired) {
                val span = hypot(down[0].position.x - down[1].position.x, down[0].position.y - down[1].position.y)
                if (startSpan == 0f) startSpan = span
                else if (span < startSpan * 0.68f) { fired = true; onPinch() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Bottom sheet shown when the workspace (or a Pagelet) is long-pressed: three big tonal tiles and four quick chips.
 * It follows a downward drag and closes past a short distance or a downward fling.
 */
@Composable
fun EditSheet(
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onPages: () -> Unit,
    onLasso: () -> Unit,
    onPagelet: () -> Unit,
    onStyle: () -> Unit,
    onSettings: () -> Unit,
    onDone: () -> Unit,
    widgetsEnabled: Boolean,
    lassoEnabled: Boolean = true,
    pageletEnabled: Boolean = true,
    pagesEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDone)
    val scheme = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val closeAt = with(density) { 72.dp.toPx() }
    val motion = LocalMotion.current
    Surface(
        modifier = modifier.fillMaxWidth()
            .graphicsLayer { translationY = pull.value }
            .draggable(
                orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                state = androidx.compose.foundation.gestures.rememberDraggableState { d -> scope.launch { pull.snapTo((pull.value + d).coerceAtLeast(0f)) } },
                onDragStopped = { v ->
                    if (pull.value > closeAt || v > 1200f) onDone()
                    else pull.animateTo(0f, motion.spring(weight = 1.3f))
                },
            ),
        shape = RoundedCornerShape(topStart = NivoRadius.sheet, topEnd = NivoRadius.sheet),
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = NivoSpace.l, vertical = NivoSpace.m)) {
            // Material drag handle: 32x4, on-surface-variant at 40%.
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = NivoSpace.l).size(width = 32.dp, height = 4.dp)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NivoSpace.m)) {
                EditTile("Style & colours", Icons.Outlined.Palette, onStyle, modifier = Modifier.weight(1f))
                EditTile("Widgets", Icons.Outlined.Widgets, onWidgets, enabled = widgetsEnabled, modifier = Modifier.weight(1f))
                EditTile("Home settings", Icons.Outlined.Home, onSettings, modifier = Modifier.weight(1f))
            }
            // Only shortcuts that act right here; anything that opens Settings lives behind "Home settings".
            Row(Modifier.fillMaxWidth().padding(top = NivoSpace.m), horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                EditChip("Wallpaper", Icons.Outlined.Wallpaper, onWallpaper, modifier = Modifier.weight(1f))
                EditChip("Pages", Icons.Outlined.ViewCarousel, onPages, enabled = pagesEnabled, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = NivoSpace.s, bottom = NivoSpace.s), horizontalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                EditChip("Select", Icons.Outlined.Gesture, onLasso, enabled = lassoEnabled, modifier = Modifier.weight(1f))
                // Stays tappable while dimmed: the tap explains why a Pagelet can't be added right now.
                EditChip("Pagelets", Icons.Outlined.AutoAwesome, onPagelet, dim = !pageletEnabled, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EditTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val container = if (selected) scheme.primaryContainer else scheme.surfaceContainer
    val content = if (selected) scheme.primary else scheme.onSurface
    val shape = RoundedCornerShape(NivoRadius.card)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .heightIn(min = 96.dp)
            .pressFeedback(interaction)
            .alpha(if (enabled) 1f else NivoAlpha.disabled)
            .background(container, shape)
            .border(1.dp, if (selected) scheme.primary.copy(alpha = 0.4f) else scheme.outlineVariant, shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NivoSpace.s, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // The icon sits in a soft tonal disc so the three tiles read as one family.
        Box(
            Modifier.size(40.dp).background(if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) scheme.primary else scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = content, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = NivoSpace.s))
    }
}

@Composable
private fun EditChip(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, dim: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NivoRadius.control)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = NivoTouch.min)
            .pressFeedback(interaction)
            .alpha(if (enabled && !dim) 1f else NivoAlpha.disabled)
            .background(if (selected) scheme.primaryContainer else Color.Transparent, shape)
            .border(1.dp, if (selected) scheme.primary.copy(alpha = 0.4f) else scheme.outlineVariant, shape)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NivoSpace.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            label, Modifier.padding(start = NivoSpace.s), style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Every workspace page as a card: a miniature of the page, its name, and a small bar of controls. */
@Composable
fun PageOverview(
    viewModel: LauncherViewModel,
    appsById: Map<String, AppInfo>,
    currentPage: Int,
    columns: Int,
    rows: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var adding by remember { mutableStateOf(false) }
    val pageCount by viewModel.pages.collectAsState()
    val items by viewModel.items.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val scheme = MaterialTheme.colorScheme
    // This page is opaque, so the system bars must follow the surface rather than the wallpaper.
    val view = androidx.compose.ui.platform.LocalView.current
    val light = scheme.surface.luminance() > 0.5f
    androidx.compose.runtime.DisposableEffect(light) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val prevStatus = controller?.isAppearanceLightStatusBars
        val prevNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = light
        controller?.isAppearanceLightNavigationBars = light
        onDispose {
            prevStatus?.let { controller.isAppearanceLightStatusBars = it }
            prevNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
    val dock = remember(items, settings.dockSlots) { items.filter { it.inDock && it.col < settings.dockSlots } }
    // Tapping empty space closes, quietly: no full-screen ripple behind the cards.
    Box(Modifier.fillMaxSize().background(scheme.surface).clickable(interactionSource = null, indication = null, onClickLabel = "Close pages", onClick = onClose)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = NivoSpace.xl, end = NivoSpace.s, top = 36.dp, bottom = NivoSpace.l), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Pages", style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                    Text(
                        if (pageCount == 1) "1 page" else "$pageCount pages",
                        style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = NivoTouch.min)) {
                    Text("Done", style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                }
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                // Extra bottom room so the Undo pill never sits over the last row.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = NivoSpace.l, end = NivoSpace.l, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(NivoSpace.m),
                verticalArrangement = Arrangement.spacedBy(NivoSpace.m),
            ) {
                gridItems((0 until pageCount).toList(), key = { it }) { page ->
                    val pageItems = items.filter { !it.inDock && it.page == page }
                    val pagelet = pageItems.firstOrNull { com.snapkode.nivolauncher.pagelet.isPageletId(it.id) }
                    PageCard(
                        page = page,
                        pageletType = pagelet?.let { com.snapkode.nivolauncher.pagelet.pageletTypeOf(it.id) },
                        pageItems = pageItems,
                        dock = dock,
                        appsById = appsById,
                        columns = columns,
                        rows = rows,
                        showDock = settings.showDock,
                        isHome = settings.defaultPage == page,
                        isCurrent = page == currentPage,
                        // A locked home can be looked at and opened, not rearranged.
                        canMoveEarlier = !settings.lockHome && page > 0,
                        canMoveLater = !settings.lockHome && page < pageCount - 1,
                        // An empty page can go, and so can a Pagelet's page (the Pagelet is removed, with Undo); a page with icons can't.
                        canDelete = !settings.lockHome && (pagelet != null || (pageCount > 1 && pageItems.isEmpty())),
                        deleteHint = if (pageItems.isNotEmpty() && pagelet == null) "Move its icons off first to delete page ${page + 1}" else null,
                        onOpen = { onSelect(page) },
                        onEarlier = { viewModel.movePage(page, -1) },
                        onLater = { viewModel.movePage(page, 1) },
                        onMakeHome = { viewModel.setDefaultPage(page) },
                        onDelete = { if (pagelet != null) viewModel.removePagelet(pagelet.id) else viewModel.deletePage(page) },
                    )
                }
                if (!settings.lockHome) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    val shape = RoundedCornerShape(24.dp)
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .pressFeedback(interaction)
                            .background(scheme.surfaceContainerLow, shape)
                            .border(1.dp, scheme.outlineVariant, shape)
                            .clip(shape)
                            .semantics { contentDescription = "Add a page" }
                            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button) { adding = true }
                            .padding(horizontal = 20.dp, vertical = NivoSpace.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(40.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Text("Add a page", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.padding(start = NivoSpace.m))
                    }
                }
            }
        }
        // A removed Pagelet page can be brought back from here too.
        val undo by viewModel.pageletUndo.collectAsState()
        PageletUndoPill(
            visible = undo != null,
            onUndo = { viewModel.undoRemovePagelet() },
            onExpire = { viewModel.clearPageletUndo() },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = NivoSpace.xl),
        )
    }
    if (adding) AddPageSheet(
        taken = com.snapkode.nivolauncher.pagelet.PageletType.entries.filterTo(HashSet()) { t -> items.any { com.snapkode.nivolauncher.pagelet.pageletTypeOf(it.id) == t } },
        onBlank = { adding = false; viewModel.addPage() },
        onPagelet = { type -> adding = false; viewModel.addPagelet(type) },
        onDismiss = { adding = false },
    )
}

/** What to add as a new page: an empty one, or a Pagelet that isn't on home yet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddPageSheet(
    taken: Set<com.snapkode.nivolauncher.pagelet.PageletType>,
    onBlank: () -> Unit,
    onPagelet: (com.snapkode.nivolauncher.pagelet.PageletType) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().padding(bottom = NivoSpace.l)) {
            Text(
                "Add a page", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = NivoSpace.xl, end = NivoSpace.xl, top = NivoSpace.s, bottom = NivoSpace.s).semantics { heading() },
            )
            AddRow("Blank page", "An empty page for icons and widgets", Icons.Filled.Add, enabled = true, onClick = onBlank)
            com.snapkode.nivolauncher.pagelet.PageletType.entries.filter { it.available }.forEach { type ->
                val here = type in taken
                AddRow(
                    "${type.title} Pagelet", if (here) "Already on home" else type.tagline, Icons.Outlined.AutoAwesome,
                    enabled = !here, onClick = { onPagelet(type) },
                )
            }
        }
    }
}

@Composable
private fun AddRow(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).alpha(if (enabled) 1f else NivoAlpha.disabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NivoSpace.xl, vertical = NivoSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = NivoSpace.l).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PageCard(
    page: Int,
    pageletType: com.snapkode.nivolauncher.pagelet.PageletType?,
    deleteHint: String?,
    pageItems: List<HomeItem>,
    dock: List<HomeItem>,
    appsById: Map<String, AppInfo>,
    columns: Int,
    rows: Int,
    showDock: Boolean,
    isHome: Boolean,
    isCurrent: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onMakeHome: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Outer 24dp corners with 10dp padding make the preview's 14dp corners concentric.
    val shape = RoundedCornerShape(24.dp)
    val previewShape = RoundedCornerShape(NivoRadius.control)
    val previewPress = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isCurrent) androidx.compose.ui.graphics.lerp(scheme.surfaceContainer, scheme.primaryContainer, 0.5f) else scheme.surfaceContainer, shape)
            .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) scheme.primary.copy(alpha = 0.8f) else scheme.outlineVariant, shape)
            .padding(10.dp),
    ) {
        PagePreview(
            pageItems, dock.takeIf { showDock }.orEmpty(), appsById, columns, rows,
            shape = previewShape,
            modifier = Modifier.fillMaxWidth()
                .pressFeedback(previewPress)
                .clip(previewShape)
                .semantics {
                    contentDescription = (pageletType?.title ?: "Page ${page + 1}")
                    val state = listOfNotNull("Home page".takeIf { isHome }, "Current page".takeIf { isCurrent })
                    if (state.isNotEmpty()) stateDescription = state.joinToString(", ")
                }
                .clickable(interactionSource = previewPress, indication = androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = "Open page", onClick = onOpen),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = NivoSpace.xs, end = NivoSpace.xs, top = 10.dp).heightIn(min = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isHome) Icon(Icons.Filled.Home, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
            if (isHome) Spacer(Modifier.width(6.dp))
            Text(
                pageletType?.title ?: if (isHome) "Home" else "Page ${page + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isCurrent) Box(Modifier.size(8.dp).background(scheme.primary, CircleShape))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = NivoSpace.s).background(scheme.surfaceContainerHigh, CircleShape).padding(horizontal = NivoSpace.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallAction(Icons.AutoMirrored.Filled.ArrowBack, "Move page ${page + 1} earlier", Modifier.weight(1f), enabled = canMoveEarlier, tint = scheme.onSurfaceVariant, onClick = onEarlier)
            SmallAction(
                if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
                if (isHome) "Page ${page + 1} is your home page" else "Make page ${page + 1} your home page",
                Modifier.weight(1f),
                tint = if (isHome) scheme.primary else scheme.onSurfaceVariant, onClick = onMakeHome,
            )
            SmallAction(
                Icons.Filled.Delete,
                if (canDelete) (if (pageletType != null) "Remove the ${pageletType.title} Pagelet and its page" else "Delete page ${page + 1}") else (deleteHint ?: "Page ${page + 1} can't be deleted"),
                Modifier.weight(1f), enabled = canDelete, tint = scheme.onSurfaceVariant, onClick = onDelete,
            )
            SmallAction(Icons.AutoMirrored.Filled.ArrowForward, "Move page ${page + 1} later", Modifier.weight(1f), enabled = canMoveLater, tint = scheme.onSurfaceVariant, onClick = onLater)
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    // Four equal slots across the card, so none is pushed off its edge on a narrow screen.
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.height(NivoTouch.min).semantics { contentDescription = description }) {
        Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else NivoAlpha.disabled), modifier = Modifier.size(20.dp))
    }
}

/** A small picture of one workspace page: its icons, widgets and folders, with the dock along the bottom. */
@Composable
private fun PagePreview(
    pageItems: List<HomeItem>,
    dock: List<HomeItem>,
    appsById: Map<String, AppInfo>,
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(NivoRadius.control),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .aspectRatio(9f / 16f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.surfaceContainerLow, scheme.tertiaryContainer.copy(alpha = 0.4f))), shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(NivoSpace.s),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cellW = maxWidth / columns
            val cellH = maxHeight / rows
            val density = LocalDensity.current
            pageItems.forEach { item ->
                val x = with(density) { (cellW * item.col).roundToPx() }
                val y = with(density) { (cellH * item.row).roundToPx() }
                Box(
                    Modifier.offset { IntOffset(x, y) }.size(cellW * item.spanX, cellH * item.spanY),
                    contentAlignment = Alignment.Center,
                ) {
                    val pageletType = com.snapkode.nivolauncher.pagelet.pageletTypeOf(item.id)
                    when {
                        pageletType != null -> PageletThumb(pageletType)
                        isAnyWidgetId(item.id) ->
                            Box(
                                Modifier.fillMaxSize().padding(2.dp)
                                    .background(scheme.primary.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                                    .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            )
                        isFolderId(item.id) ->
                            Box(Modifier.size(minOf(cellW, cellH) * 0.66f).background(scheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(minOf(cellW, cellH) * 0.2f)))
                        else -> appsById[item.id]?.let { Image(it.icon, null, Modifier.size(minOf(cellW, cellH) * 0.66f)) }
                    }
                }
            }
        }
        if (dock.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).background(scheme.onSurface.copy(alpha = 0.08f), CircleShape).padding(vertical = NivoSpace.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                dock.sortedBy { it.col }.take(5).forEach { item ->
                    appsById[item.id]?.let { Image(it.icon, null, Modifier.size(14.dp)) }
                        ?: Box(Modifier.size(14.dp).background(scheme.onSurface.copy(alpha = 0.2f), CircleShape))
                }
            }
        }
    }
}

/** A Pagelet page in the overview: its own look and name instead of an empty page. */
@Composable
private fun PageletThumb(type: com.snapkode.nivolauncher.pagelet.PageletType) {
    val scheme = MaterialTheme.colorScheme
    val sky = type == com.snapkode.nivolauncher.pagelet.PageletType.SKY
    val bg = if (sky) Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF4A5C8A), Color(0xFF0B0D12)))
    else Brush.verticalGradient(listOf(scheme.surface, scheme.surface))
    Box(Modifier.fillMaxSize().padding(NivoSpace.xxs).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(
            type.title, style = MaterialTheme.typography.labelLarge,
            color = if (sky) Color.White else scheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = NivoSpace.xs),
        )
    }
}
