package com.snapkode.nivolauncher.lasso

/** The five states of lasso mode. */
enum class LassoState { IDLE, ARMED, DRAWING, SELECTED, DRAGGING }

/**
 * The lasso state machine. Each event is a method returning whether it was accepted; an event that does not apply
 * in the current state is ignored and the state is left untouched. That makes every sequence of events
 * deterministic, and any gesture can be interrupted by a cancel at any point.
 *
 * ```
 * IDLE -arm-> ARMED -beginStroke-> DRAWING -endStroke(hits>0)-> SELECTED -beginDrag-> DRAGGING
 *   ^           ^                     |  endStroke(0 hits) -> back where it came from     |
 *   |           |                     +-- cancelStroke / geometryChanged -> back          |
 *   |           +---------- selection becomes empty (from SELECTED)                       |
 *   |                                                       endDrag / cancelDrag -> SELECTED
 *   +------------------------- exit() from any state ----------------------------------+
 * ```
 */
class LassoMachine {
    var state: LassoState = LassoState.IDLE
        private set

    /** Where a stroke returns to if it is cancelled or selects nothing. */
    private var resume: LassoState = LassoState.ARMED

    /** IDLE -> ARMED, or straight to SELECTED when mode is entered with something already selected. */
    fun arm(withSelection: Boolean = false): Boolean {
        if (state != LassoState.IDLE) return false
        state = if (withSelection) LassoState.SELECTED else LassoState.ARMED
        return true
    }

    /** ARMED or SELECTED -> DRAWING. A stroke may start while a selection exists, to add to or subtract from it. */
    fun beginStroke(): Boolean {
        if (state != LassoState.ARMED && state != LassoState.SELECTED) return false
        resume = state
        state = LassoState.DRAWING
        return true
    }

    /** DRAWING -> SELECTED if anything is selected afterwards, otherwise back to ARMED. */
    fun endStroke(selectionSize: Int): Boolean {
        if (state != LassoState.DRAWING) return false
        state = if (selectionSize > 0) LassoState.SELECTED else LassoState.ARMED
        return true
    }

    /** DRAWING -> where the stroke began. The selection is untouched. */
    fun cancelStroke(): Boolean {
        if (state != LassoState.DRAWING) return false
        state = resume
        return true
    }

    /** SELECTED -> DRAGGING. */
    fun beginDrag(): Boolean {
        if (state != LassoState.SELECTED) return false
        state = LassoState.DRAGGING
        return true
    }

    /** DRAGGING -> SELECTED, whether the drop was committed or cancelled. */
    fun endDrag(): Boolean {
        if (state != LassoState.DRAGGING) return false
        state = LassoState.SELECTED
        return true
    }

    /**
     * The selection changed outside a stroke (a tap, select all, clear). Moves ARMED <-> SELECTED to match, and does
     * nothing while drawing or dragging.
     */
    fun selectionChanged(size: Int): Boolean {
        val target = when (state) {
            LassoState.ARMED -> if (size > 0) LassoState.SELECTED else return false
            LassoState.SELECTED -> if (size == 0) LassoState.ARMED else return false
            else -> return false
        }
        state = target
        return true
    }

    /** The workspace was resized or rotated, so in-flight geometry is meaningless: abandon the stroke or drag. */
    fun geometryChanged(): Boolean = when (state) {
        LassoState.DRAWING -> cancelStroke()
        LassoState.DRAGGING -> endDrag()
        else -> false
    }

    /** Any state -> IDLE (Done, Cancel or Back). */
    fun exit(): Boolean {
        if (state == LassoState.IDLE) return false
        state = LassoState.IDLE
        resume = LassoState.ARMED
        return true
    }
}
