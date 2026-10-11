package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch

/** The launcher's long-press menu: one rounded tonal card with icon-led rows, used for every icon. */
@Composable
fun NivoMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Opened from an icon or widget: show it as a spotlight attached to that item. Elsewhere it is a plain dropdown.
    val anchor = LocalMenuAnchor.current
    if (anchor != null) {
        SpotlightMenu(anchor, expanded, onDismiss, content)
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 220.dp),
        // Same radius as the spotlight card, so every menu in the launcher reads as one family.
        shape = RoundedCornerShape(NivoRadius.card),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
fun NivoMenuItem(label: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val text = if (destructive) scheme.error else scheme.onSurface
    val iconTint = if (destructive) scheme.error else scheme.onSurfaceVariant
    // Drawn here rather than with the stock menu row, which reserves extra room on one side. This one is
    // 16dp of padding all round, so the card hugs it evenly.
    androidx.compose.foundation.layout.Row(
        Modifier
            .padding(horizontal = MenuInset)
            .fillMaxWidth()
            .defaultMinSize(minHeight = NivoTouch.min)
            // Inner radius = card radius minus the inset, so the press highlight sits concentric with the card.
            .clip(RoundedCornerShape(NivoRadius.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NivoSpace.l, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint)
        Text(
            label, style = MaterialTheme.typography.bodyLarge, color = text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = NivoSpace.l),
        )
    }
}

/** Picks a glyph for the context-specific actions that screens pass in by label. */
fun menuIconFor(label: String): ImageVector = when {
    label.startsWith("Select") -> Icons.Outlined.CheckCircle
    label.startsWith("Remove") || label.startsWith("Move out") -> Icons.Outlined.Close
    label.startsWith("Add to Home") -> Icons.Outlined.Add
    label.contains("folder", true) -> Icons.Outlined.CreateNewFolder
    label.contains("favourites", true) -> Icons.Outlined.Favorite
    label.startsWith("Hide") -> Icons.Outlined.VisibilityOff
    label.startsWith("Show") -> Icons.Outlined.Visibility
    label.startsWith("Move") -> Icons.Outlined.FolderOpen
    else -> Icons.Outlined.MoreHoriz
}

object MenuIcons {
    val Shortcut = Icons.Outlined.Bolt
    val Rename = Icons.Outlined.Edit
    val ChangeIcon = Icons.Outlined.Image
    val Info = Icons.Outlined.Info
    val Uninstall = Icons.Outlined.Delete
    val Open = Icons.Outlined.FolderOpen
    val Resize = Icons.Outlined.OpenInFull
    val Weight = Icons.Outlined.FormatBold
    val Up = Icons.Outlined.KeyboardArrowUp
    val Down = Icons.Outlined.KeyboardArrowDown
}

/** One of the big, icon-over-label buttons in the row at the top of a menu. */
@Composable
fun NivoQuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (destructive) scheme.error else scheme.onSurface
    androidx.compose.foundation.layout.Column(
        modifier
            .defaultMinSize(minHeight = NivoTouch.min)
            .clip(RoundedCornerShape(NivoRadius.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NivoSpace.s, vertical = 10.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(bottom = NivoSpace.xs))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = tint,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of [NivoQuickAction]s sharing the width evenly. */
@Composable
fun NivoQuickRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(horizontal = MenuInset).fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NivoSpace.xs),
        content = content,
    )
}

/** Horizontal inset of menu rows inside the card: card radius minus row radius, so corners stay concentric. */
private val MenuInset = NivoRadius.card - NivoRadius.control

/**
 * A full-width, 48dp-tall choice in a dialog list ("Move to…", "Add to folder"): a leading glyph, an ellipsised label and a
 * rounded press highlight, so pickers across the drawer, library and selection bar look and behave the same.
 */
@Composable
internal fun NivoChoiceRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = NivoTouch.min)
            .clip(RoundedCornerShape(NivoRadius.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NivoSpace.s, vertical = NivoSpace.s),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = scheme.onSurfaceVariant)
        Text(
            label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = NivoSpace.l),
        )
    }
}
