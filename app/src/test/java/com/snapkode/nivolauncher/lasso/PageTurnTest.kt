package com.snapkode.nivolauncher.lasso

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnTest {
    @Test fun firstSampleOnlyArms() = assertEquals(0, PageTurn(100f).move(1, 500f, 800f))

    @Test fun swipeLeftTurnsToNextPage() {
        val t = PageTurn(100f); t.move(1, 800f, 800f)
        assertEquals(0, t.move(1, 750f, 800f))
        assertEquals(1, t.move(1, 650f, 800f))
    }

    @Test fun swipeRightTurnsToPreviousPage() {
        val t = PageTurn(100f); t.move(1, 200f, 800f)
        assertEquals(-1, t.move(1, 320f, 805f))
    }

    @Test fun mostlyVerticalDragDoesNotTurn() {
        val t = PageTurn(100f); t.move(1, 500f, 400f)
        assertEquals(0, t.move(1, 620f, 700f))
    }

    @Test fun rearmingModeTurnsOnePagePerThreshold() {
        val t = PageTurn(100f, oncePerTouch = false); t.move(1, 900f, 800f)
        assertEquals(1, t.move(1, 700f, 800f))
        assertEquals(0, t.move(1, 650f, 800f))
        assertEquals(1, t.move(1, 560f, 800f))
    }

    @Test fun anotherFingerStartsFresh() {
        val t = PageTurn(100f); t.move(1, 900f, 800f)
        t.release()
        assertEquals(0, t.move(2, 300f, 800f))
        assertEquals(-1, t.move(2, 420f, 800f))
    }

    @Test fun blockedTurnIsRetriedNotDropped() {
        val t = PageTurn(100f); t.move(1, 900f, 800f)
        assertEquals(0, t.move(1, 700f, 800f) { false })
        assertEquals(1, t.move(1, 690f, 800f) { true })
    }

    @Test fun byDefaultOneTouchTurnsOnlyOnePage() {
        val t = PageTurn(100f); t.move(1, 900f, 800f)
        assertEquals(1, t.move(1, 700f, 800f))
        assertEquals(0, t.move(1, 400f, 800f))
        t.release()
        t.move(1, 900f, 800f)
        assertEquals(1, t.move(1, 700f, 800f))
    }
}
