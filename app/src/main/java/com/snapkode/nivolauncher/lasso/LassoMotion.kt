package com.snapkode.nivolauncher.lasso

/**
 * The lasso's timing targets, in one place. Springs come from the app's motion preset at the call site; these are
 * the durations used for the fades and the settle that accompany them, and the budget the design is held to.
 */
object LassoMotion {
    const val SELECTION_HIGHLIGHT_MS = 130
    const val TOOLBAR_MS = 200
    const val GROUP_LIFT_MS = 220
    const val GROUP_PLACE_MS = 300
    const val FOLDER_CREATE_MS = 320
    const val PATH_FADE_MS = 220

    /** Time before the page flips while a dragged group is held at an edge. */
    const val EDGE_DWELL_MS = 600

    /** A duration under reduced motion: effectively instant but never zero, so state still changes in order. */
    fun duration(ms: Int, reduced: Boolean): Int = if (reduced) maxOf(1, ms / 6) else ms

    /** Whether decorative loops (the marching dash on the outline) may run. */
    fun allowsAmbientMotion(reduced: Boolean): Boolean = !reduced

    /** Scale of a lifted icon. Reduced motion keeps the lift visible but does not scale. */
    fun liftScale(reduced: Boolean): Float = if (reduced) 1f else 1.08f

    /** How much larger a selected (not carried) icon is drawn. */
    const val SELECTED_SCALE = 1.05f
}
