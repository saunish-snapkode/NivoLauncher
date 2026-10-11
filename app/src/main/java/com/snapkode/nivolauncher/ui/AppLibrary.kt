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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.pressFeedback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private enum class LibraryFilter(val label: String) { All("All"), Auto("Auto-sorted"), Recent("Recent"), Hidden("Hidden") }

/** "1 app", "3 apps": counts read naturally instead of "1 apps". */
private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

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
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = NivoSpace.xxl, bottom = NivoSpace.s)) {
                Text(
                    "Library", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "${plural(groups.sumOf { it.apps.size }, "app")} in ${plural(groups.size, "category", "categories")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = NivoSpace.xxs),
                )
            }
            Row(
                Modifier
                    .then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))
                    .padding(horizontal = NivoSpace.l).padding(top = NivoSpace.xs),
                horizontalArrangement = Arrangement.spacedBy(NivoSpace.s),
            ) {
                LibraryFilter.entries.forEach { f ->
                    androidx.compose.material3.FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label, maxLines = 1, softWrap = false) },
                        shape = NivoShapes.Pill,
                        leadingIcon = if (filter == f) {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
            if (groups.isEmpty()) {
                LibraryEmpty(filter)
            } else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = NivoSpace.l, end = NivoSpace.l, top = NivoSpace.m, bottom = 96.dp),
                // Each category is its own card now, so give them room to read as separate objects.
                verticalArrangement = Arrangement.spacedBy(NivoSpace.s),
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
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f * t))
                    .clickable(interactionSource = null, indication = null, enabled = t > 0.5f, onClickLabel = "Close ${group.name}") { close() }
            )
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .clip(RoundedCornerShape(lerp(NivoRadius.sheet.value, 0f, t).dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxSize().alpha(((t - 0.35f) / 0.65f).coerceIn(0f, 1f)).statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 28.dp, end = NivoSpace.l, top = 20.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                group.name, style = MaterialTheme.typography.headlineLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                plural(group.apps.size, "app"), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = NivoSpace.xxs),
                            )
                        }
                        TextButton(onClick = { close() }, modifier = Modifier.padding(start = NivoSpace.s)) { Text("Done") }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(settings.drawerColumns),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(start = NivoSpace.s, end = NivoSpace.s, top = NivoSpace.xs, bottom = NivoSpace.l),
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
    val press = remember { MutableInteractionSource() }
    val reduced = LocalMotion.current.reduced
    Box(
        modifier
            .fillMaxWidth()
            // A large card only needs a slight give; reduced motion keeps it still.
            .pressFeedback(press, scale = if (reduced) 1f else 0.98f)
            .clip(shape)
            .background(if (highlighted) scheme.primaryContainer else scheme.surfaceContainer)
            .border(1.dp, if (highlighted) scheme.primary.copy(alpha = 0.35f) else scheme.outlineVariant, shape)
            .combinedClickable(
                interactionSource = press,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Open ${group.name}",
                onLongClickLabel = "${group.name} options",
                onLongClick = { menu = true },
                onClick = onOpen,
            )
            .padding(start = 20.dp, end = NivoSpace.m, top = NivoSpace.l, bottom = NivoSpace.l)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A long name ellipsises but never pushes the count or the chevron off the card.
                Row(Modifier.weight(1f).padding(end = NivoSpace.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name, style = MaterialTheme.typography.titleLarge,
                        color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "${group.apps.size}", style = MaterialTheme.typography.titleMedium,
                        color = if (highlighted) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = NivoSpace.m),
                    )
                }
                Box(
                    Modifier.size(44.dp).background(if (highlighted) scheme.primary.copy(alpha = 0.3f) else scheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.ChevronRight, null, tint = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface) }
            }
            // A row of real icons you can tap to launch without opening the category.
            Row(Modifier.padding(top = 14.dp, end = NivoSpace.s), horizontalArrangement = Arrangement.spacedBy(NivoSpace.m)) {
                group.apps.take(5).forEach { app ->
                    androidx.compose.foundation.Image(
                        app.icon, app.label,
                        Modifier.size(50.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Open") { viewModel.launch(app) },
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
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(24) }, singleLine = true,
                    label = { Text("Category name") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.renameCategory(group.id, text); renaming = false }),
                )
            },
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
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NivoRadius.card))
            .combinedClickable(onClickLabel = "Open", onLongClickLabel = "More options", onClick = { viewModel.launch(app) }, onLongClick = { menu = true })
            .padding(vertical = 10.dp),
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
            title = { Text("Move ${app.label} to", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    // Long category lists scroll inside the dialog instead of pushing the buttons off-screen.
                    Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        groups.filter { it.id != group.id }.forEach { g ->
                            NivoChoiceRow(g.name, Icons.Outlined.Folder) { viewModel.moveAppToCategory(app.id, g.id); moving = false }
                        }
                    }
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it.take(24) }, singleLine = true,
                        label = { Text("New category") }, modifier = Modifier.fillMaxWidth().padding(top = NivoSpace.s),
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

/** Shown when a filter has nothing in it (no hidden apps, nothing installed lately) instead of a blank page. */
@Composable
private fun LibraryEmpty(filter: LibraryFilter) {
    val scheme = MaterialTheme.colorScheme
    val (title, body) = when (filter) {
        LibraryFilter.Hidden -> "No hidden apps" to "Apps you hide from the drawer will be listed here."
        LibraryFilter.Recent -> "Nothing new yet" to "Apps you install will show up here first."
        else -> "No categories to show" to "Your apps will be sorted into categories as soon as they load."
    }
    val icon = when (filter) {
        LibraryFilter.Hidden -> Icons.Outlined.VisibilityOff
        LibraryFilter.Recent -> Icons.Outlined.Schedule
        else -> Icons.Outlined.Category
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xxl, vertical = NivoSpace.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).background(scheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp)) }
        Text(
            title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = NivoSpace.l),
        )
        Text(
            body, style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = NivoSpace.xs),
        )
        Spacer(Modifier.height(NivoSpace.xxxl))
    }
}
