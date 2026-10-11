package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.data.HomeItem
import kotlin.math.abs

class Placement(val id: String, val col: Int, val row: Int)

/**
 * Where a dragged group would land.
 *
 * @param valid every icon has a destination inside the grid that nothing outside the group occupies
 * @param deltaCol how far the group moved, in cells
 * @param placements one destination per icon, in the order the icons were given
 * @param snapped true when the exact drop position was blocked and a neighbouring one was used instead
 */
class DropPlan(
    val page: Int,
    val valid: Boolean,
    val deltaCol: Int,
    val deltaRow: Int,
    val placements: List<Placement>,
    val snapped: Boolean = false,
) {
    /** Dropping back exactly where the group came from. */
    fun isNoop(sourcePage: Int) = valid && page == sourcePage && deltaCol == 0 && deltaRow == 0
}

object GroupDropPlanner {
    /**
     * Plans a drop of [ids] (all on one source page) so the anchor icon lands on ([anchorCol], [anchorRow]) of
     * [targetPage]. Every other icon keeps its offset from the anchor, which preserves the group's shape.
     *
     * If the exact position is blocked it tries the neighbouring cells within [magnet] (nearest first), so a drop
     * that is a cell too far to the right still lands inside the grid. If nothing works the exact plan is returned
     * with `valid = false`, which is what the preview shows in red. Nothing is ever placed on an occupied cell.
     */
    fun plan(
        items: List<HomeItem>,
        ids: List<String>,
        grid: Grid,
        targetPage: Int,
        anchorId: String,
        anchorCol: Int,
        anchorRow: Int,
        magnet: Int = 1,
    ): DropPlan {
        val set = ids.toSet()
        val group = ids.mapNotNull { id -> items.firstOrNull { it.id == id && !it.inDock } }
        val anchor = group.firstOrNull { it.id == anchorId }
            ?: return DropPlan(targetPage, false, 0, 0, emptyList())
        if (targetPage < 0) return DropPlan(targetPage, false, 0, 0, group.map { Placement(it.id, it.col, it.row) })

        val occ = Occupancy(items, grid, set)
        val dc = anchorCol - anchor.col
        val dr = anchorRow - anchor.row

        fun placementsFor(c: Int, r: Int) = group.map { Placement(it.id, it.col + c, it.row + r) }
        fun fits(c: Int, r: Int) = group.all { occ.isFree(targetPage, it.col + c, it.row + r) }

        if (fits(dc, dr)) return DropPlan(targetPage, true, dc, dr, placementsFor(dc, dr))

        // Nearest neighbouring positions first, so snapping moves the group as little as possible.
        val tries = ArrayList<Pair<Int, Int>>()
        for (a in -magnet..magnet) for (b in -magnet..magnet) if (a != 0 || b != 0) tries.add(a to b)
        tries.sortWith(compareBy({ abs(it.first) + abs(it.second) }, { it.second }, { it.first }))
        for ((a, b) in tries) {
            if (fits(dc + a, dr + b)) return DropPlan(targetPage, true, dc + a, dr + b, placementsFor(dc + a, dr + b), snapped = true)
        }
        return DropPlan(targetPage, false, dc, dr, placementsFor(dc, dr))
    }

    /** Applies a valid [plan] to [items]. Returns null for an invalid plan, so nothing can be applied by mistake. */
    fun apply(items: List<HomeItem>, plan: DropPlan): List<HomeItem>? {
        if (!plan.valid) return null
        val byId = plan.placements.associateBy { it.id }
        return items.map { item ->
            val p = byId[item.id]
            if (p != null) item.copy(page = plan.page, col = p.col, row = p.row) else item
        }
    }
}
