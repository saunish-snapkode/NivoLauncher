package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.data.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HitTestingTest {
    private val policy = SelectionPolicy()

    /** A 4-column grid of 100px cells with 60px tiles centred in each. */
    private fun tile(col: Int, row: Int, size: Float = 60f): Box {
        val cx = col * 100f + 50f
        val cy = row * 100f + 50f
        return Box(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
    }

    private fun candidates(vararg cells: Pair<Int, Int>) =
        cells.map { (c, r) -> Candidate("app-$c-$r", tile(c, r)) }

    private fun loopAround(left: Float, top: Float, right: Float, bottom: Float): LassoPath {
        val p = LassoPath(minStep = 2f)
        var x = left; while (x < right) { p.add(x, top); x += 6f }
        var y = top; while (y < bottom) { p.add(right, y); y += 6f }
        x = right; while (x > left) { p.add(x, bottom); x -= 6f }
        y = bottom; while (y > top) { p.add(left, y); y -= 6f }
        return p
    }

    @Test
    fun aLoopSelectsWhatItEncloses() {
        val cs = candidates(0 to 0, 1 to 0, 0 to 1, 1 to 1, 3 to 3)
        val out = HashSet<String>()
        LassoHitTester.hits(loopAround(5f, 5f, 195f, 195f), cs, policy, out)
        assertEquals(setOf("app-0-0", "app-1-0", "app-0-1", "app-1-1"), out)
    }

    @Test
    fun anIconMostlyOutsideIsNotSelected() {
        val cs = listOf(Candidate("edge", Box(80f, 10f, 140f, 70f)))   // 20 of 60px inside = 33%
        val out = HashSet<String>()
        LassoHitTester.hits(loopAround(0f, 0f, 100f, 100f), cs, policy, out)
        assertTrue(out.isEmpty())
    }

    @Test
    fun anIconMoreThanFortyPercentInsideIsSelected() {
        val cs = listOf(Candidate("edge", Box(70f, 10f, 130f, 70f)))   // 30 of 60px inside = 50%
        val out = HashSet<String>()
        LassoHitTester.hits(loopAround(0f, 0f, 100f, 100f), cs, policy, out)
        assertEquals(setOf("edge"), out)
    }

    @Test
    fun coverageThresholdIsConfigurable() {
        val cs = listOf(Candidate("edge", Box(80f, 10f, 140f, 70f)))   // 33% covered
        val strict = HashSet<String>(); val lax = HashSet<String>()
        LassoHitTester.hits(loopAround(0f, 0f, 100f, 100f), cs, SelectionPolicy(minCoverage = 0.5f), strict)
        LassoHitTester.hits(loopAround(0f, 0f, 100f, 100f), cs, SelectionPolicy(minCoverage = 0.3f), lax)
        assertTrue(strict.isEmpty())
        assertEquals(setOf("edge"), lax)
    }

    @Test
    fun anOpenStrokeIsClosedLogically() {
        // A "C" around the first two icons, left open on the right.
        val p = LassoPath(minStep = 2f)
        var x = 195f; while (x > 5f) { p.add(x, 5f); x -= 6f }
        var y = 5f; while (y < 95f) { p.add(5f, y); y += 6f }
        x = 5f; while (x < 195f) { p.add(x, 95f); x += 6f }
        p.add(195f, 95f)
        val cs = candidates(0 to 0, 1 to 0, 3 to 0)
        val out = HashSet<String>()
        LassoHitTester.hits(p, cs, policy, out)
        assertEquals(setOf("app-0-0", "app-1-0"), out)
    }

    @Test
    fun aLineSelectsWhatItTouches() {
        val p = LassoPath(minStep = 2f)
        var x = 0f; while (x <= 400f) { p.add(x, 50f); x += 8f }
        val cs = candidates(0 to 0, 1 to 0, 2 to 0, 3 to 0, 1 to 2)
        val out = HashSet<String>()
        assertFalse(LassoHitTester.isLoop(p, policy))
        LassoHitTester.hits(p, cs, policy, out)
        assertEquals(setOf("app-0-0", "app-1-0", "app-2-0", "app-3-0"), out)
    }

    @Test
    fun aLineThatMissesSelectsNothing() {
        val p = LassoPath(minStep = 2f)
        var x = 0f; while (x <= 400f) { p.add(x, 190f); x += 8f }
        val out = HashSet<String>()
        LassoHitTester.hits(p, candidates(0 to 0, 1 to 0), policy, out)
        assertTrue(out.isEmpty())
    }

    @Test
    fun aTinyStrokeIsATapNotASelection() {
        val p = LassoPath(minStep = 1f)
        p.add(50f, 50f); p.add(54f, 52f); p.add(56f, 56f)
        val out = HashSet<String>()
        LassoHitTester.hits(p, candidates(0 to 0), policy, out)
        assertTrue(out.isEmpty())
    }

    @Test
    fun aRoundLoopWorksToo() {
        val p = LassoPath(minStep = 2f)
        for (i in 0..72) {
            val a = Math.toRadians(i * 5.0)
            p.add(100f + 130f * cos(a).toFloat(), 100f + 130f * sin(a).toFloat())
        }
        val out = HashSet<String>()
        LassoHitTester.hits(p, candidates(0 to 0, 1 to 0, 0 to 1, 1 to 1), policy, out)
        assertEquals(4, out.size)
    }

    @Test
    fun hitsReusesTheOutputSet() {
        val out = HashSet<String>().apply { add("stale") }
        LassoHitTester.hits(loopAround(5f, 5f, 95f, 95f), candidates(0 to 0), policy, out)
        assertEquals(setOf("app-0-0"), out)
    }

    @Test
    fun hitAtFindsTheIconUnderAPoint() {
        val cs = candidates(0 to 0, 1 to 0)
        assertEquals("app-1-0", LassoHitTester.hitAt(cs, 150f, 50f))
        assertEquals(null, LassoHitTester.hitAt(cs, 150f, 95f))
    }

    // ---- additive and subtractive --------------------------------------------------------------------------

    @Test
    fun additiveSelectionAccumulates() {
        val first = LassoHitTester.apply(emptySet(), setOf("a", "b"), SelectionMode.Add)
        val second = LassoHitTester.apply(first, setOf("b", "c"), SelectionMode.Add)
        assertEquals(setOf("a", "b", "c"), second)
    }

    @Test
    fun subtractiveSelectionRemoves() {
        val start = setOf("a", "b", "c")
        assertEquals(setOf("a"), LassoHitTester.apply(start, setOf("b", "c", "zzz"), SelectionMode.Subtract))
    }

    @Test
    fun nothingNewReturnsTheSameInstance() {
        val start = setOf("a", "b")
        assertSame(start, LassoHitTester.apply(start, setOf("a"), SelectionMode.Add))
        assertSame(start, LassoHitTester.apply(start, setOf("x"), SelectionMode.Subtract))
    }

    @Test
    fun tapTogglesInAddModeAndRemovesInSubtractMode() {
        val s = setOf("a")
        assertEquals(setOf("a", "b"), LassoHitTester.tap(s, "b", SelectionMode.Add))
        assertEquals(emptySet<String>(), LassoHitTester.tap(s, "a", SelectionMode.Add))
        assertEquals(emptySet<String>(), LassoHitTester.tap(s, "a", SelectionMode.Subtract))
        assertSame(s, LassoHitTester.tap(s, "b", SelectionMode.Subtract))
    }

    @Test
    fun selectionOrderIsStable() {
        var s: Set<String> = emptySet()
        for (id in listOf("c", "a", "b")) s = LassoHitTester.tap(s, id, SelectionMode.Add)
        assertEquals(listOf("c", "a", "b"), s.toList())
    }

    // ---- pages ---------------------------------------------------------------------------------------------

    @Test
    fun onlyTheCurrentPagesIconsAreCandidates() {
        val items = listOf(
            HomeItem("p0-a", 0, 0, 0), HomeItem("p0-b", 0, 1, 0),
            HomeItem("p1-a", 1, 0, 0),           // same cell, next page
            HomeItem("dock", HomeItem.DOCK, 0, 0),
            HomeItem("widget:7", 0, 2, 0, 2, 1),
        )
        // On screen during a page transition both pages' tiles overlap the viewport: same rectangles.
        val tiles = mapOf(
            "p0-a" to tile(0, 0), "p0-b" to tile(1, 0), "p1-a" to tile(0, 0),
            "dock" to tile(0, 0), "widget:7" to tile(2, 0),
        )
        val page0 = LassoCandidates.forPage(items, 0, tiles)
        assertEquals(setOf("p0-a", "p0-b"), page0.map { it.id }.toSet())
        val out = HashSet<String>()
        LassoHitTester.hits(loopAround(5f, 5f, 195f, 95f), page0, policy, out)
        assertEquals(setOf("p0-a", "p0-b"), out)              // p1-a shares p0-a's rectangle but is never selected
        assertEquals(setOf("p1-a"), LassoCandidates.forPage(items, 1, tiles).map { it.id }.toSet())
    }

    @Test
    fun iconsNotYetLaidOutAreSkipped() {
        val items = listOf(HomeItem("a", 0, 0, 0), HomeItem("b", 0, 1, 0))
        val cs = LassoCandidates.forPage(items, 0, mapOf("a" to tile(0, 0)))
        assertEquals(listOf("a"), cs.map { it.id })
    }

    @Test
    fun rectanglesTranslateBetweenCoordinateSpaces() {
        // The overlay converts root coordinates to workspace-local ones once; the transform must round-trip.
        val rootTile = Box(1100f, 400f, 1160f, 460f)
        val origin = 1080f to 300f
        val local = rootTile.translate(-origin.first, -origin.second)
        assertEquals(20f, local.left, 0f); assertEquals(100f, local.top, 0f)
        val back = local.translate(origin.first, origin.second)
        assertEquals(rootTile.left, back.left, 0f); assertEquals(rootTile.bottom, back.bottom, 0f)
    }
}
