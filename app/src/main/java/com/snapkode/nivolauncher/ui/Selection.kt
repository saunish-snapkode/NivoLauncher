package com.snapkode.nivolauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.isFolderId
import com.snapkode.nivolauncher.data.isWidgetId
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.NivoAlpha
import com.snapkode.nivolauncher.ui.design.NivoElevation
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.NivoTouch
import com.snapkode.nivolauncher.ui.design.nivoCard

/** Which screen a selection started on; taps only add to the selection on that screen. */
enum class SelectionSource { Drawer, Home }

/** Multi-select across the drawer and the home screen. Inactive until something is chosen from an icon's menu. */
class SelectionState {
    var active by mutableStateOf(false)
        private set
    var source by mutableStateOf(SelectionSource.Drawer)
        private set
    val selected = mutableStateListOf<String>()

    fun start(id: String, from: SelectionSource) {
        selected.clear()
        selected += id
        source = from
        active = true
    }

    fun toggle(id: String) {
        if (id in selected) selected -= id else selected += id
        if (selected.isEmpty()) clear()
    }

    fun clear() {
        active = false
        selected.clear()
    }
}

val LocalSelection = compositionLocalOf<SelectionState?> { null }

/** The floating card that appears while icons are selected: a count, a close button and the actions that apply. */
@Composable
fun SelectionBar(viewModel: LauncherViewModel, selection: SelectionState, drawer: DrawerState) {
    val apps by viewModel.apps.collectAsState()
    val drawerFolders by viewModel.drawerFolders.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    var choosingFolder by remember { mutableStateOf(false) }

    BackHandler(enabled = selection.active) { selection.clear() }
    // Opening or closing the drawer ends a selection that belongs to the other screen.
    LaunchedEffect(drawer.isOpen) {
        if (selection.active && (selection.source == SelectionSource.Drawer) != drawer.isOpen) selection.clear()
    }

    AnimatedVisibility(
        visible = selection.active,
        // Rises from the bottom edge on the motion preset's spring; reduced motion makes it a quick fade.
        enter = slideInVertically(motion.spring<IntOffset>()) { if (motion.reduced) 0 else it } + fadeIn(tween(motion.duration(180))),
        exit = slideOutVertically(motion.spring<IntOffset>(1.4f)) { if (motion.reduced) 0 else it } + fadeOut(tween(motion.duration(140))),
    ) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(NivoSpace.l), contentAlignment = Alignment.BottomCenter) {
            val shape = RoundedCornerShape(NivoRadius.sheet)
            // Swallow taps on the bar itself so they never reach icons beneath it. There is no Surface here, so set the text colour.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    // A floating bar over icons: lift it off the content so it never reads as part of the grid.
                    .shadow(NivoElevation.modal, shape)
                    .nivoCard(shape, scheme.surfaceContainerHigh, scheme.outlineVariant)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                    .padding(NivoSpace.s),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(NivoTouch.min).clip(CircleShape).clickable(role = Role.Button) { selection.clear() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, "Cancel selection", tint = scheme.onSurfaceVariant) }
                    val count = selection.selected.size
                    Text(
                        "$count selected",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        // Announce the running count as items are added or removed.
                        modifier = Modifier.padding(start = NivoSpace.xs).weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                val ids = selection.selected.toList()
                val chosenApps = ids.mapNotNull { id -> apps.firstOrNull { it.id == id } }
                Row(Modifier.fillMaxWidth().padding(top = NivoSpace.xs), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (selection.source == SelectionSource.Drawer) {
                        BarAction("Add to Home", Icons.Outlined.Add) {
                            ids.forEach(viewModel::addToHome); selection.clear()
                        }
                        BarAction("Folder", Icons.Outlined.CreateNewFolder) { choosingFolder = true }
                        BarAction("Hide", Icons.Outlined.VisibilityOff) {
                            ids.forEach { viewModel.setHidden(it, true) }; selection.clear()
                        }
                    } else {
                        BarAction("Group", Icons.Outlined.CreateNewFolder, enabled = chosenApps.size >= 2) {
                            viewModel.groupIntoFolder(ids); selection.clear()
                        }
                        BarAction("Remove", Icons.Filled.Close) {
                            ids.forEach(viewModel::removeFromHome); selection.clear()
                        }
                    }
                    BarAction("Uninstall", Icons.Outlined.Delete, destructive = true, enabled = chosenApps.isNotEmpty()) {
                        chosenApps.forEach(viewModel::uninstall); selection.clear()
                    }
                }
            }
            }
        }
    }

    if (choosingFolder) {
        var name by remember { mutableStateOf("") }
        val ids = selection.selected.toList()
        AlertDialog(
            onDismissRequest = { choosingFolder = false },
            title = { Text("Add ${ids.size} to folder") },
            text = {
                Column {
                    drawerFolders.values.forEach { folder ->
                        NivoChoiceRow(folder.name, Icons.Outlined.Folder) {
                            ids.forEach { viewModel.addToDrawerFolder(folder.id, it) }
                            choosingFolder = false; selection.clear()
                        }
                    }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                        label = { Text("New folder") }, modifier = Modifier.fillMaxWidth().padding(top = NivoSpace.s),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createDrawerFolderWith(name, ids)
                        choosingFolder = false; selection.clear()
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { choosingFolder = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BarAction(label: String, icon: ImageVector, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = (if (destructive) scheme.error else scheme.onSurface).copy(alpha = if (enabled) NivoAlpha.high else NivoAlpha.disabled)
    Column(
        Modifier.widthIn(min = 72.dp)
            .clip(RoundedCornerShape(NivoRadius.control))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = NivoSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = tint,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = NivoSpace.xs),
        )
    }
}

/** Corner mark on an icon while selecting: a ring when unselected, a filled check once chosen, plus a soft halo. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        if (selected) {
            Box(
                Modifier.fillMaxSize()
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, scheme.primary, RoundedCornerShape(18.dp)),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(22.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.nivoCard(CircleShape, scheme.primary, scheme.primary) else Modifier.nivoCard(CircleShape, androidx.compose.ui.graphics.Color.Transparent, scheme.onSurface.copy(alpha = 0.45f))),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}
