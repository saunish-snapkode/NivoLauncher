package com.snapkode.nivolauncher.ui.pagelet.sky

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Density

/** Which rows of a [PriorityColumn] fit: always the most important first, never leaving a gap for one that doesn't. Pure. */
fun fitByPriority(heights: List<Int>, priorities: List<Int>, available: Int): Set<Int> {
    require(heights.size == priorities.size)
    val kept = HashSet<Int>()
    var used = 0
    for (i in heights.indices.sortedWith(compareBy({ priorities[it] }, { it }))) {
        if (used + heights[i] <= available) { kept += i; used += heights[i] }
    }
    return kept
}

private class PriorityData(val priority: Int) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = this@PriorityData
}

/** Gives a row of a [PriorityColumn] its importance: 0 is never dropped, larger numbers are dropped first. */
fun Modifier.priority(p: Int): Modifier = this.then(PriorityData(p))

/**
 * A column that shows as many of its rows as fit the height it is given, most important first, in their original order.
 * Rows that don't fit are not drawn at all, so a small or very large-text screen loses a detail instead of overlapping.
 * Each row carries its own spacing (its top padding), so dropping one never leaves a hole.
 */
@Composable
fun PriorityColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val prios = measurables.map { (it.parentData as? PriorityData)?.priority ?: 1 }
        val keep = fitByPriority(placeables.map { it.height }, prios, constraints.maxHeight)
        val w = constraints.maxWidth
        val h = placeables.withIndex().filter { it.index in keep }.sumOf { it.value.height }
        layout(w, h) {
            var y = 0
            placeables.forEachIndexed { i, p -> if (i in keep) { p.placeRelative(0, y); y += p.height } }
        }
    }
}
