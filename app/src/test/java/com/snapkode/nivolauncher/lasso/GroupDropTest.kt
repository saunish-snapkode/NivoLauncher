package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.data.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupDropTest {
    private val grid = Grid(4, 5)
    private fun app(id: String, col: Int, row: Int, page: Int = 0) = HomeItem(id, page, col, row)
    private fun cells(p: DropPlan) = p.placements.map { it.id to (it.col to it.row) }.toMap()

    @Test
    fun theGroupKeepsItsShape() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("c", 0, 1))
        val p = GroupDropPlanner.plan(items, listOf("a", "b", "c"), grid, 0, "a", 2, 3)
        assertTrue(p.valid)
        assertEquals(mapOf("a" to (2 to 3), "b" to (3 to 3), "c" to (2 to 4)), cells(p))
        assertFalse(p.snapped)
    }

    @Test
    fun droppingOnTheSamePlaceIsANoop() {
        val items = listOf(app("a", 1, 1), app("b", 2, 1))
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 0, "a", 1, 1)
        assertTrue(p.isNoop(0))
        assertFalse(p.isNoop(1))
    }

    @Test
    fun theGroupMayOverlapItsOwnOldCells() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 0, "a", 1, 0)    // shift right by one
        assertTrue(p.valid)
        assertEquals(mapOf("a" to (1 to 0), "b" to (2 to 0)), cells(p))
    }

    @Test
    fun anOffGridDropSnapsToTheNearestValidPlace() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        // Anchor at column 3 would put "b" at column 4, outside a 4-column grid.
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 0, "a", 3, 0)
        assertTrue(p.valid)
        assertTrue(p.snapped)
        assertEquals(mapOf("a" to (2 to 0), "b" to (3 to 0)), cells(p))
    }

    @Test
    fun aDropFarOffTheGridIsInvalid() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 0, "a", 9, 9)
        assertFalse(p.valid)
        assertNull("an invalid plan can not be applied", GroupDropPlanner.apply(items, p))
    }

    @Test
    fun occupiedCellsAreNeverOverwritten() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0)) + (0 until 20).filter { it !in setOf(0, 1) }
            .map { app("x$it", it % 4, it / 4, page = 1) }
        // Page 1 has only cells (0,0) and (1,0) free, which are taken by nothing; fill them to block.
        val blocked = items + app("y1", 0, 0, page = 1) + app("y2", 1, 0, page = 1)
        val p = GroupDropPlanner.plan(blocked, listOf("a", "b"), grid, 1, "a", 0, 0)
        assertFalse(p.valid)
        assertNull(GroupDropPlanner.apply(blocked, p))
    }

    @Test
    fun aWidgetsWholeSpanBlocksTheDrop() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), HomeItem("widget:3", 1, 0, 0, 4, 2))
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 1, "a", 1, 1)    // inside the 4x2 widget
        // It may snap below the widget, but never onto it.
        if (p.valid) for (pl in p.placements) assertTrue("placed on the widget at (${pl.col},${pl.row})", pl.row >= 2)
        else assertTrue(p.placements.any { it.row < 2 })
    }

    @Test
    fun droppingOntoAnUnselectedIconIsInvalidSoNothingSilentlyMerges() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("victim", 2, 2))
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 0, "a", 2, 2, magnet = 0)
        assertFalse(p.valid)
        assertNull(GroupDropPlanner.apply(items, p))
    }

    @Test
    fun aGroupCanMoveToAnotherPage() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("there", 0, 0, page = 1))
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 1, "a", 1, 2)
        assertTrue(p.valid)
        val applied = GroupDropPlanner.apply(items, p)!!
        assertEquals(1, applied.first { it.id == "a" }.page)
        assertEquals(items.first { it.id == "there" }, applied.first { it.id == "there" })
    }

    @Test
    fun noPageMeansNoDrop() {
        val items = listOf(app("a", 0, 0))
        assertFalse(GroupDropPlanner.plan(items, listOf("a"), grid, -1, "a", 0, 0).valid)
    }

    @Test
    fun applyOnlyTouchesTheGroup() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("c", 3, 4))
        val p = GroupDropPlanner.plan(items, listOf("a", "b"), grid, 0, "a", 0, 2)
        val applied = GroupDropPlanner.apply(items, p)!!
        assertEquals(items.first { it.id == "c" }, applied.first { it.id == "c" })
        assertEquals(3, applied.size)
    }
}
