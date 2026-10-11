package com.snapkode.nivolauncher

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.LauncherSettings
import com.snapkode.nivolauncher.lasso.Box as LBox
import com.snapkode.nivolauncher.lasso.LassoState
import com.snapkode.nivolauncher.lasso.LayoutSnapshot
import com.snapkode.nivolauncher.ui.lasso.LassoLayer
import com.snapkode.nivolauncher.ui.lasso.LassoSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives real two-finger touches through the real LassoLayer: the first finger holds the selection, the second one
 * swipes. The page the fake pager is on must change, and the selection must be carried (state DRAGGING).
 */
@RunWith(AndroidJUnit4::class)
class LassoTwoFingerTest {
    @get:Rule val rule = createComposeRule()

    private val opened = mutableListOf<Int>()
    private var current by mutableIntStateOf(0)
    private val tileA = LBox(100f, 300f, 300f, 500f)

    private fun host(session: LassoSession, vm: LauncherViewModel) {
        val items = listOf(HomeItem("a", 0, 0, 1), HomeItem("b", 0, 2, 1))
        session.registerTile("a", tileA)
        session.registerTile("b", LBox(500f, 300f, 700f, 500f))
        session.armWith("a", 0, LayoutSnapshot(items, emptyMap(), 3))
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                LassoLayer(
                    viewModel = vm, session = session, pagerRect = Rect(0f, 0f, 1080f, 1700f),
                    workspaceItems = items, appsById = emptyMap(), folders = emptyMap(),
                    settings = LauncherSettings(),
                    currentPage = { current },
                    workspacePageCount = 3,
                    openPage = { opened += it; current = it },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun run(secondDown: Offset, secondEnd: Offset, firstDown: Offset = Offset(200f, 400f)) {
        val vm = LauncherViewModel(ApplicationProvider.getApplicationContext<Application>())
        val session = LassoSession()
        host(session, vm)
        rule.onRoot().performTouchInput {
            down(0, firstDown)
            advanceEventTime(150)
            down(1, secondDown)
            advanceEventTime(30)
            val steps = 8
            for (i in 1..steps) {
                moveTo(1, Offset(secondDown.x + (secondEnd.x - secondDown.x) * i / steps, secondDown.y))
                advanceEventTime(16)
            }
        }
        rule.waitForIdle()
        assertTrue("the first finger's selection should be lifted", session.state == LassoState.DRAGGING)
        assertEquals("the swipe should turn to the next page first", 1, opened.firstOrNull())
        rule.onRoot().performTouchInput { up(1); up(0) }
        rule.waitForIdle()
    }

    @Test fun holdingSelectedIcon_secondFingerSwipesLeft_turnsPage() = run(Offset(900f, 900f), Offset(300f, 900f))

    @Test fun secondFingerOnToolbarArea_stillTurnsPage() = run(Offset(900f, 1650f), Offset(300f, 1650f))

    @Test fun holdingEmptySpaceWithSelection_secondFingerTurnsPage() =
        run(Offset(900f, 900f), Offset(300f, 900f), firstDown = Offset(800f, 1200f))
}
