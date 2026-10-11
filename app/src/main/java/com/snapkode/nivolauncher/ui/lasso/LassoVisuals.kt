package com.snapkode.nivolauncher.ui.lasso

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.LauncherSettings
import com.snapkode.nivolauncher.lasso.Box as LBox
import com.snapkode.nivolauncher.lasso.LassoMotion
import com.snapkode.nivolauncher.lasso.LassoPath
import com.snapkode.nivolauncher.lasso.LassoState
import com.snapkode.nivolauncher.lasso.SelectionMode
import com.snapkode.nivolauncher.ui.AppIconView
import com.snapkode.nivolauncher.ui.FolderIconView
import com.snapkode.nivolauncher.ui.design.LocalMotion
import kotlin.math.roundToInt

/** The outline the finger draws: smoothed for the eye, faded out when the stroke ends. Redraws without recomposing. */
@Composable
internal fun LassoPathCanvas(session: LassoSession, origin: () -> Offset) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val drawing = session.state == LassoState.DRAWING
    val fade = remember { Animatable(1f) }          // 0 = fully visible, 1 = gone
    val dash = remember { Animatable(0f) }
    val smooth = remember { Path() }

    LaunchedEffect(drawing, reduced) {
        if (drawing) {
            fade.snapTo(0f)
            if (LassoMotion.allowsAmbientMotion(reduced)) {
                // A slow marching dash on the closing chord: the only ambient motion, and it runs only while drawing.
                while (true) dash.animateTo(dash.value + 40f, tween(1200, easing = LinearEasing))
            }
        } else {
            fade.animateTo(1f, tween(LassoMotion.duration(LassoMotion.PATH_FADE_MS, reduced), easing = FastOutSlowInEasing))
        }
    }

    val primary = scheme.primary
    val error = scheme.error
    Canvas(Modifier.fillMaxSize()) {
        session.pathTick                                   // reading it here is what invalidates the draw
        val p = session.path
        val visible = 1f - fade.value
        if (p.size < 2 || visible <= 0.01f) return@Canvas
        val tint = if (session.mode == SelectionMode.Subtract) error else primary
        val o = origin()
        translate(o.x, o.y) {
            buildSmooth(smooth, p)
            drawPath(smooth, tint.copy(alpha = 0.10f * visible), style = Fill)
            drawPath(smooth, tint.copy(alpha = 0.16f * visible), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(smooth, tint.copy(alpha = 0.92f * visible), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // The logical closing edge from the finger back to where the stroke began.
            val ex = p.xs[p.size - 1]; val ey = p.ys[p.size - 1]
            drawLine(
                tint.copy(alpha = 0.5f * visible), Offset(ex, ey), Offset(p.xs[0], p.ys[0]), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()), dash.value),
            )
            drawCircle(tint.copy(alpha = 0.20f * visible), radius = 11.dp.toPx(), center = Offset(ex, ey))
            drawCircle(tint.copy(alpha = visible), radius = 4.dp.toPx(), center = Offset(p.xs[0], p.ys[0]))
            drawCircle(tint.copy(alpha = visible), radius = 4.5.dp.toPx(), center = Offset(ex, ey))
        }
    }
}

/** Rebuilds [out] as a curve through the midpoints of the raw stroke. Reuses the same Path object every frame. */
private fun buildSmooth(out: Path, p: LassoPath) {
    out.rewind()
    val n = p.size
    out.moveTo(p.xs[0], p.ys[0])
    if (n == 2) { out.lineTo(p.xs[1], p.ys[1]); return }
    for (i in 1 until n - 1) {
        out.quadraticTo(p.xs[i], p.ys[i], (p.xs[i] + p.xs[i + 1]) / 2f, (p.ys[i] + p.ys[i + 1]) / 2f)
    }
    out.lineTo(p.xs[n - 1], p.ys[n - 1])
}

/**
 * One selected icon's highlight: a soft rounded fill, a thin outline and a small check badge. It grows in over
 * about 130 ms, and follows its icon if the icon moves.
 */
@Composable
internal fun SelectionRing(session: LassoSession, id: String, removing: Boolean, preview: Boolean) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(LassoMotion.duration(LassoMotion.SELECTION_HIGHLIGHT_MS, reduced), easing = FastOutSlowInEasing))
    }
    val tint = if (removing) scheme.error else scheme.primary
    val onTint = if (removing) scheme.onError else scheme.onPrimary
    val density = LocalDensity.current
    val pad = with(density) { 5.dp.roundToPx() }

    Box(
        Modifier
            .offset {
                session.layoutTick
                val b = session.tileRoot(id)
                if (b == null) IntOffset.Zero else IntOffset(b.left.roundToInt() - pad, b.top.roundToInt() - pad)
            }
            .layout { measurable, _ ->
                session.layoutTick
                val b = session.tileRoot(id)
                if (b == null) {
                    layout(0, 0) {}
                } else {
                    val w = b.width.roundToInt() + 2 * pad
                    val h = b.height.roundToInt() + 2 * pad
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
            }
            .graphicsLayer {
                val a = appear.value
                alpha = if (preview) 0.75f * a else a
                val s = 0.94f + 0.06f * a
                scaleX = s; scaleY = s
            }
            .drawBehind {
                val corner = CornerRadius(18.dp.toPx())
                drawRoundRect(tint.copy(alpha = if (preview) 0.10f else 0.14f), cornerRadius = corner)
                drawRoundRect(
                    tint.copy(alpha = if (preview) 0.55f else 0.85f), cornerRadius = corner,
                    style = Stroke(
                        1.5.dp.toPx(),
                        pathEffect = if (preview) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                    ),
                )
                if (!preview) {
                    // Check badge in the top-right corner.
                    val r = 8.dp.toPx()
                    val c = Offset(size.width - r - 2.dp.toPx(), r + 2.dp.toPx())
                    drawCircle(tint, r, c)
                    val w = 1.8.dp.toPx()
                    drawLine(onTint, Offset(c.x - 3.6.dp.toPx(), c.y + 0.2.dp.toPx()), Offset(c.x - 1.0.dp.toPx(), c.y + 2.8.dp.toPx()), w, StrokeCap.Round)
                    drawLine(onTint, Offset(c.x - 1.0.dp.toPx(), c.y + 2.8.dp.toPx()), Offset(c.x + 3.8.dp.toPx(), c.y - 2.6.dp.toPx()), w, StrokeCap.Round)
                }
            },
    )
}

/** Outlines of where the dragged group would land: calm accent when valid, error red when not. */
@Composable
internal fun DropPreview(session: LassoSession, cellW: Int, cellH: Int, origin: () -> Offset) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val primary = scheme.primary
    val error = scheme.error
    Canvas(Modifier.fillMaxSize()) {
        val plan = session.dropPlan ?: return@Canvas
        // No page under the group yet (between pages): show nothing rather than a red outline at the old cells.
        if (session.state != LassoState.DRAGGING || cellW <= 0 || plan.page < 0) return@Canvas
        val tint = if (plan.valid) primary else error
        val o = origin()
        val inset = 6.dp.toPx()
        val corner = CornerRadius(16.dp.toPx())
        for (pl in plan.placements) {
            val x = o.x + pl.col * cellW + inset
            val y = o.y + pl.row * cellH + inset
            val size = Size(cellW - 2 * inset, cellH - 2 * inset)
            drawRoundRect(tint.copy(alpha = 0.10f), Offset(x, y), size, corner)
            drawRoundRect(tint.copy(alpha = 0.60f), Offset(x, y), size, corner, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** What each carried icon looks like while the group is in the air. */
@Composable
internal fun GroupGhosts(
    session: LassoSession,
    settle: Animatable<Float, *>,
    fade: Animatable<Float, *>,
    settling: () -> Boolean,
    releaseDelta: () -> Offset,
    targets: () -> Map<String, Offset>,
    origin: () -> Offset,
    appsById: Map<String, AppInfo>,
    folders: Map<String, HomeFolder>,
    settings: LauncherSettings,
) {
    val reduced = LocalMotion.current.reduced
    val density = LocalDensity.current
    // A selected icon already sits at its selected scale; the ghost starts and ends there, so lift and drop never dip or pop.
    val rest = if (reduced) 1f else LassoMotion.SELECTED_SCALE
    val lift = remember { Animatable(rest) }
    val dragging = session.state == LassoState.DRAGGING
    LaunchedEffect(dragging, reduced) {
        lift.animateTo(
            if (dragging) LassoMotion.liftScale(reduced) else rest,
            tween(LassoMotion.duration(LassoMotion.GROUP_LIFT_MS, reduced), easing = FastOutSlowInEasing),
        )
    }
    for (id in session.dragIds) {
        val foot = session.dragStartTiles[id] ?: continue
        Box(
            Modifier
                .offset {
                    val o = origin()
                    IntOffset((o.x + foot.left).roundToInt(), (o.y + foot.top).roundToInt())
                }
                .size(with(density) { foot.width.toDp() }, with(density) { foot.height.toDp() })
                .graphicsLayer {
                    // The finger's movement is applied here, at draw time: no layout pass per touch event.
                    val d = if (settling()) {
                        val t = targets()[id] ?: Offset.Zero
                        val p = settle.value
                        val r = releaseDelta()
                        Offset(r.x * (1f - p) + t.x * p, r.y * (1f - p) + t.y * p)
                    } else session.dragDelta
                    translationX = d.x; translationY = d.y
                    val f = fade.value
                    val s = lift.value * (1f - 0.55f * f)
                    scaleX = s; scaleY = s
                    alpha = 1f - 0.95f * f
                },
            contentAlignment = Alignment.Center,
        ) {
            // The selection ring travels with its icon instead of staying behind on the empty cell.
            val ringTint = androidx.compose.material3.MaterialTheme.colorScheme.primary
            Box(
                Modifier.matchParentSize().drawBehind {
                    val corner = CornerRadius(18.dp.toPx())
                    drawRoundRect(ringTint.copy(alpha = 0.14f), cornerRadius = corner)
                    drawRoundRect(ringTint.copy(alpha = 0.85f), cornerRadius = corner, style = Stroke(1.5.dp.toPx()))
                },
            )
            val folder = folders[id]
            val app = appsById[id]
            when {
                folder != null -> FolderIconView(folder, appsById, settings.iconScale, settings.showLabels)
                app != null -> AppIconView(app, settings.iconScale, settings.showLabels, onWallpaper = true)
            }
        }
    }
}

/**
 * One invisible node per icon so a screen reader can find it and toggle it. Touch is not involved: the overlay
 * handles every pointer, these only describe the icons beneath it.
 */
@Composable
internal fun IconAccessibilityNode(session: LassoSession, id: String, label: String, page: Int) {
    val selected = id in session.selected
    Box(
        Modifier
            .offset {
                session.layoutTick
                val b = session.tileRoot(id)
                if (b == null) IntOffset.Zero else IntOffset(b.left.roundToInt(), b.top.roundToInt())
            }
            .layout { measurable, _ ->
                session.layoutTick
                val b = session.tileRoot(id)
                if (b == null) layout(0, 0) {} else {
                    val w = b.width.roundToInt().coerceAtLeast(1)
                    val h = b.height.roundToInt().coerceAtLeast(1)
                    val p = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { p.place(0, 0) }
                }
            }
            .semantics {
                role = Role.Checkbox
                contentDescription = label
                stateDescription = if (selected) "Selected" else "Not selected"
                onClick(label = if (selected) "Deselect" else "Select") { session.tap(id, page); true }
            },
    )
}

/** Whether a screen reader or switch access is exploring by touch, so the toolbar is used instead of gestures. */
@Composable
internal fun rememberTouchExploration(): State<Boolean> {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    val state = remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { state.value = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return state
}

/** An icon's tile: the square the icon art occupies, at the top-centre of its on-screen footprint. */
internal fun iconTile(foot: LBox, iconPx: Float): LBox {
    val side = minOf(iconPx, foot.width, foot.height)
    val cx = foot.centerX
    return LBox(cx - side / 2f, foot.top, cx + side / 2f, foot.top + side)
}

