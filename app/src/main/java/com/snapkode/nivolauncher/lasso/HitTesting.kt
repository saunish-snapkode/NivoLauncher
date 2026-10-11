package com.snapkode.nivolauncher.lasso

import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.isAnyWidgetId

/** An icon that can be lassoed: its id and the rectangle its tile occupies, in workspace-local pixels. */
class Candidate(@JvmField val id: String, @JvmField val tile: Box)

/**
 * How a stroke decides what it selected.
 *
 * * A stroke that looks like a loop selects every icon whose tile is at least [minCoverage] inside it.
 * * A stroke that encloses almost nothing (a line, a scribble) selects the icons it passes within [touchRadius] of.
 */
class SelectionPolicy(
    val minCoverage: Float = 0.4f,
    val samples: Int = 6,
    val touchRadius: Float = 10f,
    /** `closedness()` below this means "not a loop". A circle is about 0.08; a very thin loop is still above 0.02. */
    val loopThreshold: Float = 0.02f,
    /** Strokes shorter than this are taps, not selections. */
    val minLength: Float = 24f,
)

enum class SelectionMode { Add, Subtract }

object LassoCandidates {
    /**
     * The icons of [page] that can be lassoed, with their tiles taken from [tiles] (workspace-local). Membership comes
     * from the layout model, never from where a rectangle happens to be, so an icon on a neighbouring page can not be
     * picked even while the pager is mid-transition and its rectangle overlaps the viewport. Widgets and the dock are
     * never candidates, and icons that have not been laid out yet are skipped.
     */
    fun forPage(items: List<HomeItem>, page: Int, tiles: Map<String, Box>): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (item in items) {
            if (item.page != page || item.inDock || isAnyWidgetId(item.id)) continue
            val tile = tiles[item.id] ?: continue
            out.add(Candidate(item.id, tile))
        }
        return out
    }
}

object LassoHitTester {
    /** Whether the stroke should be treated as a closed loop under [policy]. */
    fun isLoop(path: LassoPath, policy: SelectionPolicy): Boolean =
        path.size >= 3 && path.length >= policy.minLength && path.closedness() >= policy.loopThreshold

    /**
     * Fills [out] with the ids of the [candidates] the stroke selects. Iterates by index and reuses [out], so it can
     * run on every accepted touch point without allocating.
     */
    fun hits(path: LassoPath, candidates: List<Candidate>, policy: SelectionPolicy, out: MutableSet<String>) {
        out.clear()
        if (path.size < 2 || path.length < policy.minLength) return
        val loop = isLoop(path, policy)
        for (i in candidates.indices) {
            val c = candidates[i]
            val hit = if (loop) {
                Polygon.coverage(path, c.tile, policy.samples) >= policy.minCoverage
            } else {
                Polygon.polylineTouches(path, c.tile, policy.touchRadius)
            }
            if (hit) out.add(c.id)
        }
    }

    /** The first candidate whose tile contains the point, or null. Used for taps. */
    fun hitAt(candidates: List<Candidate>, x: Float, y: Float): String? {
        for (i in candidates.indices) {
            if (candidates[i].tile.contains(x, y)) return candidates[i].id
        }
        return null
    }

    /**
     * Applies a stroke's [hits] to the current [selection] in the given [mode]. Returns a new set when something
     * changed, or the same instance when nothing did, so callers can skip work with an identity check.
     */
    fun apply(selection: Set<String>, hits: Set<String>, mode: SelectionMode): Set<String> = when (mode) {
        SelectionMode.Add -> if (selection.containsAll(hits)) selection else LinkedHashSet(selection).apply { addAll(hits) }
        SelectionMode.Subtract -> if (hits.none { it in selection }) selection else LinkedHashSet(selection).apply { removeAll(hits) }
    }

    /** Tap on an icon: toggles it in [SelectionMode.Add], removes it in [SelectionMode.Subtract]. */
    fun tap(selection: Set<String>, id: String, mode: SelectionMode): Set<String> = when {
        mode == SelectionMode.Subtract -> if (id in selection) LinkedHashSet(selection).apply { remove(id) } else selection
        id in selection -> LinkedHashSet(selection).apply { remove(id) }
        else -> LinkedHashSet(selection).apply { add(id) }
    }
}
