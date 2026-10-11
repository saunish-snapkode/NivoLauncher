package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionAndMotionTest {
    private fun snapshot(vararg items: HomeItem, pages: Int = 1, folders: Map<String, HomeFolder> = emptyMap()) =
        LayoutSnapshot(items.toList(), folders, pages)

    @Test
    fun undoReturnsActionsNewestFirst() {
        val h = LayoutHistory()
        val s1 = snapshot(HomeItem("a", 0, 0, 0))
        val s2 = snapshot(HomeItem("a", 0, 1, 0))
        h.push("Align", s1); h.push("Gather", s2)
        assertEquals("Gather", h.peek()!!.label)
        assertEquals(s2, h.pop()!!.snapshot)
        assertEquals(s1, h.pop()!!.snapshot)
        assertNull(h.pop())
        assertTrue(h.isEmpty)
    }

    @Test
    fun historyIsBounded() {
        val h = LayoutHistory(limit = 3)
        for (i in 0 until 10) h.push("step$i", snapshot(HomeItem("a", 0, i % 4, 0)))
        assertEquals(3, h.size)
        assertEquals("step9", h.pop()!!.label)
        assertEquals("step8", h.pop()!!.label)
        assertEquals("step7", h.pop()!!.label)
        assertNull(h.pop())
    }

    @Test
    fun aRestoredSnapshotIsExactlyWhatWasSaved() {
        val folders = mapOf("folder:f" to HomeFolder("folder:f", "F", listOf("x", "y")))
        val before = snapshot(HomeItem("a", 0, 0, 0), HomeItem("folder:f", 0, 1, 1), pages = 2, folders = folders)
        val h = LayoutHistory()
        h.push("Create folder", before)
        // The layout changes afterwards...
        val after = snapshot(HomeItem("a", 1, 3, 3), pages = 3)
        assertTrue(after != before)
        // ...and undo hands back an identical copy, folders and page count included.
        assertEquals(before, h.pop()!!.snapshot)
    }

    @Test
    fun aTransactionKnowsWhetherAnythingChanged() {
        val base = snapshot(HomeItem("a", 0, 0, 0), HomeItem("b", 0, 1, 0))
        val t = LassoTransaction(base)
        assertFalse(t.isDirty(base))
        assertFalse(t.isDirty(snapshot(HomeItem("a", 0, 0, 0), HomeItem("b", 0, 1, 0))))
        assertTrue(t.isDirty(snapshot(HomeItem("a", 0, 0, 0), HomeItem("b", 0, 2, 0))))
        assertTrue(t.isDirty(snapshot(HomeItem("a", 0, 0, 0))))
    }

    @Test
    fun aFailedOperationLeavesTheLayoutExactlyAsItWas() {
        val items = (0 until 20).map { HomeItem("f$it", 1, it % 4, it / 4) } + HomeItem("a", 0, 0, 0)
        val before = snapshot(*items.toTypedArray())
        val r = LayoutOps.moveToPage(items, setOf("a"), Grid(4, 5), 1)
        assertTrue(r is OpResult.Fail)
        // A failure carries no layout at all, so there is nothing that could be committed half-done.
        assertEquals(before, snapshot(*items.toTypedArray()))
    }

    // ---- motion --------------------------------------------------------------------------------------------

    @Test
    fun theDesignTargetsAreWithinTheBudget() {
        assertTrue(LassoMotion.SELECTION_HIGHLIGHT_MS in 100..150)
        assertTrue(LassoMotion.TOOLBAR_MS in 160..220)
        assertTrue(LassoMotion.GROUP_LIFT_MS in 180..260)
        assertTrue(LassoMotion.GROUP_PLACE_MS in 240..360)
    }

    @Test
    fun reducedMotionShortensEverythingButNeverToZero() {
        for (ms in listOf(LassoMotion.SELECTION_HIGHLIGHT_MS, LassoMotion.TOOLBAR_MS, LassoMotion.GROUP_PLACE_MS, 1, 3)) {
            val reduced = LassoMotion.duration(ms, reduced = true)
            assertTrue("$ms -> $reduced", reduced in 1..ms)
            assertTrue(reduced <= 60)
            assertEquals(ms, LassoMotion.duration(ms, reduced = false))
        }
    }

    @Test
    fun reducedMotionTurnsOffAmbientLoopsAndScaling() {
        assertFalse(LassoMotion.allowsAmbientMotion(reduced = true))
        assertTrue(LassoMotion.allowsAmbientMotion(reduced = false))
        assertEquals(1f, LassoMotion.liftScale(reduced = true), 0f)
        assertTrue(LassoMotion.liftScale(reduced = false) > 1f)
    }
}
