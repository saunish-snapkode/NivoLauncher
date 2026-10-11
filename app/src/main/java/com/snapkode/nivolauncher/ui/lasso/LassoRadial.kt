package com.snapkode.nivolauncher.ui.lasso

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.lasso.LassoMotion
import com.snapkode.nivolauncher.lasso.RadialLayout
import com.snapkode.nivolauncher.lasso.RadialPlacement
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.nivoCard
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Sizes the radial menu is laid out with, shared with the code that decides whether it fits. */
internal object RadialMetrics {
    val radius = 108.dp
    val button = 48.dp
    val labelHeight = 20.dp
    val buttonCount = 6
}

/**
 * The optional radial action menu: Move, Arrange, Folder, Gather, Remove, More, fanned in a half ring beside the
 * selection (never over it). Buttons spring out from the ring's pivot a few milliseconds apart; with reduced motion
 * they simply fade in.
 */
@Composable
internal fun LassoRadial(
    session: LassoSession,
    placement: RadialPlacement,
    selectionCount: Int,
    pageCount: Int,
    currentPageNumber: Int?,
    actions: LassoActions,
) {
    val density = LocalDensity.current
    val reduced = LocalMotion.current.reduced
    val radiusPx = with(density) { RadialMetrics.radius.toPx() }
    val centres = RadialLayout.buttonCenters(placement, RadialMetrics.buttonCount, radiusPx)

    var arrangeOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        centres.forEachIndexed { i, (cx, cy) ->
            val spec = when (i) {
                0 -> RadialSpec("Move", Icons.Outlined.OpenWith, session.moveAnywhere) { actions.toggleMove() }
                1 -> RadialSpec("Arrange", Icons.Outlined.AutoAwesomeMosaic) { arrangeOpen = true }
                2 -> RadialSpec("Folder", Icons.Outlined.CreateNewFolder, enabled = selectionCount >= 2) { actions.createFolder() }
                3 -> RadialSpec("Gather", Icons.Outlined.FilterCenterFocus) { actions.gather() }
                4 -> RadialSpec("Remove", Icons.Outlined.Delete, destructive = true) { actions.remove() }
                else -> RadialSpec("More", Icons.Outlined.MoreHoriz) { moreOpen = true }
            }
            RadialButton(i, spec, placement, cx, cy, reduced) {
                when (i) {
                    1 -> ArrangeMenu(arrangeOpen, { arrangeOpen = false }, actions.arrange)
                    5 -> MoreMenu(moreOpen, { moreOpen = false }, selectionCount > 0, pageCount, currentPageNumber, actions)
                }
            }
        }
    }
}

private class RadialSpec(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun RadialButton(
    index: Int,
    spec: RadialSpec,
    placement: RadialPlacement,
    cx: Float,
    cy: Float,
    reduced: Boolean,
    menu: @Composable () -> Unit,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduced) delay(index * 28L)
        appear.animateTo(1f, tween(LassoMotion.duration(LassoMotion.TOOLBAR_MS + 40, reduced), easing = FastOutSlowInEasing))
    }
    val half = with(density) { RadialMetrics.button.toPx() } / 2f
    val tint = when {
        !spec.enabled -> scheme.onSurface.copy(alpha = 0.35f)
        spec.destructive -> scheme.error
        spec.selected -> scheme.primary
        else -> scheme.onSurface
    }
    Column(
        Modifier
            .offset {
                val t = appear.value
                // Spring out from the pivot; with reduced motion the buttons are already in place and just fade.
                val x = if (reduced) cx else placement.pivotX + (cx - placement.pivotX) * t
                val y = if (reduced) cy else placement.pivotY + (cy - placement.pivotY) * t
                IntOffset((x - half).roundToInt(), (y - half).roundToInt())
            }
            .graphicsLayer {
                alpha = appear.value
                val s = if (reduced) 1f else 0.6f + 0.4f * appear.value
                scaleX = s; scaleY = s
            }
            .wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier
                    .size(RadialMetrics.button)
                    .nivoCard(CircleShape, scheme.surfaceContainerHigh.copy(alpha = 0.97f), if (spec.selected) scheme.primary else scheme.outlineVariant)
                    .clickable(enabled = spec.enabled, role = Role.Button, onClick = spec.onClick)
                    .semantics { contentDescription = spec.label },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(spec.icon, null, Modifier.size(22.dp), tint = tint)
            }
            menu()
        }
        androidx.compose.material3.Text(
            spec.label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(50))
                .nivoCard(RoundedCornerShape(50), scheme.surfaceContainerHigh.copy(alpha = 0.9f), scheme.outlineVariant.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
