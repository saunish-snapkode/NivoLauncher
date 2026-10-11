package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.lasso.LassoState.ARMED
import com.snapkode.nivolauncher.lasso.LassoState.DRAGGING
import com.snapkode.nivolauncher.lasso.LassoState.DRAWING
import com.snapkode.nivolauncher.lasso.LassoState.IDLE
import com.snapkode.nivolauncher.lasso.LassoState.SELECTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineTest {
    @Test
    fun theHappyPath() {
        val m = LassoMachine()
        assertEquals(IDLE, m.state)
        assertTrue(m.arm()); assertEquals(ARMED, m.state)
        assertTrue(m.beginStroke()); assertEquals(DRAWING, m.state)
        assertTrue(m.endStroke(3)); assertEquals(SELECTED, m.state)
        assertTrue(m.beginDrag()); assertEquals(DRAGGING, m.state)
        assertTrue(m.endDrag()); assertEquals(SELECTED, m.state)
        assertTrue(m.exit()); assertEquals(IDLE, m.state)
    }

    @Test
    fun aStrokeThatSelectsNothingGoesBackToArmed() {
        val m = LassoMachine().also { it.arm(); it.beginStroke() }
        assertTrue(m.endStroke(0))
        assertEquals(ARMED, m.state)
    }

    @Test
    fun aCancelledStrokeReturnsToWhereItBegan() {
        val fromArmed = LassoMachine().also { it.arm(); it.beginStroke() }
        assertTrue(fromArmed.cancelStroke()); assertEquals(ARMED, fromArmed.state)

        val fromSelected = LassoMachine().also { it.arm(withSelection = true); it.beginStroke() }
        assertEquals(DRAWING, fromSelected.state)
        assertTrue(fromSelected.cancelStroke()); assertEquals(SELECTED, fromSelected.state)
    }

    @Test
    fun armingWithASelectionStartsSelected() {
        val m = LassoMachine()
        assertTrue(m.arm(withSelection = true))
        assertEquals(SELECTED, m.state)
    }

    @Test
    fun aSecondStrokeCanExtendTheSelection() {
        val m = LassoMachine().also { it.arm(); it.beginStroke(); it.endStroke(2) }
        assertTrue(m.beginStroke()); assertEquals(DRAWING, m.state)
        assertTrue(m.endStroke(5)); assertEquals(SELECTED, m.state)
    }

    @Test
    fun selectionChangesOutsideAStrokeMoveBetweenArmedAndSelected() {
        val m = LassoMachine().also { it.arm() }
        assertTrue(m.selectionChanged(1)); assertEquals(SELECTED, m.state)
        assertFalse(m.selectionChanged(2)); assertEquals(SELECTED, m.state)
        assertTrue(m.selectionChanged(0)); assertEquals(ARMED, m.state)
        assertFalse(m.selectionChanged(0)); assertEquals(ARMED, m.state)
    }

    @Test
    fun selectionChangesAreIgnoredWhileBusy() {
        val drawing = LassoMachine().also { it.arm(); it.beginStroke() }
        assertFalse(drawing.selectionChanged(4)); assertEquals(DRAWING, drawing.state)
        val dragging = LassoMachine().also { it.arm(true); it.beginDrag() }
        assertFalse(dragging.selectionChanged(0)); assertEquals(DRAGGING, dragging.state)
    }

    @Test
    fun exitWorksFromEveryState() {
        for (setup in listOf<(LassoMachine) -> Unit>(
            { it.arm() },
            { it.arm(); it.beginStroke() },
            { it.arm(true) },
            { it.arm(true); it.beginDrag() },
        )) {
            val m = LassoMachine().also(setup)
            assertTrue(m.exit()); assertEquals(IDLE, m.state)
            assertFalse("a second exit is ignored", m.exit())
        }
    }

    @Test
    fun geometryChangesAbandonInFlightGestures() {
        val drawing = LassoMachine().also { it.arm(); it.beginStroke() }
        assertTrue(drawing.geometryChanged()); assertEquals(ARMED, drawing.state)

        val dragging = LassoMachine().also { it.arm(true); it.beginDrag() }
        assertTrue(dragging.geometryChanged()); assertEquals(SELECTED, dragging.state)

        val idle = LassoMachine()
        assertFalse(idle.geometryChanged())
        val selected = LassoMachine().also { it.arm(true) }
        assertFalse(selected.geometryChanged()); assertEquals(SELECTED, selected.state)
    }

    /** Every (state, event) pair: events that do not apply are rejected and change nothing. */
    @Test
    fun invalidEventsAreIgnoredAndNeverChangeTheState() {
        val setups = mapOf<LassoState, (LassoMachine) -> Unit>(
            IDLE to { },
            ARMED to { it.arm() },
            DRAWING to { it.arm(); it.beginStroke() },
            SELECTED to { it.arm(true) },
            DRAGGING to { it.arm(true); it.beginDrag() },
        )
        val events = mapOf<String, (LassoMachine) -> Boolean>(
            "arm" to { it.arm() },
            "beginStroke" to { it.beginStroke() },
            "endStroke" to { it.endStroke(1) },
            "cancelStroke" to { it.cancelStroke() },
            "beginDrag" to { it.beginDrag() },
            "endDrag" to { it.endDrag() },
        )
        // state -> events that ARE legal there
        val legal = mapOf(
            IDLE to setOf("arm"),
            ARMED to setOf("beginStroke"),
            DRAWING to setOf("endStroke", "cancelStroke"),
            SELECTED to setOf("beginStroke", "beginDrag"),
            DRAGGING to setOf("endDrag"),
        )
        for ((state, setup) in setups) for ((name, event) in events) {
            val m = LassoMachine().also(setup)
            assertEquals(state, m.state)
            val accepted = event(m)
            val expected = name in legal.getValue(state)
            assertEquals("$name in $state", expected, accepted)
            if (!accepted) assertEquals("$name must not move $state", state, m.state)
        }
    }

    @Test
    fun theSameEventSequenceAlwaysGivesTheSameResult() {
        fun run(): List<LassoState> {
            val m = LassoMachine()
            val seen = ArrayList<LassoState>()
            m.arm(); seen += m.state
            m.beginStroke(); seen += m.state
            m.cancelStroke(); seen += m.state
            m.beginStroke(); m.endStroke(2); seen += m.state
            m.beginDrag(); seen += m.state
            m.geometryChanged(); seen += m.state
            m.exit(); seen += m.state
            return seen
        }
        assertEquals(run(), run())
        assertEquals(listOf(ARMED, DRAWING, ARMED, SELECTED, DRAGGING, SELECTED, IDLE), run())
    }
}
