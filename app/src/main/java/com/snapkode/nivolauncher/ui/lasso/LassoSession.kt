package com.snapkode.nivolauncher.ui.lasso

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.snapkode.nivolauncher.lasso.Box
import com.snapkode.nivolauncher.lasso.DropPlan
import com.snapkode.nivolauncher.lasso.LassoHitTester
import com.snapkode.nivolauncher.lasso.LassoMachine
import com.snapkode.nivolauncher.lasso.LassoPath
import com.snapkode.nivolauncher.lasso.LassoState
import com.snapkode.nivolauncher.lasso.LassoTransaction
import com.snapkode.nivolauncher.lasso.LayoutSnapshot
import com.snapkode.nivolauncher.lasso.SelectionMode

/** The active lasso session, or null in previews. Provided once at the root. */
val LocalLasso = compositionLocalOf<LassoSession?> { null }

/**
 * Everything the UI needs to know about lasso mode, as Compose state: where the state machine is, what is selected,
 * and the live geometry (the stroke being drawn, the group being dragged).
 *
 * It lives in the view model, so a recreated activity keeps the selection. It holds no views and no layout model:
 * layout changes go through the view model, and a drag never touches the model until it is dropped.
 *
 * Selection is **page-scoped**: it belongs to [page], and acting on a different page starts a fresh selection there.
 */
@Stable
class LassoSession {
    private val machine = LassoMachine()

    var state by mutableStateOf(LassoState.IDLE)
        private set
    val active: Boolean get() = state != LassoState.IDLE

    /** The workspace page the selection belongs to, or -1 when nothing is selected. */
    var page by mutableIntStateOf(-1)
        private set

    /** The selected icons. Replaced, never mutated, so reading it is a plain state read. */
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Whether strokes and taps add to the selection or remove from it. */
    var mode by mutableStateOf(SelectionMode.Add)

    /** When on, dragging anywhere (not only on a selected icon) moves the whole selection. */
    var moveAnywhere by mutableStateOf(false)

    /** A short line shown to the user, such as why an action could not be done. Cleared by the UI after a moment. */
    var message by mutableStateOf<String?>(null)

    // ---- the stroke -----------------------------------------------------------------------------------------

    /** The stroke being drawn (workspace-local pixels). Reused between strokes; read it inside draw blocks only. */
    var path: LassoPath = LassoPath(minStep = DEFAULT_MIN_STEP)
        private set

    /** Bumped whenever the stroke grows, so a draw block that reads it redraws without recomposing anything. */
    var pathTick by mutableIntStateOf(0)
        private set

    /** Bumped when a stroke ends, to start the outline's fade-out. */
    var strokeSerial by mutableIntStateOf(0)
        private set

    /** What the stroke would select right now. Shown as a preview ring; only applied when the stroke ends. */
    var strokeHits by mutableStateOf<Set<String>>(emptySet())
        private set

    // ---- icon geometry --------------------------------------------------------------------------------------

    private val rootTiles = HashMap<String, Box>()

    /** Bumped when an icon's position changes while lasso mode is on, so rings follow icons as they move. */
    var layoutTick by mutableIntStateOf(0)
        private set

    // ---- group drag -----------------------------------------------------------------------------------------

    /** The icons being carried. Their real views are hidden while this is non-empty; ghosts are drawn instead. */
    var dragIds by mutableStateOf<List<String>>(emptyList())
        private set
    var dragAnchor: String? = null
        private set

    /** Where each carried icon started, in workspace-local pixels, frozen when the drag began. */
    var dragStartTiles: Map<String, Box> = emptyMap()
        private set

    /** How far the finger has moved since the drag began, in pixels. */
    var dragDelta by mutableStateOf(Offset.Zero)

    /** Where the group would land if released now, or null before the first move. */
    var dropPlan by mutableStateOf<DropPlan?>(null)

    /** Icons whose next move should appear instantly (they were already animated into place by the drop). */
    var snapIds by mutableStateOf<Set<String>>(emptySet())

    /** The rollback point taken when the mode was entered. */
    var transaction: LassoTransaction? = null
        private set

    // ---- lifecycle ------------------------------------------------------------------------------------------

    /** Enter the mode with nothing selected. False if it is already on. */
    fun arm(baseline: LayoutSnapshot): Boolean {
        if (!machine.arm(withSelection = false)) return false
        resetTransient()
        selected = emptySet()
        page = -1
        mode = SelectionMode.Add
        transaction = LassoTransaction(baseline)
        state = machine.state
        return true
    }

    /** Enter the mode with one icon already selected (from an icon's menu). */
    fun armWith(id: String, itemPage: Int, baseline: LayoutSnapshot): Boolean {
        if (!machine.arm(withSelection = true)) return false
        resetTransient()
        selected = setOf(id)
        page = itemPage
        mode = SelectionMode.Add
        transaction = LassoTransaction(baseline)
        state = machine.state
        return true
    }

    /** Leave the mode. Does not touch the layout: rolling back, if wanted, is the caller's job (see the transaction). */
    fun exit() {
        machine.exit()
        resetTransient()
        selected = emptySet()
        page = -1
        mode = SelectionMode.Add
        moveAnywhere = false
        message = null
        transaction = null
        state = machine.state
    }

    private fun resetTransient() {
        path.clear()
        pathTick++
        strokeHits = emptySet()
        dragIds = emptyList()
        dragAnchor = null
        dragStartTiles = emptyMap()
        dragDelta = Offset.Zero
        dropPlan = null
    }

    /** Use a stroke buffer whose point spacing suits the screen density. */
    fun configurePath(minStepPx: Float) {
        if (path.size == 0) path = LassoPath(minStep = minStepPx)
    }

    // ---- stroke ---------------------------------------------------------------------------------------------

    fun beginStroke(x: Float, y: Float): Boolean {
        if (!machine.beginStroke()) return false
        path.clear()
        path.add(x, y)
        strokeHits = emptySet()
        pathTick++
        state = machine.state
        return true
    }

    fun addStrokePoint(x: Float, y: Float): Boolean {
        if (state != LassoState.DRAWING) return false
        val kept = path.add(x, y)
        if (kept) pathTick++
        return kept
    }

    fun updateStrokeHits(hits: Set<String>) {
        if (hits != strokeHits) strokeHits = HashSet(hits)
    }

    /** The stroke finished on [onPage]: apply what it selected under the current mode. */
    fun endStroke(hits: Set<String>, onPage: Int): Boolean {
        if (state != LassoState.DRAWING) return false
        val base = if (page != -1 && page != onPage) emptySet() else selected
        val next = LassoHitTester.apply(base, hits, mode)
        selected = next
        page = if (next.isEmpty()) -1 else onPage
        strokeHits = emptySet()
        strokeSerial++
        machine.endStroke(next.size)
        state = machine.state
        return true
    }

    fun cancelStroke() {
        if (!machine.cancelStroke()) return
        strokeHits = emptySet()
        strokeSerial++
        state = machine.state
    }

    // ---- selection ------------------------------------------------------------------------------------------

    /** A tap on an icon of [onPage]. */
    fun tap(id: String, onPage: Int) {
        if (state != LassoState.ARMED && state != LassoState.SELECTED) return
        val base = if (page != -1 && page != onPage) emptySet() else selected
        val next = LassoHitTester.tap(base, id, mode)
        applySelection(next, onPage)
    }

    /** Replace the selection with [ids] of [onPage] (select all). */
    fun setSelection(ids: Collection<String>, onPage: Int) {
        if (state != LassoState.ARMED && state != LassoState.SELECTED) return
        applySelection(LinkedHashSet(ids), onPage)
    }

    fun clearSelection() {
        if (state != LassoState.ARMED && state != LassoState.SELECTED) return
        applySelection(emptySet(), -1)
    }

    private fun applySelection(next: Set<String>, onPage: Int) {
        selected = next
        page = if (next.isEmpty()) -1 else onPage
        machine.selectionChanged(next.size)
        state = machine.state
    }

    /** The selection moved to another page (after "move to page"): same icons, new home. */
    fun followToPage(newPage: Int) {
        if (selected.isNotEmpty()) page = newPage
    }

    /** Keep only icons that still exist (after an action removed or merged some). */
    fun retainOnly(existing: Set<String>) {
        val next = selected.filterTo(LinkedHashSet()) { it in existing }
        if (next.size != selected.size) applySelection(next, page)
    }

    // ---- group drag -----------------------------------------------------------------------------------------

    fun beginDrag(anchorId: String, ids: List<String>, startTiles: Map<String, Box>): Boolean {
        if (!machine.beginDrag()) return false
        dragAnchor = anchorId
        dragIds = ids
        dragStartTiles = startTiles
        dragDelta = Offset.Zero
        dropPlan = null
        state = machine.state
        return true
    }

    /** The drag is over (dropped, snapped back, or abandoned): show the real icons again. */
    fun finishDrag() {
        if (state != LassoState.DRAGGING) return
        dragIds = emptyList()
        dragAnchor = null
        dragStartTiles = emptyMap()
        dragDelta = Offset.Zero
        dropPlan = null
        machine.endDrag()
        state = machine.state
    }

    /** The workspace changed size or shape: whatever was in flight no longer lines up, so drop it. */
    fun geometryChanged() {
        when (state) {
            LassoState.DRAWING -> cancelStroke()
            LassoState.DRAGGING -> finishDrag()
            else -> Unit
        }
    }

    // ---- icon geometry --------------------------------------------------------------------------------------

    /** An icon's on-screen footprint, in root pixels. Cheap enough to call on every layout. */
    fun registerTile(id: String, box: Box) {
        rootTiles[id] = box
        if (active) layoutTick++
    }

    fun unregisterTile(id: String) {
        rootTiles.remove(id)
    }

    fun tileRoot(id: String): Box? = rootTiles[id]

    /** Every known tile translated into workspace-local pixels (origin = the workspace's top-left in root space). */
    fun tilesLocal(originX: Float, originY: Float): Map<String, Box> {
        val out = HashMap<String, Box>(rootTiles.size * 2)
        for ((id, box) in rootTiles) out[id] = box.translate(-originX, -originY)
        return out
    }

    val selectionCount: Int get() = selected.size

    private companion object {
        const val DEFAULT_MIN_STEP = 12f
    }
}
