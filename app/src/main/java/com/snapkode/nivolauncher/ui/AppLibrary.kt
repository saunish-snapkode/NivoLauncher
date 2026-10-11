package com.snapkode.nivolauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.LibraryGroup
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.NivoRadius
import com.snapkode.nivolauncher.ui.design.NivoShapes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private enum class LibraryFilter(val label: String) { All("All"), Auto("Auto-sorted"), Recent("Recent"), Hidden("Hidden") }

/**
 * The Nivo App Library: every app sorted into editable categories. Tap a card to expand it from
 * where it sits; tap one of its four preview icons to launch straight away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPage(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val allGroups by viewModel.libraryGroups.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val allApps by viewModel.apps.collectAsState()
    var filter by remember { mutableStateOf(LibraryFilter.All) }
    val groups = remember(allGroups, allApps, settings.hiddenApps, filter) {
        when (filter) {
            LibraryFilter.All -> allGroups
            LibraryFilter.Auto -> allGroups.filter { !it.id.startsWith("custom:") }
            LibraryFilter.Recent -> listOf(
                com.snapkode.nivolauncher.data.LibraryGroup("recent", "Recently installed", allApps.sortedByDescending { it.installTime }.take(12))
            ).filter { it.apps.isNotEmpty() }
            LibraryFilter.Hidden -> listOf(
                com.snapkode.nivolauncher.data.LibraryGroup("hidden", "Hidden apps", allApps.filter { it.id in settings.hiddenApps })
            ).filter { it.apps.isNotEmpty() }
        }
    }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var pageRect by remember { mutableStateOf(Rect.Zero) }
    var openId by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val density = LocalDensity.current

    fun open(id: String) {
        openId = id
        scope.launch { progress.animateTo(1f, motion.spring(1f)) }
    }
    fun close() {
        scope.launch {
            progress.animateTo(0f, motion.spring(1.2f))
            openId = null
        }
    }
    BackHandler(enabled = openId != null) { close() }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
    ) {
    Box(modifier.fillMaxSize().onGloballyPositioned { pageRect = it.boundsInRoot() }) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 8.dp)) {
                Text("Library", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${groups.sumOf { it.apps.size }} apps in ${groups.size} categories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier
                    .then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))
                    .padding(horizontal = 16.dp).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.forEach { f ->
                    androidx.compose.material3.FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label, maxLines = 1, softWrap = false) },
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = if (filter == f) {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(groups, key = { _, g -> g.id }) { index, group ->
                    CategoryCard(
                        group = group,
                        viewModel = viewModel,
                        shape = NivoShapes.group(index, groups.size),
                        highlighted = index == 0,
                        modifier = Modifier
                            .alpha(if (openId == group.id) 0f else 1f)
                            .onGloballyPositioned { bounds[group.id] = it.boundsInRoot() },
                        onOpen = { open(group.id) },
                    )
                }
            }
        }

        val group = groups.firstOrNull { it.id == openId }
        val from = bounds[openId]
        if (group != null && from != null) {
            val t = progress.value.coerceIn(0f, 1f) // springs can overshoot; geometry must not
            val full = Rect(0f, 0f, pageRect.width, pageRect.height)
            val start = Rect(from.left - pageRect.left, from.top - pageRect.top, from.right - pageRect.left, from.bottom - pageRect.top)
            val left = lerp(start.left, full.left, t)
            val top = lerp(start.top, full.top, t)
            val width = lerp(start.width, full.width, t)
            val height = lerp(start.height, full.height, t)
            // Everything else recedes while the card grows.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f * t)).clickable(enabled = t > 0.5f) { close() })
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .clip(RoundedCornerShape(lerp(28f, 0f, t).dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxSize().alpha(((t - 0.35f) / 0.65f).coerceIn(0f, 1f)).statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.headlineLarge)
                            Text("${group.apps.size} apps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { close() }) { Text("Done") }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(settings.drawerColumns),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        items(group.apps, key = { it.id }) { app ->
                            LibraryApp(app, group, groups, viewModel, settings.drawerIconScale, settings.showDrawerLabels)
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCard(
    group: LibraryGroup,
    viewModel: LauncherViewModel,
    shape: androidx.compose.ui.graphics.Shape,
    highlighted: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) scheme.primaryContainer else scheme.surfaceContainer)
            .border(1.dp, if (highlighted) scheme.primary.copy(alpha = 0.35f) else scheme.outlineVariant, shape)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name, style = MaterialTheme.typography.titleLarge,
                    color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${group.apps.size}", style = MaterialTheme.typography.titleMedium,
                    color = if (highlighted) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                Box(
                    Modifier.size(44.dp).background(if (highlighted) scheme.primary.copy(alpha = 0.3f) else scheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.ChevronRight, "Open ${group.name}", tint = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface) }
            }
            // A row of real icons you can tap to launch without opening the category.
            Row(Modifier.padding(top = 14.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                group.apps.take(5).forEach { app ->
                    androidx.compose.foundation.Image(
                        app.icon, app.label,
                        Modifier.size(50.dp).clip(CircleShape).clickable { viewModel.launch(app) },
                    )
                }
            }
        }
        NivoMenu(expanded = menu, onDismiss = { menu = false }) {
            NivoMenuItem("Rename", MenuIcons.Rename, { menu = false; renaming = true })
            NivoMenuItem("Move earlier", MenuIcons.Up, { menu = false; viewModel.moveCategory(group.id, -1) })
            NivoMenuItem("Move later", MenuIcons.Down, { menu = false; viewModel.moveCategory(group.id, 1) })
            NivoMenuItem("Hide category", menuIconFor("Hide"), { menu = false; viewModel.hideCategory(group.id) })
            if (group.id.startsWith("custom:")) {
                NivoMenuItem("Delete category", MenuIcons.Uninstall, { menu = false; viewModel.deleteCategory(group.id) }, destructive = true)
            }
        }
    }
    if (renaming) {
        var text by remember { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename category") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(24) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.renameCategory(group.id, text); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { viewModel.renameCategory(group.id, ""); renaming = false }) { Text("Reset") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryApp(
    app: AppInfo,
    group: LibraryGroup,
    groups: List<LibraryGroup>,
    viewModel: LauncherViewModel,
    scale: Float,
    showLabel: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().combinedClickable(onClick = { viewModel.launch(app) }, onLongClick = { menu = true }).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppIconView(app, scale, showLabel, onWallpaper = false)
        AppMenu(
            expanded = menu, app = app, viewModel = viewModel,
            extraActions = buildList {
                if (!viewModel.isOnHome(app.id)) add(MenuAction("Add to Home") { viewModel.addToHome(app.id) })
                add(MenuAction("Move to category…") { moving = true })
            },
            onDismiss = { menu = false },
        )
    }
    if (moving) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { moving = false },
            title = { Text("Move ${app.label} to") },
            text = {
                Column {
                    groups.filter { it.id != group.id }.forEach { g ->
                        Text(
                            g.name,
                            Modifier.fillMaxWidth().clickable { viewModel.moveAppToCategory(app.id, g.id); moving = false }.padding(vertical = 12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it.take(24) }, singleLine = true,
                        label = { Text("New category") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    val id = viewModel.createCategory(newName)
                    viewModel.moveAppToCategory(app.id, id)
                    moving = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { moving = false }) { Text("Cancel") } },
        )
    }
}
