package com.snapkode.nivolauncher.lasso

/**
 * Undo history for batch edits. Each entry is the layout as it was *before* an action, so popping it restores
 * exactly that. Bounded, so a long session cannot grow it without limit.
 */
class LayoutHistory(private val limit: Int = 10) {
    data class Entry(val label: String, val snapshot: LayoutSnapshot)

    private val stack = ArrayDeque<Entry>()

    val size: Int get() = stack.size
    val isEmpty: Boolean get() = stack.isEmpty()

    fun push(label: String, snapshot: LayoutSnapshot) {
        stack.addLast(Entry(label, snapshot))
        while (stack.size > limit) stack.removeFirst()
    }

    fun peek(): Entry? = stack.lastOrNull()

    fun pop(): Entry? = stack.removeLastOrNull()

    fun clear() = stack.clear()
}

/**
 * The state of the home layout when lasso mode began. **Done** keeps whatever happened since; **Cancel** puts the
 * [baseline] back. Nothing is written to the model while a gesture is in flight, so this only matters for the batch
 * actions that were completed during the session.
 */
class LassoTransaction(val baseline: LayoutSnapshot) {
    fun isDirty(current: LayoutSnapshot): Boolean = current != baseline
}
