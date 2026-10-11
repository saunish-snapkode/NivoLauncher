package com.snapkode.nivolauncher.ui.lasso

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AlignHorizontalCenter
import androidx.compose.material.icons.outlined.AlignVerticalCenter
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HorizontalDistribute
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.lasso.LassoMotion
import com.snapkode.nivolauncher.lasso.LassoState
import com.snapkode.nivolauncher.lasso.SelectionMode
import com.snapkode.nivolauncher.ui.NivoMenu
import com.snapkode.nivolauncher.ui.NivoMenuItem
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.nivoCard

enum class ArrangeKind(val label: String) {
    AlignHorizontally("Align horizontally"),
    AlignVertically("Align vertically"),
    Distribute("Distribute evenly"),
    Compact("Compact grid"),
}

/** What the toolbar and the radial menu can ask for. The layer supplies the behaviour; these are only buttons. */
class LassoActions(
    val arrange: (ArrangeKind) -> Unit,
    val createFolder: () -> Unit,
    val gather: () -> Unit,
    val remove: () -> Unit,
    val moveToPage: (Int) -> Unit,
    val selectAll: () -> Unit,
    val clear: () -> Unit,
    val done: () -> Unit,
    val cancel: () -> Unit,
    val undo: () -> Unit,
    val toggleMove: () -> Unit,
    val previousPage: () -> Unit,
    val nextPage: () -> Unit,
)

/**
 * The contextual toolbar. It is always present in lasso mode: it shows the selection count, the Add/Remove switch,
 * Cancel and Done, and (unless the radial menu is in use) every action as a labelled button.
 */
@Composable
internal fun LassoToolbar(
    session: LassoSession,
    undoLabel: String?,
    showActions: Boolean,
    currentPageNumber: Int?,
    pageCount: Int,
    actions: LassoActions,
    modifier: Modifier = Modifier,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val ms = LassoMotion.duration(LassoMotion.TOOLBAR_MS, reduced)
    val count = session.selectionCount
    val busy = session.state == LassoState.DRAWING || session.state == LassoState.DRAGGING

    AnimatedVisibility(
        visible = session.active,
        modifier = modifier,
        enter = slideInVertically(tween(ms, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(ms)),
        exit = slideOutVertically(tween(ms, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(ms)),
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .nivoCard(shape, scheme.surfaceContainerHigh.copy(alpha = 0.97f), scheme.outlineVariant)
                // The toolbar must swallow touches meant for it, never letting them fall through to the workspace.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // Row 1: cancel, count, mode, done.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(Icons.Filled.Close, "Cancel and undo changes", onClick = actions.cancel)
                Column(
                    Modifier.weight(1f).padding(horizontal = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    androidx.compose.material3.Text(
                        when {
                            count == 0 -> "Draw around icons"
                            count == 1 -> "1 selected"
                            else -> "$count selected"
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                    val line = session.message
                        ?: if (count == 0) "Or tap icons one by one" else currentPageNumber?.let { "Page $it" }
                    if (line != null) {
                        androidx.compose.material3.Text(
                            line,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = if (session.message != null) scheme.primary else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                ModeSwitch(session.mode) { session.mode = it }
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                androidx.compose.material3.Button(
                    onClick = actions.done,
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    androidx.compose.material3.Text("Done", Modifier.padding(start = 6.dp))
                }
            }

            // Row 2: page switcher (the overlay captures swipes, so changing page needs a control).
            if (pageCount > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    ToolButton(Icons.Filled.ChevronLeft, "Previous page", onClick = actions.previousPage, size = 36.dp)
                    androidx.compose.material3.Text(
                        currentPageNumber?.let { "Page $it of $pageCount" } ?: "Pages",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    ToolButton(Icons.Filled.ChevronRight, "Next page", onClick = actions.nextPage, size = 36.dp)
                }
            }

            // Row 3: the actions, when the radial menu is not carrying them.
            if (showActions) {
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 8.dp), color = scheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ArrangeButton(enabled = count > 0 && !busy, onChoose = actions.arrange)
                    ActionButton(Icons.Outlined.CreateNewFolder, "Folder", count >= 2 && !busy, actions.createFolder)
                    ActionButton(Icons.Outlined.FilterCenterFocus, "Gather", count > 0 && !busy, actions.gather)
                    ActionButton(Icons.Outlined.OpenWith, "Move", count > 0 && !busy, actions.toggleMove, selected = session.moveAnywhere)
                    ActionButton(Icons.Outlined.Delete, "Remove", count > 0 && !busy, actions.remove, destructive = true)
                    MoreButton(count > 0, !busy, pageCount, currentPageNumber, actions)
                }
            }

            if (undoLabel != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                    androidx.compose.material3.TextButton(onClick = actions.undo) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                        androidx.compose.material3.Text("Undo ${undoLabel.lowercase()}", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/** A small floating "Undo" that appears for a few seconds after a batch action once lasso mode has been left. */
@Composable
internal fun LassoUndoPill(label: String?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val reduced = LocalMotion.current.reduced
    val ms = LassoMotion.duration(LassoMotion.TOOLBAR_MS, reduced)
    AnimatedVisibility(
        visible = label != null,
        modifier = modifier,
        enter = slideInVertically(tween(ms, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(ms)),
        exit = fadeOut(tween(ms)),
    ) {
        val shape = CircleShape
        Row(
            Modifier
                .nivoCard(shape, scheme.surfaceContainerHigh, scheme.outlineVariant)
                .clickable(role = Role.Button, onClickLabel = "Undo", onClick = onUndo)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp), tint = scheme.primary)
            androidx.compose.material3.Text(
                "Undo ${label.orEmpty().lowercase()}",
                Modifier.padding(start = 8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        Modifier.size(maxOf(size, 48.dp)).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Add / Deselect: what the next stroke or tap does to the selection. */
@Composable
private fun ModeSwitch(mode: SelectionMode, onChange: (SelectionMode) -> Unit) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).border(1.dp, scheme.outlineVariant, CircleShape).padding(2.dp)
            .semantics { contentDescription = "Selection mode" },
    ) {
        for (m in SelectionMode.entries) {
            val on = m == mode
            val tint = if (m == SelectionMode.Subtract) scheme.error else scheme.primary
            androidx.compose.material3.Text(
                if (m == SelectionMode.Add) "Add" else "Deselect",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = if (on) tint else scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton, onClick = { onChange(m) })
                    .semantics { selected = on; stateDescription = if (on) "On" else "Off" }
                    .then(if (on) Modifier.border(1.dp, tint.copy(alpha = 0.5f), CircleShape) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val base = when {
        destructive -> scheme.error
        selected -> scheme.primary
        else -> scheme.onSurface
    }
    val tint = base.copy(alpha = if (enabled) 1f else 0.35f)
    Column(
        Modifier
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.border(1.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = tint)
        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun ArrangeButton(enabled: Boolean, onChoose: (ArrangeKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionButton(Icons.Outlined.AutoAwesomeMosaic, "Arrange", enabled, { open = true })
        ArrangeMenu(open, { open = false }, onChoose)
    }
}

@Composable
private fun MoreButton(hasSelection: Boolean, enabled: Boolean, pageCount: Int, currentPageNumber: Int?, actions: LassoActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionButton(Icons.Outlined.MoreHoriz, "More", enabled, { open = true })
        MoreMenu(open, { open = false }, hasSelection, pageCount, currentPageNumber, actions)
    }
}

/** The arrange choices, shared by the toolbar and the radial menu. */
@Composable
internal fun ArrangeMenu(expanded: Boolean, onDismiss: () -> Unit, onChoose: (ArrangeKind) -> Unit) {
    NivoMenu(expanded = expanded, onDismiss = onDismiss) {
        for (kind in ArrangeKind.entries) {
            NivoMenuItem(kind.label, when (kind) {
                ArrangeKind.AlignHorizontally -> Icons.Outlined.AlignHorizontalCenter
                ArrangeKind.AlignVertically -> Icons.Outlined.AlignVerticalCenter
                ArrangeKind.Distribute -> Icons.Outlined.HorizontalDistribute
                ArrangeKind.Compact -> Icons.Outlined.GridView
            }, { onDismiss(); onChoose(kind) })
        }
    }
}

/** Move to page, select all, clear. */
@Composable
internal fun MoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hasSelection: Boolean,
    pageCount: Int,
    currentPageNumber: Int?,
    actions: LassoActions,
) {
    // Skip the page the selection is on (not the page being looked at): moving there would do nothing.
    val selectionPage = LocalLasso.current?.page?.takeIf { it >= 0 } ?: currentPageNumber?.minus(1)
    NivoMenu(expanded = expanded, onDismiss = onDismiss) {
        if (hasSelection) {
            for (p in 0 until pageCount) {
                if (selectionPage == p) continue
                NivoMenuItem("Move to page ${p + 1}", Icons.Outlined.OpenWith, { onDismiss(); actions.moveToPage(p) })
            }
            NivoMenuItem("Move to a new page", Icons.Outlined.OpenWith, { onDismiss(); actions.moveToPage(pageCount) })
        }
        NivoMenuItem("Select all on this page", Icons.Filled.Check, { onDismiss(); actions.selectAll() })
        if (hasSelection) NivoMenuItem("Clear selection", Icons.Filled.Close, { onDismiss(); actions.clear() })
    }
}

