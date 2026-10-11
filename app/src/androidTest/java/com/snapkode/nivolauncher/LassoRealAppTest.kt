package com.snapkode.nivolauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.lasso.LassoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The same two-finger gesture, but through the whole real launcher (real pager, toolbar, HomeScreen handlers). */
@RunWith(AndroidJUnit4::class)
class LassoRealAppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /**
     * A visible Pagelet runs a continuous frame loop, which Compose's test idler treats as "busy forever". These tests are
     * about icons, so start from a home with no Pagelets (PageletTest covers them).
     */
    @org.junit.Before fun clearPagelets() {
        val vm = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]
        rule.runOnUiThread {
            vm.items.value.filter { com.snapkode.nivolauncher.pagelet.isPageletId(it.id) }.forEach { vm.removePagelet(it.id) }
            vm.clearPageletUndo()
        }
        rule.waitForIdle()
    }

    @Test fun holdOneIcon_secondFingerSwipe_turnsRealPage() {
        val vm = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]
        rule.runOnUiThread { vm.finishOnboarding() }
        rule.waitForIdle()
        val ids = vm.apps.value.take(2).map { it.id }
        rule.runOnUiThread { ids.forEach { vm.addToHome(it) } }
        rule.waitUntil(10_000) { vm.items.value.any { it.id == ids[0] } }
        rule.waitForIdle()
        val item = vm.items.value.first { it.id == ids[0] }
        rule.runOnUiThread { vm.lassoStartWith(ids[0]) }
        rule.waitUntil(10_000) { vm.lasso.tileRoot(ids[0]) != null }
        rule.waitForIdle()
        val tile = vm.lasso.tileRoot(ids[0])!!
        val first = Offset(tile.centerX, tile.centerY)
        val shown = vm.lasso.page
        val rec = FrameRecorder(rule.activity, "lasso hold+second-finger page turn")
        rule.runOnUiThread { rec.start() }
        rule.onRoot().performTouchInput { down(0, first) }
        Thread.sleep(250)
        rule.onRoot().performTouchInput { down(1, Offset(width * 0.85f, height * 0.5f)) }
        rule.waitForIdle()
        // A finger moves at frame rate, not instantly: let the UI recompose between samples.
        for (i in 1..10) {
            rule.onRoot().performTouchInput { moveTo(1, Offset(width * (0.85f - 0.045f * i), height * 0.5f)) }
            rule.waitForIdle()
            Thread.sleep(16)
        }
        Thread.sleep(700)
        rule.runOnUiThread { rec.stop() }
        assertEquals("selection should be lifted", LassoState.DRAGGING, vm.lasso.state)
        // The group's drop target must now be on the page after the one it started from.
        rule.waitUntil(5_000) { vm.lasso.dropPlan?.page == item.page + 1 }
        assertNotNull(vm.lasso.dropPlan)
        assertEquals(item.page + 1, vm.lasso.dropPlan!!.page)
        rule.onRoot().performTouchInput { up(1); up(0) }
        // Dropping commits the move: the held icon now lives on the next page.
        rule.waitUntil(8_000) { vm.items.value.firstOrNull { it.id == ids[0] }?.page == item.page + 1 }
        assertEquals(item.page + 1, vm.items.value.first { it.id == ids[0] }.page)
        rule.waitForIdle()
    }

    /** No lasso mode at all: long-press an icon, drag it a little, then turn the page with a second finger. */
    @Test fun normalDrag_secondFingerSwipe_carriesIconToNextPage() {
        val vm = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]
        rule.runOnUiThread { vm.finishOnboarding() }
        rule.waitForIdle()
        // Column 1, not 0: an icon held in the left-edge zone flips pages by itself after a moment (that is a feature).
        rule.waitUntil(10_000) { vm.items.value.any { !it.inDock && it.col == 1 } }
        val id = vm.items.value.first { !it.inDock && it.col == 1 && !com.snapkode.nivolauncher.pagelet.isPageletId(it.id) }.id
        rule.waitUntil(10_000) { vm.lasso.tileRoot(id) != null }
        rule.waitForIdle()
        val item = vm.items.value.first { it.id == id }
        // Bring the icon's page on screen (the launcher opens on its default page).
        var tries = 0
        while (tries++ < 5) {
            val b = vm.lasso.tileRoot(id)
            if (b != null && b.centerX > 20f && b.centerX < 1060f) break
            rule.onAllNodes(isRoot())[0].performTouchInput { swipeLeft() }
            rule.waitForIdle(); Thread.sleep(600)
        }
        val tile = vm.lasso.tileRoot(id)!!
        val at = Offset(tile.centerX, tile.centerY)
        val rec = FrameRecorder(rule.activity, "normal drag + second-finger page turn")
        rule.onAllNodes(isRoot())[0].performTouchInput { down(0, at) }
        rule.runOnUiThread { rec.start() }
        rule.mainClock.advanceTimeBy(800)      // long press
        rule.waitForIdle()
        for (i in 1..6) {                      // lift it
            rule.onAllNodes(isRoot())[0].performTouchInput { moveTo(0, Offset(at.x + 6f * i, at.y + 6f * i)) }
            rule.waitForIdle(); Thread.sleep(16)
        }
        rule.onAllNodes(isRoot())[0].performTouchInput { down(1, Offset(width * 0.85f, height * 0.5f)) }
        rule.waitForIdle()
        for (i in 1..10) {
            rule.onAllNodes(isRoot())[0].performTouchInput { moveTo(1, Offset(width * (0.85f - 0.045f * i), height * 0.5f)) }
            rule.waitForIdle(); Thread.sleep(16)
        }
        rule.mainClock.advanceTimeBy(1500)    // let the page settle, in the test's own time
        rule.waitForIdle()
        rule.runOnUiThread { rec.stop() }
        rule.onAllNodes(isRoot())[0].performTouchInput { up(1); up(0) }
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
        val after = vm.items.value.first { it.id == id }
        assertEquals("was $item, now $after, all=${vm.items.value.map { it.page }}", item.page + 1, after.page)
    }

    /** The icon is only held, never dragged: the page turn must pick it up and carry it. */
    @Test fun heldButNotLifted_secondFingerSwipe_stillCarriesIcon() {
        val vm = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]
        rule.runOnUiThread { vm.finishOnboarding() }
        rule.waitForIdle()
        // Column 1, not 0: an icon held in the left-edge zone flips pages by itself after a moment (that is a feature).
        rule.waitUntil(10_000) { vm.items.value.any { !it.inDock && it.col == 1 } }
        val id = vm.items.value.first { !it.inDock && it.col == 1 && !com.snapkode.nivolauncher.pagelet.isPageletId(it.id) }.id
        rule.waitUntil(10_000) { vm.lasso.tileRoot(id) != null }
        rule.waitForIdle()
        val item = vm.items.value.first { it.id == id }
        // Bring the icon's page on screen (the launcher opens on its default page).
        var tries = 0
        while (tries++ < 5) {
            val b = vm.lasso.tileRoot(id)
            if (b != null && b.centerX > 20f && b.centerX < 1060f) break
            rule.onAllNodes(isRoot())[0].performTouchInput { swipeLeft() }
            rule.waitForIdle(); Thread.sleep(600)
        }
        val tile = vm.lasso.tileRoot(id)!!
        val at = Offset(tile.centerX, tile.centerY)
        val rec = FrameRecorder(rule.activity, "held (not lifted) + second-finger page turn")
        rule.onAllNodes(isRoot())[0].performTouchInput { down(0, at) }
        rule.runOnUiThread { rec.start() }
        rule.mainClock.advanceTimeBy(800)      // long press
        rule.waitForIdle()
        rule.onAllNodes(isRoot())[0].performTouchInput { down(1, Offset(width * 0.85f, height * 0.5f)) }
        rule.waitForIdle()
        for (i in 1..10) {
            rule.onAllNodes(isRoot())[0].performTouchInput { moveTo(1, Offset(width * (0.85f - 0.045f * i), height * 0.5f)) }
            rule.waitForIdle(); Thread.sleep(16)
        }
        rule.mainClock.advanceTimeBy(1500)    // let the page settle, in the test's own time
        rule.waitForIdle()
        rule.runOnUiThread { rec.stop() }
        rule.onAllNodes(isRoot())[0].performTouchInput { up(1); up(0) }
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
        val after = vm.items.value.first { it.id == id }
        assertEquals("was $item, now $after, all=${vm.items.value.map { it.page }}", item.page + 1, after.page)
    }
}
