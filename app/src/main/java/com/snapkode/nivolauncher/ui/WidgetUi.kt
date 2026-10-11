package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import com.snapkode.nivolauncher.data.isNivoWidgetId
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.appWidgetIdOf
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.ui.design.pressFeedback
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Hosts a widget view and turns a long press into a move gesture: once it fires, the rest of the touch
 * is reported as drag deltas instead of reaching the widget.
 */
private class GestureFrame(context: Context) : FrameLayout(context) {
    var onLongPress: () -> Unit = {}
    var onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> }
    var onDragEnd: (moved: Boolean) -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private var moved = false
    private val trigger = Runnable {
        fired = true
        moved = false
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.rawX; downY = ev.rawY
                handler.postDelayed(trigger, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (!fired && hypot(ev.rawX - downX, ev.rawY - downY) > slop) handler.removeCallbacks(trigger)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(trigger)
                // Swallow the release so the widget never sees it as a tap.
                val wasFired = fired
                if (wasFired) finishDrag()
                return wasFired
            }
        }
        return fired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!fired) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!moved && hypot(dx, dy) > slop) moved = true
                if (moved) onDrag(dx, dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishDrag()
        }
        return true
    }

    private fun finishDrag() {
        if (!fired) return
        fired = false
        parent?.requestDisallowInterceptTouchEvent(false)
        onDragEnd(moved)
    }
}

private enum class Edge { Left, Top, Right, Bottom }

@Composable
fun WidgetCell(
    item: HomeItem,
    viewModel: LauncherViewModel,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    cellWidthPx: Float,
    cellHeightPx: Float,
    rounded: Boolean,
    onLiftChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Launcher-drawn widgets (the Nivo clock) have no system widget behind them.
    val isNivo = isNivoWidgetId(item.id)
    val appWidgetId = if (isNivo) -1 else appWidgetIdOf(item.id)
    val info = if (isNivo) null else (remember(appWidgetId) { viewModel.widgetManager.getAppWidgetInfo(appWidgetId) } ?: return)
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings by viewModel.settings.collectAsState()
    val locked = LocalHomeLocked.current
    val density = LocalDensity.current
    val slop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop

    val resizingId by viewModel.resizingWidgetId.collectAsState()
    val resizing = resizingId == item.id
    var menuOpen by remember { mutableStateOf(false) }
    var widgetBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var lifted by remember { mutableStateOf(false) }
    var moveOffset by remember { mutableStateOf(Offset.Zero) }
    var movedFar by remember { mutableStateOf(false) }
    // Edge deltas in px while a resize handle is being dragged: left, top, right, bottom.
    var edges by remember { mutableStateOf(listOf(0f, 0f, 0f, 0f)) }
    var rootTop by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }

    val currentItem by rememberUpdatedState(item)
    val currentLocked by rememberUpdatedState(locked)

    fun commitMove() {
        val dx = (moveOffset.x / cellWidthPx).roundToInt()
        val dy = (moveOffset.y / cellHeightPx).roundToInt()
        val it = currentItem
        viewModel.placeWidgetAt(it.id, it.col + dx, it.row + dy, it.spanX, it.spanY)
    }

    fun commitResize() {
        val it = currentItem
        val l = (edges[0] / cellWidthPx).roundToInt()
        val t = (edges[1] / cellHeightPx).roundToInt()
        val r = (edges[2] / cellWidthPx).roundToInt()
        val b = (edges[3] / cellHeightPx).roundToInt()
        viewModel.placeWidgetAt(it.id, it.col + l, it.row + t, it.spanX - l + r, it.spanY - t + b)
        edges = listOf(0f, 0f, 0f, 0f)
    }

    // While a handle is being dragged the content follows the frame pixel by pixel.
    val liveW = with(density) { (width.toPx() + edges[2] - edges[0]).coerceAtLeast(cellWidthPx * 0.6f).toDp() }
    val liveH = with(density) { (height.toPx() + edges[3] - edges[1]).coerceAtLeast(cellHeightPx * 0.6f).toDp() }
    // Lifting eases in and settles back instead of snapping; the drag offset itself stays 1:1 with the finger.
    val motion = LocalMotion.current
    val lift by animateFloatAsState(if (lifted) 1f else 0f, motion.spring(weight = 1.4f), label = "widgetLift")
    Box(
        modifier
            .onGloballyPositioned {
                // Only sample the resting position; while lifted, the layer is translated.
                if (!lifted && moveOffset == Offset.Zero) rootTop = it.positionInRoot().y
                heightPx = it.size.height.toFloat()
                widgetBounds = it.boundsInRoot()
            }
            .zIndex(if (lifted || resizing) 2f else 0f)
            .graphicsLayer {
                translationX = moveOffset.x
                translationY = moveOffset.y
                val s = 1f + 0.04f * lift
                scaleX = s; scaleY = s
                alpha = 1f - 0.08f * lift
            }
            .size(width, height)
            .padding(4.dp)
    ) {
        if (isNivo) {
            fun finishLift() {
                if (!lifted) return
                lifted = false
                onLiftChange(false)
                if (movedFar) commitMove() else menuOpen = true
                moveOffset = Offset.Zero
                movedFar = false
            }
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset { IntOffset(edges[0].roundToInt(), edges[1].roundToInt()) }
                    .size(liveW - 8.dp, liveH - 8.dp)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { if (!currentLocked) { lifted = true; onLiftChange(true); menuOpen = true } },
                            onDrag = { change, drag ->
                                change.consume()
                                if (lifted) {
                                    moveOffset += drag
                                    if (moveOffset.getDistance() > viewConfiguration.touchSlop) { movedFar = true; menuOpen = false }
                                }
                            },
                            onDragEnd = { finishLift() },
                            onDragCancel = { finishLift() },
                        )
                    },
            ) {
                // The release of a long press must not count as a tap on the clock.
                NivoClockWidget(
                    weight = appSettings.nivoClockWeight,
                    onTap = { if (!lifted && !menuOpen && !resizing) openClockApp(context) },
                )
            }
        } else
            AndroidView(
                modifier = Modifier
                    // Anchored by its top-left corner (the frame's), so shrinking and growing both track the frame exactly.
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset { IntOffset(edges[0].roundToInt(), edges[1].roundToInt()) }
                    .size(liveW - 8.dp, liveH - 8.dp)
                    .then(if (rounded) Modifier.clip(RoundedCornerShape(NivoRadius.card)) else Modifier),
                factory = { context ->
                    GestureFrame(context).apply {
                        val host: AppWidgetHostView = viewModel.widgetHost.createView(context, appWidgetId, info)
                        host.setAppWidget(appWidgetId, info)
                        addView(host, FrameLayout.LayoutParams(-1, -1))
                    }
                },
                update = { frame ->
                    val host = frame.getChildAt(0) as? AppWidgetHostView
                    val w = (liveW.value - 8).toInt()
                    val h = (liveH.value - 8).toInt()
                    host?.updateAppWidgetSize(Bundle(), w, h, w, h)
                    frame.onLongPress = { if (!currentLocked) { lifted = true; onLiftChange(true); menuOpen = true } }
                    frame.onDrag = { dx, dy ->
                        if (lifted) {
                            moveOffset = Offset(dx, dy)
                            if (moveOffset.getDistance() > slop) menuOpen = false
                        }
                    }
                    frame.onDragEnd = { moved ->
                        if (lifted) {
                            lifted = false
                            onLiftChange(false)
                            if (moved) commitMove() else menuOpen = true
                            moveOffset = Offset.Zero
                        }
                    }
                },
            )

        androidx.compose.runtime.CompositionLocalProvider(LocalMenuAnchor provides widgetBounds.takeIf { menuOpen && it != androidx.compose.ui.geometry.Rect.Zero }) {
        NivoMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            NivoMenuItem("Resize", MenuIcons.Resize, { menuOpen = false; viewModel.resizingWidgetId.value = item.id })
            if (isNivo) {
                val bold = appSettings.nivoClockWeight == com.snapkode.nivolauncher.data.ClockWeight.Bold
                NivoMenuItem(
                    if (bold) "Light strokes" else "Bold strokes", MenuIcons.Weight,
                    {
                        menuOpen = false
                        viewModel.updateSettings {
                            it.copy(nivoClockWeight = if (bold) com.snapkode.nivolauncher.data.ClockWeight.Light else com.snapkode.nivolauncher.data.ClockWeight.Bold)
                        }
                    },
                )
            }
            NivoMenuItem("Remove", MenuIcons.Uninstall, { menuOpen = false; viewModel.removeFromHome(item.id) }, destructive = true)
        }
        }

        if (resizing) {
            ResizeFrame(
                edges = edges,
                onEdgeDrag = { edge, delta ->
                    // The frame can never leave the screen, and never shrinks below one cell.
                    val cfg = viewModel.settings.value
                    val it = currentItem
                    val i = edge.ordinal
                    val moved = (edges[i] + delta).let { v ->
                        when (edge) {
                            Edge.Left -> v.coerceIn(-it.col * cellWidthPx, (it.spanX - 1) * cellWidthPx + edges[2])
                            Edge.Right -> v.coerceIn(-(it.spanX - 1) * cellWidthPx + edges[0], (cfg.columns - it.col - it.spanX) * cellWidthPx)
                            Edge.Top -> v.coerceIn(-it.row * cellHeightPx, (it.spanY - 1) * cellHeightPx + edges[3])
                            Edge.Bottom -> v.coerceIn(-(it.spanY - 1) * cellHeightPx + edges[1], (cfg.rows - it.row - it.spanY) * cellHeightPx)
                        }
                    }
                    edges = edges.toMutableList().also { list -> list[i] = moved }
                    // Tell the layout which cells the frame now covers, so icons in the way shift aside right now.
                    val l = (edges[0] / cellWidthPx).roundToInt()
                    val t = (edges[1] / cellHeightPx).roundToInt()
                    val r = (edges[2] / cellWidthPx).roundToInt()
                    val b = (edges[3] / cellHeightPx).roundToInt()
                    viewModel.previewWidgetAt(it.id, it.col + l, it.row + t, it.spanX - l + r, it.spanY - t + b)
                },
                onEdgeEnd = { commitResize() },
                onDone = { viewModel.resizingWidgetId.value = null; viewModel.clearWidgetPreview() },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun ResizeFrame(
    edges: List<Float>,
    onEdgeDrag: (Edge, Float) -> Unit,
    onEdgeEnd: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val frameOffset = IntOffset(edges[0].roundToInt(), edges[1].roundToInt())
    val frameShape = RoundedCornerShape(NivoRadius.card)
    Box(modifier) {
        Box(
            Modifier
                .offset { frameOffset }
                .matchSizeWithEdges(edges)
                // A faint accent wash shows the area the widget will take; the crisp 2dp line marks its bounds.
                .background(accent.copy(alpha = 0.08f), frameShape)
                .border(2.dp, accent, frameShape)
        ) {
            Handle(Edge.Left, accent, Alignment.CenterStart, onEdgeDrag, onEdgeEnd)
            Handle(Edge.Right, accent, Alignment.CenterEnd, onEdgeDrag, onEdgeEnd)
            Handle(Edge.Top, accent, Alignment.TopCenter, onEdgeDrag, onEdgeEnd)
            Handle(Edge.Bottom, accent, Alignment.BottomCenter, onEdgeDrag, onEdgeEnd)
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(NivoSpace.s),
            ) {
                FrameButton(Icons.Default.Check, accent, "Done resizing", onDone)
            }
        }
    }
}

/** Grows/shrinks the frame by the live edge deltas (px). */
private fun Modifier.matchSizeWithEdges(edges: List<Float>): Modifier = layout { measurable, constraints ->
    val extraW = (edges[2] - edges[0]).roundToInt()
    val extraH = (edges[3] - edges[1]).roundToInt()
    val w = (constraints.maxWidth + extraW).coerceAtLeast(0)
    val h = (constraints.maxHeight + extraH).coerceAtLeast(0)
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
}

@Composable
private fun Handle(
    edge: Edge,
    color: androidx.compose.ui.graphics.Color,
    alignment: Alignment,
    onDrag: (Edge, Float) -> Unit,
    onEnd: () -> Unit,
) {
    val horizontal = edge == Edge.Left || edge == Edge.Right
    var active by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    // The grip grows a little while held, so the finger gets a clear "got it".
    val grip by animateDpAsState(if (active) 36.dp else 26.dp, motion.spring(weight = 1.4f), label = "handleGrip")
    val thick = 6.dp
    Box(
        Modifier
            .fillMaxSizeFor(edge)
            .wrapContentSizeAt(alignment)
            .size(NivoTouch.min)
            .semantics { contentDescription = "Resize ${edge.name.lowercase()} edge" }
            .pointerInput(edge) {
                // Typed up front so overload resolution picks the classic (Offset) / () callbacks.
                val start: (Offset) -> Unit = { active = true }
                val end: () -> Unit = { active = false; onEnd() }
                detectDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = end) { change, amount ->
                    change.consume()
                    onDrag(edge, if (horizontal) amount.x else amount.y)
                }
            },
        contentAlignment = alignment,
    ) {
        // A pill grip that straddles the frame line (half in, half out), parallel to the edge it moves.
        val nudgeX = when (edge) { Edge.Left -> -thick / 2; Edge.Right -> thick / 2; else -> 0.dp }
        val nudgeY = when (edge) { Edge.Top -> -thick / 2; Edge.Bottom -> thick / 2; else -> 0.dp }
        Box(
            Modifier
                .offset(x = nudgeX, y = nudgeY)
                .size(width = if (horizontal) thick else grip, height = if (horizontal) grip else thick)
                .shadow(2.dp, CircleShape)
                .background(color, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
        )
    }
}

private fun Modifier.fillMaxSizeFor(edge: Edge): Modifier = this.then(Modifier.fillMaxSize())
private fun Modifier.wrapContentSizeAt(alignment: Alignment): Modifier =
    this.then(Modifier.wrapContentSize(alignment))

@Composable
private fun FrameButton(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    // 48dp touch target around a 40dp visible disc.
    Box(
        Modifier
            .size(NivoTouch.min)
            .pressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).shadow(4.dp, CircleShape).background(color, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
fun WidgetPicker(viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val providers = remember {
        val pm = context.packageManager
        viewModel.allWidgetProviders()
            .map { Triple(it, it.loadLabel(pm), viewModel.isWorkWidget(it)) }
            .sortedWith(compareBy({ appLabel(context, it.first) }, { it.second }))
    }
    var query by remember { androidx.compose.runtime.mutableStateOf("") }
    var expanded by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme
    val groups = remember(providers, query) {
        providers
            .filter { query.isBlank() || it.second.contains(query, true) || appLabel(context, it.first).contains(query, true) }
            .groupBy { it.first.provider.packageName }
            .map { (pkg, list) -> Triple(pkg, appLabel(context, list.first().first), list) }
            .sortedBy { it.second.lowercase() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Surface(
                Modifier.fillMaxWidth().fillMaxHeight(0.92f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(topStart = NivoRadius.sheet, topEnd = NivoRadius.sheet),
                color = scheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = NivoSpace.l).navigationBarsPadding()) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = NivoSpace.m).size(width = 32.dp, height = 4.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
                    Text(
                        "Widgets", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = NivoSpace.l).semantics { heading() },
                    )
                    androidx.compose.material3.TextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        placeholder = { Text("Search widgets", maxLines = 1) },
                        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null, tint = scheme.onSurfaceVariant) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                androidx.compose.material3.IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = scheme.onSurfaceVariant)
                                }
                            }
                        } else null,
                        shape = CircleShape,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = scheme.surfaceContainerHigh, unfocusedContainerColor = scheme.surfaceContainerHigh,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                    LazyColumn(
                        Modifier.fillMaxSize().padding(top = NivoSpace.m),
                        verticalArrangement = Arrangement.spacedBy(NivoSpace.xs),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = NivoSpace.l),
                    ) {
                        if (query.isBlank() || "clock".contains(query, true) || "nivo".contains(query, true)) item(key = "nivo") {
                            NivoWidgetGroup(onAddClock = { onDismiss(); viewModel.addNivoClock() })
                        }
                        items(groups.size, key = { groups[it].first }) { i ->
                            val (pkg, name, list) = groups[i]
                            val open = expanded == pkg
                            val motion = LocalMotion.current
                            val corner by animateDpAsState(if (open) NivoRadius.hero else NivoRadius.control, motion.spring(), label = "groupCorner")
                            val chevron by animateFloatAsState(if (open) 180f else 0f, motion.spring(), label = "groupChevron")
                            val shape = RoundedCornerShape(corner)
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(if (open) scheme.surfaceContainerHigh else scheme.surfaceContainer, shape)
                                    .clip(shape)
                                    .animateContentSize(motion.spring()),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                        .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                                        .clickable(role = Role.Button, onClickLabel = if (open) "Hide widgets" else "Show widgets") { expanded = if (open) null else pkg }
                                        .padding(horizontal = NivoSpace.l, vertical = NivoSpace.m),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val icon = remember(pkg) { runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(120, 120).asImageBitmap() }.getOrNull() }
                                    if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(CircleShape)) else Box(Modifier.size(40.dp))
                                    Column(Modifier.weight(1f).padding(horizontal = NivoSpace.l)) {
                                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${list.size} ${if (list.size == 1) "widget" else "widgets"}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    Icon(
                                        androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                        null, tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.graphicsLayer { rotationZ = chevron },
                                    )
                                }
                                if (open) list.forEach { (info, label, work) ->
                                    ProviderRow(info, label, appLabel(context, info) + if (work) " · Work" else "") {
                                        onDismiss(); viewModel.requestWidget(info)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(info: AppWidgetProviderInfo, label: String, app: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val preview = remember(info) {
        runCatching { (info.loadPreviewImage(context, density) ?: info.loadIcon(context, density))?.toBitmap(240, 240)?.asImageBitmap() }.getOrNull()
    }
    val scheme = MaterialTheme.colorScheme
    val tile = RoundedCornerShape(NivoRadius.card)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Add widget", onClick = onClick).padding(horizontal = NivoSpace.l, vertical = NivoSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NivoSpace.l),
    ) {
        Box(
            Modifier.size(88.dp).background(scheme.surfaceContainerHighest, tile).border(1.dp, scheme.outlineVariant, tile),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) Image(preview, null, Modifier.size(72.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(app, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun appLabel(context: Context, info: AppWidgetProviderInfo): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

/** The picker's own group: Nivo's launcher-drawn widgets, always expanded since there are only a few. */
@Composable
private fun NivoWidgetGroup(onAddClock: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val shape = RoundedCornerShape(NivoRadius.hero)
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainer, shape).clip(shape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = NivoSpace.l, vertical = NivoSpace.m), verticalAlignment = Alignment.CenterVertically) {
            val icon = remember { runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(120, 120).asImageBitmap() }.getOrNull() }
            if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(CircleShape)) else Box(Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = NivoSpace.l)) {
                Text("Nivo Launcher", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("1 widget", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Add widget", onClick = onAddClock).padding(horizontal = NivoSpace.l, vertical = NivoSpace.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NivoSpace.l),
        ) {
            // A still of the widget, so the picker shows what you are getting.
            Column(
                Modifier.size(width = 120.dp, height = 88.dp).clip(RoundedCornerShape(NivoRadius.card))
                    .background(Brush.verticalGradient(listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerHigh)), RoundedCornerShape(NivoRadius.card))
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(NivoRadius.card))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("SUNDAY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("10", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = scheme.onSurface)
                    Text(":", style = MaterialTheme.typography.headlineMedium, color = scheme.onSurfaceVariant)
                    Text("09", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = scheme.primary)
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(scheme.onSurface.copy(alpha = 0.10f), CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text("Clock", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Animated time and date", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(NivoSpace.s))
    }
}
