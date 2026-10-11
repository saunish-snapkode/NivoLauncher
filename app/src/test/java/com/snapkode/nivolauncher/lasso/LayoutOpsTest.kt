package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {
    private val grid = Grid(columns = 4, rows = 5)

    private fun app(id: String, col: Int, row: Int, page: Int = 0) = HomeItem(id, page, col, row)

    private fun ok(r: OpResult): OpResult.Ok = r as OpResult.Ok
    private fun fail(r: OpResult): FailReason = (r as OpResult.Fail).reason

    private fun List<HomeItem>.at(id: String) = first { it.id == id }

    /** No two items share a cell on the same page, whatever their spans. */
    private fun assertNoOverlap(items: List<HomeItem>) {
        val seen = HashSet<Triple<Int, Int, Int>>()
        for (i in items.filter { !it.inDock }) for (c in i.col until i.col + i.spanX) for (r in i.row until i.row + i.spanY) {
            assertTrue("cell ($c,$r) on page ${i.page} is used twice", seen.add(Triple(i.page, c, r)))
        }
    }

    // ---- align ---------------------------------------------------------------------------------------------

    @Test
    fun alignHorizontallyPutsEverythingOnOneRowInOrder() {
        val items = listOf(app("a", 0, 0), app("b", 2, 1), app("c", 3, 3), app("other", 1, 4))
        val r = ok(LayoutOps.alignHorizontally(items, setOf("a", "b", "c"), grid, 0))
        val rows = setOf("a", "b", "c").map { r.items.at(it).row }.toSet()
        assertEquals(1, rows.size)
        assertEquals(listOf(0, 2, 3), listOf("a", "b", "c").map { r.items.at(it).col })
        assertEquals(1, rows.first())
        assertEquals(items.at("other"), r.items.at("other"))
        assertNoOverlap(r.items)
    }

    @Test
    fun alignVerticallyPutsEverythingInOneColumn() {
        val items = listOf(app("a", 0, 0), app("b", 1, 2), app("c", 3, 4))
        val r = ok(LayoutOps.alignVertically(items, setOf("a", "b", "c"), grid, 0))
        assertEquals(1, setOf("a", "b", "c").map { r.items.at(it).col }.toSet().size)
        assertEquals(listOf(0, 2, 4), listOf("a", "b", "c").map { r.items.at(it).row })
        assertNoOverlap(r.items)
    }

    @Test
    fun alignSkipsARowThatIsBlockedAndNeverOverwrites() {
        // Row 1 is the natural choice but two of its cells hold other icons.
        val items = listOf(
            app("a", 0, 0), app("b", 2, 1), app("c", 3, 3),
            app("x", 1, 1), app("y", 3, 1), app("z", 0, 1),
        )
        val r = ok(LayoutOps.alignHorizontally(items, setOf("a", "b", "c"), grid, 0))
        assertNoOverlap(r.items)
        for (blocker in listOf("x", "y", "z")) assertEquals("$blocker must not move", items.at(blocker), r.items.at(blocker))
        assertEquals(1, setOf("a", "b", "c").map { r.items.at(it).row }.toSet().size)
        assertTrue("it picked a different row", r.items.at("a").row != 1)
    }

    @Test
    fun alignFailsCleanlyWhenNoRowHasRoom() {
        // Six selected icons cannot fit in any 4-wide row.
        val sel = (0 until 6).map { app("s$it", it % 4, it / 4) }
        assertEquals(FailReason.NoRoom, fail(LayoutOps.alignHorizontally(sel, sel.map { it.id }.toSet(), grid, 0)))
    }

    @Test
    fun alignOnlyActsOnTheGivenPage() {
        val items = listOf(app("a", 0, 0), app("b", 2, 2), app("c", 1, 1, page = 1))
        val r = ok(LayoutOps.alignHorizontally(items, setOf("a", "b", "c"), grid, 0))
        assertEquals(items.at("c"), r.items.at("c"))
    }

    @Test
    fun alignWithNothingSelectedFails() {
        assertEquals(FailReason.NothingSelected, fail(LayoutOps.alignHorizontally(listOf(app("a", 0, 0)), emptySet(), grid, 0)))
    }

    @Test
    fun aSingleIconIsAlreadyAligned() {
        val r = ok(LayoutOps.alignHorizontally(listOf(app("a", 2, 3)), setOf("a"), grid, 0))
        assertEquals(0, r.changed)
    }

    @Test
    fun alreadyAlignedMeansNothingChanged() {
        val items = listOf(app("a", 0, 2), app("b", 1, 2), app("c", 3, 2))
        assertEquals(0, ok(LayoutOps.alignHorizontally(items, setOf("a", "b", "c"), grid, 0)).changed)
    }

    // ---- distribute ----------------------------------------------------------------------------------------

    @Test
    fun distributeSpacesIconsEvenlyAlongTheirSpread() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("c", 3, 0))
        val r = ok(LayoutOps.distribute(items, setOf("a", "b", "c"), grid, 0))
        assertEquals(listOf(0, 2, 3), listOf("a", "b", "c").map { r.items.at(it).col })
        assertTrue(listOf("a", "b", "c").all { r.items.at(it).row == 0 })
    }

    @Test
    fun distributeWorksVerticallyWhenTheSpreadIsTaller() {
        val items = listOf(app("a", 1, 0), app("b", 1, 1), app("c", 1, 4))
        val r = ok(LayoutOps.distribute(items, setOf("a", "b", "c"), grid, 0))
        assertEquals(listOf(0, 2, 4), listOf("a", "b", "c").map { r.items.at(it).row })
    }

    @Test
    fun distributeUsesTheNearestFreeCellWhenTheIdealOneIsTaken() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("c", 3, 0), app("blocker", 2, 0))
        val r = ok(LayoutOps.distribute(items, setOf("a", "b", "c"), grid, 0))
        assertEquals(items.at("blocker"), r.items.at("blocker"))
        assertNoOverlap(r.items)
    }

    @Test
    fun distributeNeedsThreeIconsAndEnoughSpace() {
        assertEquals(FailReason.NeedsThree, fail(LayoutOps.distribute(listOf(app("a", 0, 0), app("b", 3, 0)), setOf("a", "b"), grid, 0)))
        val cramped = listOf(app("a", 0, 0), app("b", 1, 0), app("c", 1, 1))
        assertEquals(FailReason.NoRoom, fail(LayoutOps.distribute(cramped, setOf("a", "b", "c"), grid, 0)))
    }

    // ---- compact -------------------------------------------------------------------------------------------

    @Test
    fun compactPacksScatteredIconsIntoASmallBlock() {
        val items = listOf(app("a", 0, 0), app("b", 3, 0), app("c", 0, 4), app("d", 3, 4), app("e", 2, 2))
        val ids = items.map { it.id }.toSet()
        val r = ok(LayoutOps.compact(items, ids, grid, 0))
        val cols = ids.map { r.items.at(it).col }
        val rows = ids.map { r.items.at(it).row }
        assertTrue("block is at most 3 wide", cols.max() - cols.min() <= 2)
        assertTrue("block is at most 2 tall", rows.max() - rows.min() <= 1)
        assertNoOverlap(r.items)
    }

    @Test
    fun compactStepsAroundOtherIcons() {
        val items = listOf(app("a", 0, 0), app("b", 3, 4), app("c", 3, 0), app("n1", 1, 2), app("n2", 2, 2))
        val r = ok(LayoutOps.compact(items, setOf("a", "b", "c"), grid, 0))
        assertEquals(items.at("n1"), r.items.at("n1"))
        assertEquals(items.at("n2"), r.items.at("n2"))
        assertNoOverlap(r.items)
    }

    @Test
    fun compactFailsWhenThereIsNoRoomForABlock() {
        val full = (0 until 4).flatMap { c -> (0 until 5).map { r -> app("i$c$r", c, r) } }
        val selected = setOf("i00", "i33", "i12")
        // Selected cells count as free, but a 2x2 block needs 3 contiguous free cells and there are only 3 scattered.
        val r = LayoutOps.compact(full, selected, grid, 0)
        assertTrue(r is OpResult.Fail)
    }

    // ---- gather --------------------------------------------------------------------------------------------

    @Test
    fun gatherBringsIconsTogetherAroundTheirCentre() {
        val items = listOf(app("a", 0, 0), app("b", 3, 4), app("c", 0, 4))
        val r = ok(LayoutOps.gather(items, setOf("a", "b", "c"), grid, 0))
        val cells = setOf("a", "b", "c").map { r.items.at(it).col to r.items.at(it).row }
        assertEquals(3, cells.toSet().size)
        val spreadBefore = 3 + 4
        val spreadAfter = cells.maxOf { it.first } - cells.minOf { it.first } + cells.maxOf { it.second } - cells.minOf { it.second }
        assertTrue("tighter than before ($spreadAfter < $spreadBefore)", spreadAfter < spreadBefore)
        assertNoOverlap(r.items)
    }

    @Test
    fun gatherPullsInIconsFromOtherPages() {
        val items = listOf(app("a", 1, 1), app("b", 2, 2), app("far", 0, 0, page = 2))
        val r = ok(LayoutOps.gather(items, setOf("a", "b", "far"), grid, 0))
        assertEquals(0, r.items.at("far").page)
        assertNoOverlap(r.items)
    }

    @Test
    fun gatherLeavesOtherIconsAlone() {
        val items = listOf(app("a", 0, 0), app("b", 3, 3), app("keep", 1, 1), app("keep2", 2, 2))
        val r = ok(LayoutOps.gather(items, setOf("a", "b"), grid, 0))
        assertEquals(items.at("keep"), r.items.at("keep"))
        assertEquals(items.at("keep2"), r.items.at("keep2"))
        assertNoOverlap(r.items)
    }

    // ---- move to page --------------------------------------------------------------------------------------

    @Test
    fun moveToPageKeepsCellsWhereFreeAndFindsRoomForTheRest() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0), app("there", 0, 0, page = 1))
        val r = ok(LayoutOps.moveToPage(items, setOf("a", "b"), grid, 1))
        assertEquals(1, r.targetPage)
        assertEquals(1, r.items.at("a").page); assertEquals(1, r.items.at("b").page)
        assertEquals(items.at("there"), r.items.at("there"))
        assertEquals(1 to 0, r.items.at("b").col to r.items.at("b").row)       // kept its own cell
        assertEquals(2 to 0, r.items.at("a").col to r.items.at("a").row)       // (0,0) was taken, so first free
        assertNoOverlap(r.items)
    }

    @Test
    fun moveToANewEmptyPage() {
        val items = listOf(app("a", 2, 3), app("b", 0, 1))
        val r = ok(LayoutOps.moveToPage(items, setOf("a", "b"), grid, 5))
        assertTrue(r.items.all { it.page == 5 })
        assertEquals(2 to 3, r.items.at("a").col to r.items.at("a").row)
    }

    @Test
    fun moveToAFullPageFailsAndChangesNothing() {
        val full = (0 until 20).map { app("f$it", it % 4, it / 4, page = 1) }
        val items = listOf(app("a", 0, 0)) + full
        val r = LayoutOps.moveToPage(items, setOf("a"), grid, 1)
        assertEquals(FailReason.NoRoom, fail(r))
    }

    // ---- folder and remove ---------------------------------------------------------------------------------

    @Test
    fun createFolderFromSelectedAppsSkipsFolders() {
        val items = listOf(
            app("a", 2, 1), app("b", 0, 3), app("c", 1, 0), app("keep", 3, 3),
            app("folder:old", 3, 0),
        )
        val folders = mapOf("folder:old" to HomeFolder("folder:old", "Old", listOf("x", "y")))
        val r = ok(LayoutOps.createFolder(items, folders, setOf("a", "b", "c", "folder:old"), 0, "folder:new"))
        assertTrue(r.items.none { it.id in setOf("a", "b", "c") })
        val created = r.items.at("folder:new")
        assertEquals(1 to 0, created.col to created.row)                          // where the top-left-most app was
        assertEquals(listOf("c", "a", "b"), r.folders!!.getValue("folder:new").appIds)  // reading order
        assertTrue("the existing folder is untouched", r.items.any { it.id == "folder:old" })
        assertEquals(items.at("keep"), r.items.at("keep"))
        assertNoOverlap(r.items)
    }

    @Test
    fun createFolderNeedsTwoApps() {
        val items = listOf(app("a", 0, 0), app("folder:f", 1, 0))
        assertEquals(FailReason.NeedsTwo, fail(LayoutOps.createFolder(items, emptyMap(), setOf("a", "folder:f"), 0, "folder:n")))
        assertEquals(FailReason.NothingSelected, fail(LayoutOps.createFolder(items, emptyMap(), emptySet(), 0, "folder:n")))
    }

    @Test
    fun removeTakesIconsOffTheHomeScreenButNeverTouchesWidgetsOrTheDock() {
        val items = listOf(
            app("a", 0, 0), app("b", 1, 0), app("keep", 2, 0),
            HomeItem("widget:5", 0, 0, 1, 2, 2), HomeItem("dockApp", HomeItem.DOCK, 0, 0),
        )
        val r = ok(LayoutOps.remove(items, setOf("a", "b", "widget:5", "dockApp")))
        assertEquals(setOf("keep", "widget:5", "dockApp"), r.items.map { it.id }.toSet())
        assertEquals(2, r.changed)
    }

    // ---- selection helpers ---------------------------------------------------------------------------------

    @Test
    fun selectAllOnAPageGivesIconsInReadingOrderWithoutWidgets() {
        val items = listOf(
            app("c", 2, 1), app("a", 0, 0), app("b", 1, 0), app("other", 0, 0, page = 1),
            HomeItem("widget:1", 0, 0, 2, 2, 2), HomeItem("nivo:clock:x", 0, 0, 4, 4, 1),
        )
        assertEquals(listOf("a", "b", "c"), LayoutOps.selectableOn(items, 0))
    }

    @Test
    fun movableExcludesWidgetsSpannedItemsAndTheDock() {
        val items = listOf(app("a", 0, 0), HomeItem("big", 0, 1, 1, 2, 1), HomeItem("widget:2", 0, 2, 2), HomeItem("d", HomeItem.DOCK, 0, 0))
        assertEquals(listOf("a"), LayoutOps.movable(items, items.map { it.id }.toSet()).map { it.id })
    }

    @Test
    fun monotoneAssignKeepsOrderAndMinimisesTravel() {
        // Desired 0,0,5 into free {0,1,5}: the two icons that want 0 take 0 and 1, the other keeps 5.
        assertEquals(listOf(0, 1, 5), LayoutOps.monotoneAssign(listOf(0, 0, 5), listOf(0, 1, 5)).toList())
        assertEquals(listOf(2, 3), LayoutOps.monotoneAssign(listOf(2, 3), listOf(0, 2, 3, 4)).toList())
    }

    @Test
    fun noOperationEverMutatesItsInput() {
        val items = listOf(app("a", 0, 0), app("b", 2, 1), app("c", 3, 3))
        val copy = items.toList()
        LayoutOps.alignHorizontally(items, setOf("a", "b", "c"), grid, 0)
        LayoutOps.compact(items, setOf("a", "b", "c"), grid, 0)
        LayoutOps.gather(items, setOf("a", "b", "c"), grid, 0)
        LayoutOps.moveToPage(items, setOf("a", "b", "c"), grid, 2)
        assertEquals(copy, items)
    }

    @Test
    fun resultsAreDeterministic() {
        val items = listOf(app("a", 0, 0), app("b", 2, 1), app("c", 3, 3), app("d", 1, 4))
        val ids = setOf("a", "b", "c", "d")
        assertEquals(LayoutOps.gather(items, ids, grid, 0), LayoutOps.gather(items, ids, grid, 0))
        assertEquals(LayoutOps.compact(items, ids, grid, 0), LayoutOps.compact(items, ids, grid, 0))
        assertEquals(LayoutOps.alignVertically(items, ids, grid, 0), LayoutOps.alignVertically(items, ids, grid, 0))
    }
}
