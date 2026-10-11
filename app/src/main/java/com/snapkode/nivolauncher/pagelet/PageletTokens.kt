package com.snapkode.nivolauncher.pagelet

/** Durations and easings shared by Pagelets (milliseconds). */
object PageletTokens {
    const val ENTER_RISE_MS = 900
    const val ARC_TRAVEL_MS = 2200
    const val ARC_DRAW_MS = 1600
    const val SEGMENT_START_MS = 600
    const val SEGMENT_STAGGER_MS = 90
    const val COUNT_UP_MS = 1400
    const val GOAL_BAR_MS = 1200
    const val GOAL_BAR_DELAY_MS = 400
    const val SCENE_CROSSFADE_MS = 1400
    const val REDUCED_CROSSFADE_MS = 150

    /** cubic-bezier(.05,.7,.1,1): Material 3 emphasized decelerate. */
    val EmphasizedDecelerate = floatArrayOf(0.05f, 0.7f, 0.1f, 1f)

    /** cubic-bezier(.2,0,0,1): Material 3 emphasized. */
    val Emphasized = floatArrayOf(0.2f, 0f, 0f, 1f)

    const val ARC_DESIGN_SIDE_MARGIN_DP = 30
}
