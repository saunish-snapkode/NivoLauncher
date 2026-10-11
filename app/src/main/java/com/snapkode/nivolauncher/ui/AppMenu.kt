package com.snapkode.nivolauncher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.AppInfo

/** An extra, context-specific entry shown beneath the shared App info / Uninstall entries. */
data class MenuAction(val label: String, val run: () -> Unit)

@Composable
fun AppMenu(
    expanded: Boolean,
    app: AppInfo,
    viewModel: LauncherViewModel,
    extraActions: List<MenuAction>,
    onDismiss: () -> Unit,
) {
    var shortcuts by remember(app.id) { mutableStateOf(emptyList<LauncherViewModel.AppShortcut>()) }
    LaunchedEffect(expanded) {
        if (expanded) shortcuts = viewModel.shortcutsFor(app)
    }
    var renaming by remember(app.id) { mutableStateOf(false) }
    var changingIcon by remember(app.id) { mutableStateOf(false) }
    if (renaming) RenameDialog(app, viewModel) { renaming = false }
    if (changingIcon) IconPickerDialog(app, viewModel) { changingIcon = false }
    NivoMenu(expanded = expanded, onDismiss = onDismiss) {
        shortcuts.forEach { shortcut ->
            NivoMenuItem(shortcut.label, MenuIcons.Shortcut, { onDismiss(); viewModel.startShortcut(shortcut) })
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        // The three actions people reach for most sit in a row; the rest stay in a short list below.
        val select = extraActions.firstOrNull { it.label == "Select" }
        NivoQuickRow {
            if (select != null) NivoQuickAction("Select", menuIconFor("Select"), { onDismiss(); select.run() }, Modifier.weight(1f))
            NivoQuickAction("App info", MenuIcons.Info, { onDismiss(); viewModel.showAppInfo(app) }, Modifier.weight(1f))
            NivoQuickAction("Uninstall", MenuIcons.Uninstall, { onDismiss(); viewModel.uninstall(app) }, Modifier.weight(1f), destructive = true)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        extraActions.filter { it !== select }.forEach { action ->
            NivoMenuItem(action.label, menuIconFor(action.label), { onDismiss(); action.run() })
        }
        NivoMenuItem("Rename", MenuIcons.Rename, { onDismiss(); renaming = true })
        NivoMenuItem("Change icon", MenuIcons.ChangeIcon, { onDismiss(); changingIcon = true })
    }
}

@Composable
private fun RenameDialog(app: AppInfo, viewModel: LauncherViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf(app.label) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Rename app") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { viewModel.renameApp(app.id, name); onDone() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { viewModel.renameApp(app.id, ""); onDone() }) { Text("Reset") } },
    )
}

/** Lets the user replace an app's icon with a picture or an icon from the selected icon pack. */
@Composable
private fun IconPickerDialog(app: AppInfo, viewModel: LauncherViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importCustomIcon(app.id, uri)
        onDone()
    }
    val pack = remember(settings.iconPack) {
        if (settings.iconPack.isEmpty()) null else com.snapkode.nivolauncher.data.IconPack.load(context, settings.iconPack)
    }
    val names = remember(pack) { pack?.drawableNames().orEmpty() }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Icon for ${app.label}") },
        text = {
            Column {
                TextButton(onClick = { gallery.launch("image/*") }) { Text("Choose a picture…") }
                TextButton(onClick = { viewModel.setIconOverride(app.id, null); onDone() }) { Text("Reset to default") }
                if (pack != null && names.isNotEmpty()) {
                    Text("From ${settings.iconPack}", style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                        modifier = Modifier.heightIn(max = 280.dp),
                    ) {
                        items(names, key = { it }) { name ->
                            val bitmap = remember(name) {
                                pack.drawableByName(name)?.let {
                                    androidx.core.graphics.drawable.DrawableCompat.wrap(it).toBitmap(96, 96).asImageBitmap()
                                }
                            }
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap, name,
                                    Modifier.padding(4.dp).size(48.dp).clickable {
                                        viewModel.setIconOverride(app.id, "pack:${settings.iconPack}/$name"); onDone()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Pick an icon pack in Settings → Icons to choose from its icons here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Close") } },
    )
}
