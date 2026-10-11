package com.snapkode.nivolauncher.ui.lasso

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.LauncherSettings
import com.snapkode.nivolauncher.lasso.Box as LBox
import com.snapkode.nivolauncher.lasso.Candidate
import com.snapkode.nivolauncher.lasso.Grid
import com.snapkode.nivolauncher.lasso.GridMetrics
import com.snapkode.nivolauncher.lasso.GroupDropPlanner
import com.snapkode.nivolauncher.lasso.LassoCandidates
import com.snapkode.nivolauncher.lasso.LassoHitTester
import com.snapkode.nivolauncher.lasso.LassoMotion
import com.snapkode.nivolauncher.lasso.LassoState
import com.snapkode.nivolauncher.lasso.PageTurn
import com.snapkode.nivolauncher.lasso.LayoutOps
import com.snapkode.nivolauncher.lasso.OpResult
import com.snapkode.nivolauncher.lasso.RadialLayout
import com.snapkode.nivolauncher.lasso.SelectionMode
import com.snapkode.nivolauncher.lasso.SelectionPolicy
import com.snapkode.nivolauncher.ui.design.LocalHapticsEnabled
import com.snapkode.nivolauncher.ui.design.LocalMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class Phase { Undecided, Draw, GroupDrag, PageSwipe }

/**
 * Lasso mode's whole interactive surface. It sits over the workspace while the mode is on and owns every touch:
 * drawing the loop, tapping icons, dragging the selection, and the toolbar. Normal launching and paging are
 * suspended underneath it.
 *
 * Nothing here writes the layout while a gesture is in flight. Strokes only change the selection; a drag draws
 * ghosts and a preview and commits once, atomically, on a valid drop.
 */
@Composable
fun LassoLayer(
    viewModel: LauncherViewModel,
    session: LassoSession,
    pagerRect: Rect,
    workspaceItems: List<HomeItem>,
    appsById: Map<String, AppInfo>,
    folders: Map<String, HomeFolder>,
    settings: LauncherSettings,
    /** The workspace page showing right now, or null on News/Library or while the pager is between pages. */
    currentPage: () -> Int?,
    /** The page the pager is on or heading to; the drop is planned for it even while the pages slide. */
    targetPage: () -> Int? = currentPage,
    /** How many workspace pages can be navigated to (includes the spare page offered while a group is held). */
    workspacePageCount: Int,
    openPage: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val motion = LocalMotion.current
    val reduced = motion.reduced
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val scope = rememberCoroutineScope()
    val touchExploration by rememberTouchExploration()
    val undoLabel by viewModel.lassoUndo.collectAsState()

    val items by rememberUpdatedState(workspaceItems)
    val rect by rememberUpdatedState(pagerRect)
    val pageNow by rememberUpdatedState(currentPage)
    val pageAhead by rememberUpdatedState(targetPage)
    val settingsNow by rememberUpdatedState(settings)
    val pageCountNow by rememberUpdatedState(workspacePageCount)

    val widthPx = pagerRect.width.roundToInt()
    val heightPx = pagerRect.height.roundToInt()
    val gridMetrics = remember(widthPx, heightPx, settings.columns, settings.rows) {
        GridMetrics(widthPx, heightPx, settings.columns, settings.rows)
    }
    val gridModel = remember(settings.columns, settings.rows) { Grid(settings.columns, settings.rows) }
    val policy = remember(density) {
        with(density) { SelectionPolicy(touchRadius = 12.dp.toPx(), minLength = 28.dp.toPx()) }
    }
    val slopBase = with(density) { 5.dp.toPx() }
    androidx.compose.runtime.SideEffect { session.configurePath(slopBase) }
    val iconPx = with(density) { (56 * settings.iconScale).dp.toPx() }
    val edgePx = with(density) { 28.dp.toPx() }
    val pageSwipePx = with(density) { 56.dp.toPx() }

    // ---- haptics --------------------------------------------------------------------------------------------
    val lastTick = remember { longArrayOf(0L) }
    fun tick() {
        if (!hapticsOn) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTick[0] < 55) return
        lastTick[0] = now
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    fun lift() { if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    fun confirm() { if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    fun reject() { if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.Reject) }

    // ---- in-flight geometry changes cancel gestures cleanly -------------------------------------------------
    // Only a real change of shape (rotation, split screen) abandons a gesture in flight. The workspace also drifts by a
    // few pixels when the spare page appears or the page strip animates; that must not drop a group the user is holding.
    val lastSize = remember { intArrayOf(0, 0) }
    LaunchedEffect(widthPx, heightPx) {
        val w = lastSize[0]
        val h = lastSize[1]
        lastSize[0] = widthPx; lastSize[1] = heightPx
        if (w != 0 && h != 0 && (abs(widthPx - w) > w * 0.15f || abs(heightPx - h) > h * 0.25f)) session.geometryChanged()
    }

    // ---- group drag: settle animation state -----------------------------------------------------------------
    val settle = remember { Animatable(0f) }
    val ghostFade = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    var releaseDelta by remember { mutableStateOf(Offset.Zero) }
    var settleTargets by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    var pendingOpen by remember { mutableStateOf<Int?>(null) }
    var lastPlanKey by remember { mutableStateOf(Long.MIN_VALUE) }
    val pointerLocal = remember { floatArrayOf(0f, 0f) }
    /** True while the first finger is held on a selected icon: a second finger anywhere then turns pages. */
    var holding by remember { mutableStateOf(false) }

    LaunchedEffect(pendingOpen, workspacePageCount) {
        val p = pendingOpen
        if (p != null && p < workspacePageCount) { openPage(p); pendingOpen = null }
    }
    LaunchedEffect(session.message) {
        if (session.message != null) { delay(2600); session.message = null }
    }

    // ---- helpers over the layout ----------------------------------------------------------------------------
    /** Footprints (icon + label) of the current page's icons, workspace-local. Used for taps and for ghosts. */
    fun footprints(page: Int): Map<String, LBox> {
        val r = rect
        val all = session.tilesLocal(r.left, r.top)
        val out = HashMap<String, LBox>()
        for (c in LassoCandidates.forPage(items, page, all)) out[c.id] = c.tile
        return out
    }

    fun tileCandidates(feet: Map<String, LBox>): List<Candidate> =
        feet.map { (id, foot) -> Candidate(id, iconTile(foot, iconPx)) }

    fun tapCandidates(feet: Map<String, LBox>): List<Candidate> =
        feet.map { (id, foot) -> Candidate(id, foot.inflate(with(density) { 6.dp.toPx() })) }

    fun updatePlan() {
        val anchor = session.dragAnchor ?: return
        val foot = session.dragStartTiles[anchor] ?: return
        val d = session.dragDelta
        val cx = foot.centerX + d.x
        val cy = foot.centerY + d.y
        val page = pageNow() ?: pageAhead()
        val inside = cx >= 0f && cy >= 0f && cx < widthPx && cy < heightPx
        val col = gridMetrics.colAt(cx)
        val row = gridMetrics.rowAt(cy)
        val key = ((page ?: -2) + 5) * 100_000_000L + (if (inside) 1L else 0L) * 10_000_000L + (col + 1000) * 10_000L + (row + 1000)
        if (key == lastPlanKey && session.dropPlan != null) return
        lastPlanKey = key
        val target = if (page != null && inside) page else -1
        val plan = GroupDropPlanner.plan(items, session.dragIds, gridModel, target, anchor, col, row)
        val previous = session.dropPlan
        session.dropPlan = plan
        if (plan.valid && (previous == null || previous.deltaCol != plan.deltaCol || previous.deltaRow != plan.deltaRow || previous.page != plan.page)) tick()
    }

    fun startGroupDrag(hit: String?, start: Offset): Boolean {
        val page = pageNow() ?: return false
        val feet = footprints(page)
        val ids = session.selected.filter { it in feet }
        if (ids.isEmpty()) return false
        val chosen = LinkedHashMap<String, LBox>()
        for (id in ids) chosen[id] = feet.getValue(id)
        val anchor = hit?.takeIf { it in chosen }
            ?: ids.minBy { hypot(chosen.getValue(it).centerX - start.x, chosen.getValue(it).centerY - start.y) }
        if (!session.beginDrag(anchor, ids, chosen)) return false
        settling = false
        lastPlanKey = Long.MIN_VALUE
        lift()
        updatePlan()
        return true
    }

    /** Returns the ghosts to where they started (or home, after a refused drop), then ends the drag. */
    fun settleBack() {
        val ids = session.dragIds
        settleTargets = ids.associateWith { Offset.Zero }
        releaseDelta = session.dragDelta
        settling = true
        // The ghosts return to the source page's cells, so show that page again instead of gliding over a different one.
        val shown = pageNow() ?: pageAhead()
        if (session.page >= 0 && shown != null && shown != session.page) openPage(session.page)
        scope.launch {
            settle.snapTo(0f)
            settle.animateTo(1f, SettleSpring)
            session.finishDrag()
            settling = false
        }
    }

    fun releaseDrag() {
        if (session.state != LassoState.DRAGGING || settling) return
        val plan = session.dropPlan
        val ids = session.dragIds
        val sourcePage = session.page
        val ok = plan != null && plan.valid && !plan.isNoop(sourcePage)
        if (!ok) {
            if (plan != null && !plan.valid && session.dragDelta.getDistance() > slopBase * 3) {
                session.message = "No room to drop there"
                reject()
            }
            settleBack()
            return
        }
        val byId = plan!!.placements.associateBy { it.id }
        val targets = HashMap<String, Offset>()
        for (id in ids) {
            val item = items.firstOrNull { it.id == id } ?: continue
            val p = byId[id] ?: continue
            targets[id] = Offset((p.col - item.col) * gridMetrics.cellW.toFloat(), (p.row - item.row) * gridMetrics.cellH.toFloat())
        }
        settleTargets = targets
        releaseDelta = session.dragDelta
        settling = true
        // Commit straight away: the real icons stay hidden under the ghosts until those land, and the new layout has
        // reached the screen by then, so nothing flashes at the old cells.
        val committed = viewModel.lassoCommitMove(plan)
        if (committed) {
            // The ghosts end exactly where the real icons now are, so the real ones appear without animating.
            session.snapIds = ids.toSet()
            confirm()
        }
        scope.launch {
            settle.snapTo(0f)
            settle.animateTo(1f, SettleSpring)
            if (committed) session.followToPage(plan.page)
            session.finishDrag()
            settling = false
            delay(700)
            session.snapIds = emptySet()
        }
    }

    fun cancelDrag() {
        if (session.state == LassoState.DRAGGING && !settling) settleBack()
    }

    // ---- taps and strokes -----------------------------------------------------------------------------------
    fun onTap(hit: String?) {
        val page = pageNow() ?: return
        if (hit == null) return
        session.tap(hit, page)
        tick()
    }

    fun afterOp(result: OpResult, unchangedMessage: String): Boolean {
        return when (result) {
            is OpResult.Fail -> { session.message = result.reason.message; reject(); false }
            is OpResult.Ok -> {
                if (result.changed == 0) session.message = unchangedMessage else confirm()
                result.targetPage?.let { session.followToPage(it); pendingOpen = it }
                true
            }
        }
    }

    // ---- actions --------------------------------------------------------------------------------------------
    val actions = remember(session, viewModel) {
        LassoActions(
            arrange = { kind ->
                val sel = session.selected
                val page = session.page
                val result = viewModel.lassoRun(kind.label) { its, _, g ->
                    when (kind) {
                        ArrangeKind.AlignHorizontally -> LayoutOps.alignHorizontally(its, sel, g, page)
                        ArrangeKind.AlignVertically -> LayoutOps.alignVertically(its, sel, g, page)
                        ArrangeKind.Distribute -> LayoutOps.distribute(its, sel, g, page)
                        ArrangeKind.Compact -> LayoutOps.compact(its, sel, g, page)
                    }
                }
                afterOp(result, "Already arranged")
            },
            createFolder = {
                val sel = session.selected
                val page = session.page
                val newId = viewModel.newFolderId()
                val before = viewModel.items.value
                val probe = LayoutOps.createFolder(before, viewModel.folders.value, sel, page, newId)
                if (probe is OpResult.Fail) {
                    session.message = probe.reason.message; reject()
                } else if (probe is OpResult.Ok && session.state == LassoState.SELECTED && !settling) {
                    val merged = before.filter { b -> probe.items.none { it.id == b.id } }.map { it.id }
                    val anchorCell = probe.items.firstOrNull { it.id == newId }
                    val anchorId = before.firstOrNull { it.page == page && anchorCell != null && it.col == anchorCell.col && it.row == anchorCell.row && it.id in merged }?.id
                        ?: merged.firstOrNull()
                    val feet = footprints(page)
                    val ids = merged.filter { it in feet }
                    if (anchorId != null && anchorId in feet && ids.isNotEmpty() && session.beginDrag(anchorId, ids, ids.associateWith { feet.getValue(it) })) {
                        val target = feet.getValue(anchorId)
                        settleTargets = ids.associateWith { id ->
                            val f = feet.getValue(id)
                            Offset(target.centerX - f.centerX, target.centerY - f.centerY)
                        }
                        releaseDelta = Offset.Zero
                        settling = true
                        lift()
                        scope.launch {
                            settle.snapTo(0f); ghostFade.snapTo(0f)
                            val fade = launch { ghostFade.animateTo(1f, androidx.compose.animation.core.tween(LassoMotion.duration(LassoMotion.FOLDER_CREATE_MS, reduced))) }
                            settle.animateTo(1f, motion.spring(1f))
                            fade.join()
                            val result = viewModel.lassoRun("Create folder") { its, fs, _ ->
                                LayoutOps.createFolder(its, fs, sel, page, newId)
                            }
                            session.finishDrag()
                            settling = false
                            ghostFade.snapTo(0f)
                            if (afterOp(result, "Nothing to group")) viewModel.lassoDone()
                        }
                    } else {
                        val result = viewModel.lassoRun("Create folder") { its, fs, _ -> LayoutOps.createFolder(its, fs, sel, page, newId) }
                        if (afterOp(result, "Nothing to group")) viewModel.lassoDone()
                    }
                }
            },
            gather = {
                val sel = session.selected
                val target = pageNow() ?: session.page
                val result = viewModel.lassoRun("Gather") { its, _, g -> LayoutOps.gather(its, sel, g, target) }
                if (result is OpResult.Ok && result.changed > 0) session.followToPage(target)
                afterOp(result, "Already together")
            },
            remove = {
                val sel = session.selected
                val result = viewModel.lassoRun("Remove") { its, _, _ -> LayoutOps.remove(its, sel) }
                if (afterOp(result, "Nothing to remove")) viewModel.lassoDone()
            },
            moveToPage = { page ->
                val sel = session.selected
                val result = viewModel.lassoRun("Move to page") { its, _, g -> LayoutOps.moveToPage(its, sel, g, page) }
                afterOp(result, "Already on that page")
            },
            selectAll = {
                val page = pageNow() ?: session.page.takeIf { it >= 0 } ?: 0
                session.setSelection(LayoutOps.selectableOn(items, page), page)
                tick()
            },
            clear = { session.clearSelection() },
            done = { viewModel.lassoDone() },
            cancel = { viewModel.lassoCancel() },
            undo = {
                if (viewModel.lassoUndoLast()) { session.message = "Undone"; confirm() }
            },
            toggleMove = { session.moveAnywhere = !session.moveAnywhere; tick() },
            previousPage = { val p = pageNow() ?: session.page.coerceAtLeast(0); openPage((p - 1).coerceAtLeast(0)) },
            nextPage = { val p = pageNow() ?: session.page.coerceAtLeast(0); openPage((p + 1).coerceAtMost((pageCountNow - 1).coerceAtLeast(0))) },
        )
    }

    // Holding a dragged group at an edge flips the page, as holding a single icon does.
    LaunchedEffect(session.state == LassoState.DRAGGING) {
        if (session.state != LassoState.DRAGGING) return@LaunchedEffect
        var side = 0
        var dwell = 0
        while (true) {
            delay(50)
            if (settling) { dwell = 0; continue }
            val x = pointerLocal[0]
            val s = when {
                x < edgePx -> -1
                x > widthPx - edgePx -> 1
                else -> 0
            }
            if (s != 0 && s == side) {
                dwell += 50
                if (dwell >= LassoMotion.EDGE_DWELL_MS) {
                    dwell = 0
                    val now = pageNow()
                    if (now != null) {
                        val target = now + s
                        if (target in 0 until pageCountNow) { lastPlanKey = Long.MIN_VALUE; openPage(target) }
                    }
                }
            } else { side = s; dwell = 0 }
        }
    }

    // After a page change the plan must be recomputed for the page now showing, the moment it changes.
    LaunchedEffect(session.state == LassoState.DRAGGING) {
        if (session.state != LassoState.DRAGGING) return@LaunchedEffect
        snapshotFlow { pageNow() }.distinctUntilChanged().collect { lastPlanKey = Long.MIN_VALUE; updatePlan() }
    }

    BackHandler(enabled = session.active) {
        when (session.state) {
            LassoState.DRAGGING -> cancelDrag()
            LassoState.DRAWING -> session.cancelStroke()
            else -> viewModel.lassoDone()
        }
    }

    val origin = { Offset(rect.left, rect.top) }
    val pageForUi by remember { derivedStateOf { pageNow() } }
    val screenInsetsTop = WindowInsets.statusBars.getTop(density).toFloat()
    val navInset = WindowInsets.navigationBars.getBottom(density).toFloat()

    Box(
        Modifier.fillMaxSize().then(
            if (!session.active) Modifier else Modifier.pointerInput(session) {
                // Above the toolbar and the surface: with a selected icon held, a second finger anywhere on screen
                // (the thumb usually lands on the toolbar) swipes between pages and carries the selection along.
                val pageTurn = PageTurn(pageSwipePx)
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val other = event.changes.firstOrNull { it.pressed && it.id != first.id }
                        if (holding && other != null && event.changes.any { it.id == first.id && it.pressed }) {
                            val turn = pageTurn.move(other.id.value, other.position.x, other.position.y) { dir ->
                                pageNow()?.let { it + dir in 0 until pageCountNow } == true
                            }
                            if (turn != 0) {
                                val now = pageNow()
                                if (now != null && now + turn in 0 until pageCountNow) {
                                    lastPlanKey = Long.MIN_VALUE
                                    openPage(now + turn)
                                    tick()
                                }
                            }
                            other.consume()
                        } else pageTurn.release()
                    } while (event.changes.any { it.pressed })
                }
            },
        ),
    ) {
        if (session.active) {
            // ---- the pointer surface, exactly over the workspace ----------------------------------------------
            Box(
                Modifier
                    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .semantics {
                        contentDescription = "Lasso selection. Draw a loop around icons to select them, or use the toolbar."
                        customActions = listOf(
                            CustomAccessibilityAction("Select all icons on this page") { actions.selectAll(); true },
                            CustomAccessibilityAction("Clear selection") { actions.clear(); true },
                            CustomAccessibilityAction("Done") { actions.done(); true },
                        )
                    }
                    .pointerInput(session) {
                        val slop = viewConfiguration.touchSlop
                        val scratch = HashSet<String>()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            // While a group is settling or being carried, a new touch is ignored until it lifts.
                            if (session.state == LassoState.DRAGGING || session.state == LassoState.IDLE) {
                                do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                                return@awaitEachGesture
                            }
                            val start = down.position
                            val page = pageNow()
                            val feet = if (page != null) footprints(page) else emptyMap()
                            val tapHit = LassoHitTester.hitAt(tapCandidates(feet), start.x, start.y)
                            val fromEdge = start.x < edgePx || start.x > size.width - edgePx
                            holding = false
                            var phase = Phase.Undecided
                            var dragOrigin = start
                            var candidates: List<Candidate> = emptyList()
                            var previewCount = 0
                            var finished = false
                            pointerLocal[0] = start.x; pointerLocal[1] = start.y
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size > 1) {
                                        // A second finger while the first one holds a selection (on an icon, after drawing a
                                        // loop it hasn't lifted from, or on empty space) lifts the selection under the first
                                        // finger; the second finger then swipes between pages (see the handler above this
                                        // surface) and the group goes with it. For anything else it abandons the gesture.
                                        val primary = event.changes.firstOrNull { it.id == down.id }
                                        var carrying = phase == Phase.GroupDrag
                                        if (!carrying && primary != null && primary.pressed) {
                                            val hasSelection = session.selected.isNotEmpty() || (phase == Phase.Draw && scratch.isNotEmpty())
                                            if (hasSelection && (phase == Phase.Undecided || phase == Phase.Draw)) {
                                                val onIcon = phase == Phase.Undecided && tapHit != null && tapHit in session.selected
                                                if (phase == Phase.Draw) {
                                                    // The loop is done as far as the user is concerned: apply it.
                                                    session.endStroke(HashSet(scratch), page ?: session.page.coerceAtLeast(0))
                                                }
                                                val here = primary.position
                                                session.dragDelta = Offset.Zero
                                                if (session.state == LassoState.SELECTED && startGroupDrag(if (onIcon) tapHit else null, here)) {
                                                    phase = Phase.GroupDrag
                                                    dragOrigin = here
                                                    carrying = true
                                                } else if (phase == Phase.Draw) {
                                                    phase = Phase.Undecided
                                                }
                                            }
                                        }
                                        holding = carrying
                                        if (carrying && primary != null && primary.pressed) {
                                            event.changes.forEach { it.consume() }
                                            // Keep following the first finger.
                                            pointerLocal[0] = primary.position.x; pointerLocal[1] = primary.position.y
                                            session.dragDelta = Offset(primary.position.x - dragOrigin.x, primary.position.y - dragOrigin.y)
                                            updatePlan()
                                            continue
                                        }
                                        when (phase) {
                                            Phase.Draw -> session.cancelStroke()
                                            Phase.GroupDrag -> cancelDrag()
                                            else -> Unit
                                        }
                                        finished = true
                                        event.changes.forEach { it.consume() }
                                        do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                                        break
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    val pos = change.position
                                    pointerLocal[0] = pos.x; pointerLocal[1] = pos.y

                                    if (!change.pressed) {
                                        when (phase) {
                                            Phase.Undecided -> onTap(tapHit)
                                            Phase.Draw -> {
                                                val hits = HashSet(scratch)
                                                session.endStroke(hits, page ?: session.page.coerceAtLeast(0))
                                            }
                                            Phase.GroupDrag -> releaseDrag()
                                            Phase.PageSwipe -> {
                                                val dx = pos.x - start.x
                                                val now = pageNow() ?: session.page.coerceAtLeast(0)
                                                if (abs(dx) > pageSwipePx) openPage((now + if (dx < 0) 1 else -1).coerceIn(0, (pageCountNow - 1).coerceAtLeast(0)))
                                            }
                                        }
                                        finished = true
                                        // A second finger may still be down (the page-turning one): let it finish unseen.
                                        var rest = event.changes.any { it.pressed }
                                        while (rest) { val e = awaitPointerEvent(); e.changes.forEach { it.consume() }; rest = e.changes.any { it.pressed } }
                                        break
                                    }

                                    when (phase) {
                                        Phase.Undecided -> if (hypot(pos.x - start.x, pos.y - start.y) > slop) {
                                            val dx = pos.x - start.x
                                            val dy = pos.y - start.y
                                            val grabbed = session.state == LassoState.SELECTED &&
                                                ((tapHit != null && tapHit in session.selected) || (session.moveAnywhere && session.selected.isNotEmpty()))
                                            phase = when {
                                                fromEdge && abs(dx) > abs(dy) -> Phase.PageSwipe
                                                grabbed && startGroupDrag(tapHit, start) -> Phase.GroupDrag
                                                page != null && session.beginStroke(start.x, start.y) -> {
                                                    candidates = tileCandidates(feet)
                                                    Phase.Draw
                                                }
                                                else -> Phase.PageSwipe
                                            }
                                            if (phase == Phase.Draw && session.addStrokePoint(pos.x, pos.y)) {
                                                LassoHitTester.hits(session.path, candidates, policy, scratch)
                                                session.updateStrokeHits(scratch)
                                            }
                                        }
                                        Phase.Draw -> if (session.addStrokePoint(pos.x, pos.y)) {
                                            LassoHitTester.hits(session.path, candidates, policy, scratch)
                                            session.updateStrokeHits(scratch)
                                            if (scratch.size > previewCount) tick()
                                            previewCount = scratch.size
                                        }
                                        Phase.GroupDrag -> {
                                            session.dragDelta = Offset(pos.x - dragOrigin.x, pos.y - dragOrigin.y)
                                            updatePlan()
                                        }
                                        Phase.PageSwipe -> Unit
                                    }
                                }
                            } finally {
                                holding = false
                                // The pointer coroutine can be cancelled mid-gesture (the layer left composition, the
                                // key changed): never leave a half-drawn stroke or a held group behind.
                                if (!finished) {
                                    when (phase) {
                                        Phase.Draw -> session.cancelStroke()
                                        Phase.GroupDrag -> cancelDrag()
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    },
            )

            // ---- what screen readers see: one checkbox per icon -----------------------------------------------
            val shownPage = pageForUi
            if (shownPage != null) {
                for (item in items) {
                    if (item.page != shownPage) continue
                    val label = folders[item.id]?.name ?: appsById[item.id]?.label ?: continue
                    key(item.id) { IconAccessibilityNode(session, item.id, label, shownPage) }
                }
            }

            // ---- the visuals (all redraw without recomposing) -------------------------------------------------
            LassoPathCanvas(session, origin)
            // Carried icons get no ring at their old (empty) cells; the ring returns with them when they land.
            for (id in session.selected) if (id !in session.dragIds) key("sel-$id") { SelectionRing(session, id, removing = false, preview = false) }
            if (session.state == LassoState.DRAWING) {
                for (id in session.strokeHits) {
                    if (session.mode == SelectionMode.Add && id !in session.selected) key("add-$id") { SelectionRing(session, id, removing = false, preview = true) }
                    if (session.mode == SelectionMode.Subtract && id in session.selected) key("sub-$id") { SelectionRing(session, id, removing = true, preview = false) }
                }
            }
            DropPreview(session, gridMetrics.cellW, gridMetrics.cellH, origin)
            GroupGhosts(
                session = session, settle = settle, fade = ghostFade, settling = { settling },
                releaseDelta = { releaseDelta }, targets = { settleTargets }, origin = origin,
                appsById = appsById, folders = folders, settings = settings,
            )
        }

        // ---- radial menu or toolbar ---------------------------------------------------------------------------
        val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
        val screenW = window.width.toFloat()
        val screenH = window.height.toFloat()
        val selectionBox = remember(session.selected, if (pageForUi == null) -1 else session.layoutTick) {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            var any = false
            for (id in session.selected) {
                val bx = session.tileRoot(id) ?: continue
                any = true
                if (bx.left < l) l = bx.left; if (bx.top < t) t = bx.top
                if (bx.right > r) r = bx.right; if (bx.bottom > b) b = bx.bottom
            }
            if (any) LBox(l, t, r, b) else null
        }
        val radialPlacement = if (
            settings.lassoRadialMenu && !touchExploration && session.state == LassoState.SELECTED &&
            !settling && selectionBox != null && pageForUi == session.page
        ) {
            val radius = with(density) { RadialMetrics.radius.toPx() }
            val button = with(density) { RadialMetrics.button.toPx() }
            val label = with(density) { RadialMetrics.labelHeight.toPx() }
            val usable = RadialLayout.spacing(radius, RadialMetrics.buttonCount) >= button + with(density) { 8.dp.toPx() }
            if (!usable) null else RadialLayout.place(
                selection = selectionBox,
                screenWidth = screenW, screenHeight = screenH,
                topInset = screenInsetsTop, bottomInset = navInset + with(density) { 150.dp.toPx() },
                extent = radius + button / 2f + label,
                halfWidth = radius * 0.966f + button / 2f,
                gap = with(density) { 20.dp.toPx() }, margin = with(density) { 8.dp.toPx() },
            )
        } else null

        if (session.active && radialPlacement != null) {
            LassoRadial(session, radialPlacement, session.selectionCount, workspacePageCount.coerceAtLeast(1), pageForUi?.plus(1), actions)
        }

        LassoToolbar(
            session = session,
            undoLabel = undoLabel.takeIf { session.active },
            showActions = radialPlacement == null,
            currentPageNumber = (pageForUi ?: session.page.takeIf { it >= 0 })?.plus(1),
            pageCount = workspacePageCount,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Landing of a dropped group: quick and critically damped, so it never overshoots past its cell. */
private val SettleSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 1400f)
