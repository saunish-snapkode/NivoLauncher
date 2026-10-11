package com.snapkode.nivolauncher.lasso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.math.cos
import kotlin.math.sin

/** Measurements, not just assertions: they print what they saw so a regression is visible in the test report. */
class PerformanceTest {
    private val policy = SelectionPolicy()

    private fun bigStroke(points: Int): LassoPath {
        val p = LassoPath(minStep = 1f)
        for (i in 0 until points) {
            val a = i * 2.0 * Math.PI / points
            p.add(540f + 420f * cos(a).toFloat() * (1f + 0.1f * sin(7 * a).toFloat()), 900f + 700f * sin(a).toFloat())
        }
        return p
    }

    /** 8 rows x 6 columns of icons filling a 1080x1800 workspace: far more than any real page holds. */
    private fun candidates() = (0 until 48).map {
        val c = it % 6; val r = it / 6
        val x = c * 180f + 90f; val y = r * 225f + 110f
        Candidate("i$it", Box(x - 50f, y - 50f, x + 50f, y + 50f))
    }

    @Test
    fun hitTestingOnEveryTouchPointIsFarInsideAFrameBudget() {
        val stroke = bigStroke(600)
        val cs = candidates()
        val out = HashSet<String>()
        repeat(200) { LassoHitTester.hits(stroke, cs, policy, out) }          // warm up the JIT
        val runs = 2000
        val start = System.nanoTime()
        repeat(runs) { LassoHitTester.hits(stroke, cs, policy, out) }
        val perRunMs = (System.nanoTime() - start) / 1e6 / runs
        println("PERF hit test: 600-point stroke x 48 icons = %.4f ms per evaluation".format(perRunMs))
        // A frame at 120 Hz is 8.3 ms. One evaluation must be a small fraction of that.
        assertTrue("took $perRunMs ms", perRunMs < 2.0)
        assertTrue(out.isNotEmpty())
    }

    @Test
    fun appendingPointsDoesNotAllocatePerPoint() {
        val bean = ManagementFactory.getThreadMXBean()
        val sun = bean as? com.sun.management.ThreadMXBean
        assumeTrue("allocation counters not available on this JVM", sun != null && sun.isThreadAllocatedMemorySupported)
        val p = LassoPath(initialCapacity = 4096, minStep = 1f)
        repeat(5) { p.clear(); for (i in 0 until 2000) p.add(i * 2f, (i % 50) * 3f) }     // warm up
        p.clear()
        val id = Thread.currentThread().id
        val before = sun!!.getThreadAllocatedBytes(id)
        for (i in 0 until 2000) p.add(i * 2f, (i % 50) * 3f)
        val bytes = sun.getThreadAllocatedBytes(id) - before
        println("PERF path append: 2000 points allocated $bytes bytes")
        assertEquals(2000, p.size)
        assertTrue("allocated $bytes bytes for 2000 points", bytes < 4096)
    }

    @Test
    fun theLiveHitTestAllocatesAlmostNothing() {
        val bean = ManagementFactory.getThreadMXBean()
        val sun = bean as? com.sun.management.ThreadMXBean
        assumeTrue("allocation counters not available on this JVM", sun != null && sun.isThreadAllocatedMemorySupported)
        val stroke = bigStroke(400)
        val cs = candidates()
        val out = HashSet<String>()
        repeat(500) { LassoHitTester.hits(stroke, cs, policy, out) }
        val id = Thread.currentThread().id
        val runs = 1000
        val before = sun!!.getThreadAllocatedBytes(id)
        repeat(runs) { LassoHitTester.hits(stroke, cs, policy, out) }
        val perRun = (sun.getThreadAllocatedBytes(id) - before) / runs
        println("PERF hit test allocation: $perRun bytes per evaluation (${out.size} hits)")
        // Only the result set's own entries are allowed; the geometry itself must not allocate.
        assertTrue("allocated $perRun bytes per evaluation", perRun < 4096)
    }

    @Test
    fun layoutOperationsAreInstantOnAFullPage() {
        val grid = Grid(6, 8)
        val items = (0 until 48).map { com.snapkode.nivolauncher.data.HomeItem("i$it", 0, it % 6, it / 6) }
        val ids = items.take(30).map { it.id }.toSet()
        repeat(50) { LayoutOps.compact(items, ids, grid, 0); LayoutOps.gather(items, ids, grid, 0) }
        val start = System.nanoTime()
        repeat(200) {
            LayoutOps.alignHorizontally(items, ids, grid, 0)
            LayoutOps.distribute(items, ids, grid, 0)
            LayoutOps.compact(items, ids, grid, 0)
            LayoutOps.gather(items, ids, grid, 0)
        }
        val perSetMs = (System.nanoTime() - start) / 1e6 / 200
        println("PERF layout ops: align+distribute+compact+gather of 30 icons on a 6x8 page = %.3f ms".format(perSetMs))
        assertTrue("took $perSetMs ms", perSetMs < 50.0)
    }
}
