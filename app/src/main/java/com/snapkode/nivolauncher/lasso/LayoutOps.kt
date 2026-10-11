package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.isAnyWidgetId
import com.snapkode.nivolauncher.data.isFolderId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The workspace grid size (columns by rows) the operations place things on. */
data class Grid(val columns: Int, val rows: Int) {
    fun contains(col: Int, row: Int) = col in 0 until columns && row in 0 until rows
}

enum class FailReason(val message: String) {
    NothingSelected("Select some icons first"),
    NoRoom("Not enough free space for that"),
    NeedsTwo("Pick at least two apps to make a folder"),
    NeedsThree("Pick at least three icons to distribute"),
}

sealed interface OpResult {
    /**
     * @param changed how many icons actually moved; zero means the layout was already as requested.
     * @param folders the new folder table when the operation created or changed folders, else null.
     * @param targetPage the page the user should be taken to, when the operation moved icons to another page.
     */
    data class Ok(
        val items: List<HomeItem>,
        val changed: Int,
        val folders: Map<String, HomeFolder>? = null,
        val targetPage: Int? = null,
    ) : OpResult

    data class Fail(val reason: FailReason) : OpResult
}

/** A restorable copy of the home layout: what a rollback or an undo puts back. */
data class LayoutSnapshot(val items: List<HomeItem>, val folders: Map<String, HomeFolder>, val pageCount: Int)

/** Which cells of each page are taken, by everything except the ids in `ignore` (the icons being moved). */
class Occupancy(items: List<HomeItem>, private val grid: Grid, ignore: Set<String>) {
    private val pages = HashMap<Int, BooleanArray>()

    init {
        for (item in items) {
            if (item.inDock || item.id in ignore) continue
            for (c in item.col until item.col + item.spanX) for (r in item.row until item.row + item.spanY) {
                if (grid.contains(c, r)) mark(item.page, c, r)
            }
        }
    }

    private fun cells(page: Int) = pages.getOrPut(page) { BooleanArray(grid.columns * grid.rows) }

    private fun mark(page: Int, col: Int, row: Int) {
        cells(page)[row * grid.columns + col] = true
    }

    fun isFree(page: Int, col: Int, row: Int) = grid.contains(col, row) && !cells(page)[row * grid.columns + col]

    fun claim(page: Int, col: Int, row: Int) = mark(page, col, row)
}

/**
 * Batch layout operations on the home screen. All are pure: they take the current items and return the new ones, or
 * a reason they could not. None ever places an icon on a cell that something else holds, and none touches widgets.
 */
object LayoutOps {
    /** Icons the lasso can act on: plain apps and folders on a workspace page. Never widgets, never the dock. */
    fun movable(items: List<HomeItem>, ids: Set<String>): List<HomeItem> =
        items.filter { it.id in ids && !it.inDock && !isAnyWidgetId(it.id) && it.spanX == 1 && it.spanY == 1 }

    /** Every selectable icon on [page]. */
    fun selectableOn(items: List<HomeItem>, page: Int): List<String> =
        items.filter { it.page == page && !isAnyWidgetId(it.id) && it.spanX == 1 && it.spanY == 1 }
            .sortedWith(compareBy({ it.row }, { it.col })).map { it.id }

    private fun readingOrder() = compareBy<HomeItem>({ it.page }, { it.row }, { it.col }, { it.id })

    private fun applyMoves(items: List<HomeItem>, moves: Map<String, HomeItem>): OpResult.Ok {
        var changed = 0
        val out = items.map { item ->
            val moved = moves[item.id]
            if (moved != null && (moved.page != item.page || moved.col != item.col || moved.row != item.row)) {
                changed++
                moved
            } else item
        }
        return OpResult.Ok(out, changed)
    }

    // ---- Align ----------------------------------------------------------------------------------------------

    /** Puts the selected icons of [page] on one row (a horizontal line), keeping their left-to-right order. */
    fun alignHorizontally(items: List<HomeItem>, selected: Set<String>, grid: Grid, page: Int): OpResult =
        align(items, selected, grid, page, horizontal = true)

    /** Puts the selected icons of [page] in one column (a vertical line), keeping their top-to-bottom order. */
    fun alignVertically(items: List<HomeItem>, selected: Set<String>, grid: Grid, page: Int): OpResult =
        align(items, selected, grid, page, horizontal = false)

    private fun align(items: List<HomeItem>, selected: Set<String>, grid: Grid, page: Int, horizontal: Boolean): OpResult {
        val sel = movable(items, selected).filter { it.page == page }
        if (sel.isEmpty()) return OpResult.Fail(FailReason.NothingSelected)
        if (sel.size == 1) return OpResult.Ok(items, 0)

        fun line(i: HomeItem) = if (horizontal) i.row else i.col
        fun along(i: HomeItem) = if (horizontal) i.col else i.row
        val lineCount = if (horizontal) grid.rows else grid.columns
        val alongCount = if (horizontal) grid.columns else grid.rows
        val occ = Occupancy(items, grid, selected)
        val sorted = sel.sortedWith(compareBy({ along(it) }, { line(it) }, { it.id }))
        val mean = sel.map { line(it) }.average()
        val order = (0 until lineCount).sortedWith(compareBy({ abs(it - mean) }, { it }))

        for (candidate in order) {
            val free = (0 until alongCount).filter { a ->
                if (horizontal) occ.isFree(page, a, candidate) else occ.isFree(page, candidate, a)
            }
            if (free.size < sorted.size) continue
            val chosen = monotoneAssign(sorted.map { along(it) }, free)
            val moves = HashMap<String, HomeItem>()
            sorted.forEachIndexed { i, item ->
                moves[item.id] = if (horizontal) item.copy(col = chosen[i], row = candidate) else item.copy(col = candidate, row = chosen[i])
            }
            return applyMoves(items, moves)
        }
        return OpResult.Fail(FailReason.NoRoom)
    }

    /**
     * Picks one of the ascending [free] positions for each desired position, keeping order and minimising total
     * travel. Classic O(n*m) dynamic programme with a running minimum.
     */
    internal fun monotoneAssign(desired: List<Int>, free: List<Int>): IntArray {
        val n = desired.size
        val m = free.size
        val inf = Int.MAX_VALUE / 4
        val dp = Array(n) { IntArray(m) { inf } }
        val from = Array(n) { IntArray(m) { -1 } }
        for (j in 0 until m) dp[0][j] = abs(desired[0] - free[j])
        for (i in 1 until n) {
            var bestPrev = inf
            var bestIdx = -1
            for (j in 0 until m) {
                // best dp[i-1][k] for k < j
                if (j > 0 && dp[i - 1][j - 1] < bestPrev) { bestPrev = dp[i - 1][j - 1]; bestIdx = j - 1 }
                if (bestIdx >= 0) { dp[i][j] = bestPrev + abs(desired[i] - free[j]); from[i][j] = bestIdx }
            }
        }
        var j = (0 until m).minByOrNull { dp[n - 1][it] }!!
        val result = IntArray(n)
        for (i in n - 1 downTo 0) {
            result[i] = free[j]
            if (i > 0) j = from[i][j]
        }
        return result
    }

    // ---- Distribute -----------------------------------------------------------------------------------------

    /**
     * Spaces the selected icons of [page] evenly along whichever axis they are more spread on, keeping the first and
     * last where they are and each icon's other coordinate unchanged. If the ideal cell is taken, the nearest free
     * cell on the same line is used.
     */
    fun distribute(items: List<HomeItem>, selected: Set<String>, grid: Grid, page: Int): OpResult {
        val sel = movable(items, selected).filter { it.page == page }
        if (sel.isEmpty()) return OpResult.Fail(FailReason.NothingSelected)
        if (sel.size < 3) return OpResult.Fail(FailReason.NeedsThree)

        val horizontal = (sel.maxOf { it.col } - sel.minOf { it.col }) >= (sel.maxOf { it.row } - sel.minOf { it.row })
        fun along(i: HomeItem) = if (horizontal) i.col else i.row
        fun other(i: HomeItem) = if (horizontal) i.row else i.col
        val sorted = sel.sortedWith(compareBy({ along(it) }, { other(it) }, { it.id }))
        val first = along(sorted.first())
        val last = along(sorted.last())
        val span = last - first
        if (span + 1 < sorted.size) return OpResult.Fail(FailReason.NoRoom)

        val occ = Occupancy(items, grid, selected)
        val alongMax = if (horizontal) grid.columns else grid.rows
        val moves = HashMap<String, HomeItem>()
        for ((i, item) in sorted.withIndex()) {
            val ideal = first + (span * i / (sorted.size - 1.0)).roundToInt()
            var placedAt = -1
            var d = 0
            while (placedAt < 0 && d <= alongMax) {
                for (cand in if (d == 0) listOf(ideal) else listOf(ideal - d, ideal + d)) {
                    if (cand !in 0 until alongMax) continue
                    val c = if (horizontal) cand else other(item)
                    val r = if (horizontal) other(item) else cand
                    if (occ.isFree(page, c, r)) { placedAt = cand; break }
                }
                d++
            }
            if (placedAt < 0) return OpResult.Fail(FailReason.NoRoom)
            val c = if (horizontal) placedAt else other(item)
            val r = if (horizontal) other(item) else placedAt
            occ.claim(page, c, r)
            moves[item.id] = item.copy(col = c, row = r)
        }
        return applyMoves(items, moves)
    }

    // ---- Compact grid ---------------------------------------------------------------------------------------

    /** Packs the selected icons of [page] into the smallest near-square block, as close to where they were as free cells allow. */
    fun compact(items: List<HomeItem>, selected: Set<String>, grid: Grid, page: Int): OpResult {
        val sel = movable(items, selected).filter { it.page == page }
        if (sel.isEmpty()) return OpResult.Fail(FailReason.NothingSelected)
        if (sel.size == 1) return OpResult.Ok(items, 0)

        val n = sel.size
        val occ = Occupancy(items, grid, selected)
        val centreCol = sel.map { it.col }.average()
        val centreRow = sel.map { it.row }.average()
        val ordered = sel.sortedWith(readingOrder())

        // Block shapes, smallest area first, then closest to square.
        val start = ceil(sqrt(n.toDouble())).toInt().coerceAtLeast(1)
        val shapes = (start..grid.columns).map { w -> w to ceil(n / w.toDouble()).toInt() }
            .filter { (_, h) -> h <= grid.rows }
            .sortedWith(compareBy({ it.first * it.second }, { abs(it.first - it.second) }))

        for ((w, h) in shapes) {
            val anchors = ArrayList<Pair<Int, Int>>()
            for (ar in 0..grid.rows - h) for (ac in 0..grid.columns - w) anchors.add(ac to ar)
            anchors.sortBy { (ac, ar) -> abs(ac + (w - 1) / 2.0 - centreCol) + abs(ar + (h - 1) / 2.0 - centreRow) }
            for ((ac, ar) in anchors) {
                val cells = ArrayList<Pair<Int, Int>>(n)
                loop@ for (r in 0 until h) for (c in 0 until w) {
                    if (cells.size == n) break@loop
                    cells.add(ac + c to ar + r)
                }
                if (cells.size == n && cells.all { (c, r) -> occ.isFree(page, c, r) }) {
                    val moves = HashMap<String, HomeItem>()
                    ordered.forEachIndexed { i, item -> moves[item.id] = item.copy(col = cells[i].first, row = cells[i].second) }
                    return applyMoves(items, moves)
                }
            }
        }
        return OpResult.Fail(FailReason.NoRoom)
    }

    // ---- Gather ---------------------------------------------------------------------------------------------

    /**
     * Brings the selected icons together onto [page], around their centre of mass. Icons already on the page keep as
     * close to their cell as possible; icons from other pages are pulled in too.
     */
    fun gather(items: List<HomeItem>, selected: Set<String>, grid: Grid, page: Int): OpResult {
        val sel = movable(items, selected)
        if (sel.isEmpty()) return OpResult.Fail(FailReason.NothingSelected)

        val here = sel.filter { it.page == page }
        val centreCol = if (here.isNotEmpty()) here.map { it.col }.average() else (grid.columns - 1) / 2.0
        val centreRow = if (here.isNotEmpty()) here.map { it.row }.average() else (grid.rows - 1) / 2.0
        val occ = Occupancy(items, grid, selected)

        val freeCells = ArrayList<Pair<Int, Int>>()
        for (r in 0 until grid.rows) for (c in 0 until grid.columns) if (occ.isFree(page, c, r)) freeCells.add(c to r)
        if (freeCells.size < sel.size) return OpResult.Fail(FailReason.NoRoom)
        // The cells nearest the centre of mass, ties broken in reading order so the result is deterministic.
        val target = freeCells
            .sortedWith(compareBy({ (c, r) -> (c - centreCol) * (c - centreCol) + (r - centreRow) * (r - centreRow) }, { it.second }, { it.first }))
            .take(sel.size)

        // Match icons to those cells greedily by shortest distance, so nothing travels further than it has to.
        data class Pair2(val dist: Double, val item: HomeItem, val cell: Pair<Int, Int>)
        val pairs = ArrayList<Pair2>()
        for (item in sel) for (cell in target) {
            val dx = (cell.first - item.col).toDouble()
            val dy = (cell.second - item.row).toDouble()
            pairs.add(Pair2(dx * dx + dy * dy, item, cell))
        }
        pairs.sortWith(compareBy({ it.dist }, { it.cell.second }, { it.cell.first }, { it.item.id }))
        val usedItems = HashSet<String>()
        val usedCells = HashSet<Pair<Int, Int>>()
        val moves = HashMap<String, HomeItem>()
        for (p in pairs) {
            if (p.item.id in usedItems || p.cell in usedCells) continue
            usedItems.add(p.item.id); usedCells.add(p.cell)
            moves[p.item.id] = p.item.copy(page = page, col = p.cell.first, row = p.cell.second)
        }
        return applyMoves(items, moves)
    }

    // ---- Move to another page --------------------------------------------------------------------------------

    /**
     * Moves every selected icon to [targetPage]. Each keeps its own cell if that is free there; the rest take the
     * first free cells in reading order. Fails, changing nothing, if the page cannot hold them all.
     */
    fun moveToPage(items: List<HomeItem>, selected: Set<String>, grid: Grid, targetPage: Int): OpResult {
        val sel = movable(items, selected)
        if (sel.isEmpty()) return OpResult.Fail(FailReason.NothingSelected)
        val occ = Occupancy(items, grid, selected)
        val moves = HashMap<String, HomeItem>()
        val leftovers = ArrayList<HomeItem>()
        for (item in sel.sortedWith(readingOrder())) {
            if (occ.isFree(targetPage, item.col, item.row)) {
                occ.claim(targetPage, item.col, item.row)
                moves[item.id] = item.copy(page = targetPage)
            } else leftovers.add(item)
        }
        for (item in leftovers) {
            var spot: Pair<Int, Int>? = null
            search@ for (r in 0 until grid.rows) for (c in 0 until grid.columns) {
                if (occ.isFree(targetPage, c, r)) { spot = c to r; break@search }
            }
            val s = spot ?: return OpResult.Fail(FailReason.NoRoom)
            occ.claim(targetPage, s.first, s.second)
            moves[item.id] = item.copy(page = targetPage, col = s.first, row = s.second)
        }
        return applyMoves(items, moves).let { it.copy(targetPage = targetPage) }
    }

    // ---- Folder and remove ----------------------------------------------------------------------------------

    /**
     * Turns the selected plain apps on [page] into one folder placed where the top-left-most of them was. Folders in
     * the selection are left alone. Needs at least two apps.
     */
    fun createFolder(
        items: List<HomeItem>,
        folders: Map<String, HomeFolder>,
        selected: Set<String>,
        page: Int,
        newFolderId: String,
        name: String = "Folder",
    ): OpResult {
        val apps = movable(items, selected).filter { it.page == page && !isFolderId(it.id) }.sortedWith(readingOrder())
        if (apps.isEmpty()) return OpResult.Fail(FailReason.NothingSelected)
        if (apps.size < 2) return OpResult.Fail(FailReason.NeedsTwo)
        val anchor = apps.first()
        val ids = apps.map { it.id }.toSet()
        val out = items.filterNot { it.id in ids } + HomeItem(newFolderId, page, anchor.col, anchor.row)
        return OpResult.Ok(out, changed = apps.size, folders = folders + (newFolderId to HomeFolder(newFolderId, name, apps.map { it.id })))
    }

    /** Takes the selected icons off the home screen. This is not an uninstall: the apps stay installed. */
    fun remove(items: List<HomeItem>, selected: Set<String>): OpResult {
        val sel = movable(items, selected)
        if (sel.isEmpty()) return OpResult.Fail(FailReason.NothingSelected)
        val ids = sel.map { it.id }.toSet()
        return OpResult.Ok(items.filterNot { it.id in ids }, changed = ids.size)
    }
}
