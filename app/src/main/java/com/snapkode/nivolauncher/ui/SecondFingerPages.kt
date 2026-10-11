package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.snapkode.nivolauncher.lasso.PageTurn

/**
 * While [active] (an icon is held or being dragged with the first finger), a second finger swiping sideways turns
 * pages, and the held icon goes along. [onTurn] gets -1 (previous) or +1 (next) and says whether that page exists;
 * when it doesn't, the swipe keeps accumulating instead of being lost.
 *
 * It listens before the pager does and takes the second finger's touches, so the pager does not also scroll.
 */
fun Modifier.secondFingerPages(thresholdPx: Float, active: () -> Boolean, onTurn: (Int) -> Boolean): Modifier =
    pointerInput(thresholdPx) {
        val turn = PageTurn(thresholdPx)
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val other = event.changes.firstOrNull { it.pressed && it.id != first.id }
                if (other != null && active() && event.changes.any { it.id == first.id && it.pressed }) {
                    turn.move(other.id.value, other.position.x, other.position.y, onTurn)
                    other.consume()
                } else turn.release()
            } while (event.changes.any { it.pressed })
        }
    }
