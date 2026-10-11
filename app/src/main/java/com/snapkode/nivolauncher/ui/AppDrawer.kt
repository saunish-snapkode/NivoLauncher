package com.snapkode.nivolauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.snapkode.nivolauncher.data.ContactResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.ui.design.nivoCard
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import com.snapkode.nivolauncher.data.DrawerMode
import com.snapkode.nivolauncher.data.DrawerSort
import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.SearchTools
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(viewModel: LauncherViewModel, drawer: DrawerState) {
    // Building the whole grid on the first frame of a swipe up cost a visible stall. Compose it once, shortly after start,
    // and keep it composed (parked off-screen) from then on, so opening is only a translation.
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1200); warm = true }
    if (!warm && !drawer.isOpen && !drawer.keepComposed) return
    DrawerContent(viewModel, drawer)
}

/** One tile in the drawer grid: a plain app or a user-made drawer folder. */
private sealed interface DrawerEntry {
    val key: String
    val label: String

    data class App(val app: AppInfo) : DrawerEntry {
        override val key get() = app.id
        override val label get() = app.label
    }

    data class Folder(val folder: HomeFolder) : DrawerEntry {
        override val key get() = folder.id
        override val label get() = folder.name
    }
}

private enum class DrawerTab { Personal, Work }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerContent(viewModel: LauncherViewModel, drawer: DrawerState) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val items by viewModel.items.collectAsState()
    val counts by viewModel.launchCounts.collectAsState()
    val drawerFolders by viewModel.drawerFolders.collectAsState()
    val hasWork by viewModel.hasWorkProfile.collectAsState()
    val workPaused by viewModel.workPaused.collectAsState()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(DrawerTab.Personal) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val predicted by viewModel.predicted.collectAsState()
    var contacts by remember { mutableStateOf(emptyList<ContactResult>()) }
    LaunchedEffect(query, settings.contactsSearch) {
        contacts = if (settings.contactsSearch && query.isNotBlank()) {
            delay(150) // debounce typing
            withContext(Dispatchers.IO) { viewModel.searchContacts(query) }
        } else emptyList()
    }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(query, settings.webSuggestions, settings.searchProvider) {
        suggestions = if (settings.webSuggestions && settings.webSearch && query.isNotBlank()) {
            delay(300)
            viewModel.webSuggestions(query.trim())
        } else emptyList()
    }
    val settingsHits = remember(query, settings.settingsSearch) {
        if (settings.settingsSearch) SearchTools.matchingSettings(query) else emptyList()
    }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // The drawer stays composed while closed, so each opening starts a fresh "session" here instead of a fresh composition.
    LaunchedEffect(drawer.isOpen) {
        if (drawer.isOpen) {
            if (drawer.focusSearch || settings.autoShowKeyboard) {
                drawer.focusSearch = false
                if (!settings.hideDrawerSearch) runCatching { searchFocus.requestFocus() }
            }
        } else {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
            query = ""
            openFolderId = null
        }
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    if (!hasWork && tab == DrawerTab.Work) tab = DrawerTab.Personal

    val searching = query.isNotBlank()
    val inFolder = remember(drawerFolders) { drawerFolders.values.flatMap { it.appIds }.toSet() }

    /** Apps matching the query (when searching) or the current tab, without folder grouping. */
    val matches by remember(apps, settings.hiddenApps, settings.hiddenAppsInSearch, settings.fuzzySearch, query, tab, hasWork) {
        derivedStateOf {
            val q = query.trim()
            if (q.isNotEmpty()) {
                apps.filter { settings.hiddenAppsInSearch || it.id !in settings.hiddenApps }
                    .map { it to SearchTools.score(it.label, q, settings.fuzzySearch) }
                    .filter { it.second > 0 }
                    .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }.thenBy { it.first.label.lowercase() })
                    .map { it.first }
            } else {
                apps.filter { app ->
                    app.id !in settings.hiddenApps &&
                        if (hasWork) viewModel.isWorkApp(app) == (tab == DrawerTab.Work) else true
                }
            }
        }
    }

    val entries by remember(matches, drawerFolders, inFolder, settings.drawerSort, counts, searching, tab) {
        derivedStateOf {
            val list: List<DrawerEntry> =
                if (searching) matches.map { DrawerEntry.App(it) }
                else matches.filter { it.id !in inFolder }.map { DrawerEntry.App(it) } +
                    if (tab == DrawerTab.Personal) drawerFolders.values.map { DrawerEntry.Folder(it) } else emptyList()
            val byName = compareBy<DrawerEntry> { it.label.lowercase() }
            if (searching) return@derivedStateOf list
            when (settings.drawerSort) {
                DrawerSort.AToZ -> list.sortedWith(byName)
                DrawerSort.ZToA -> list.sortedWith(byName.reversed())
                DrawerSort.Recent -> list.sortedByDescending { (it as? DrawerEntry.App)?.app?.installTime ?: Long.MAX_VALUE }
                DrawerSort.MostUsed -> list.sortedWith(
                    compareByDescending<DrawerEntry> { if (it is DrawerEntry.App) counts[it.app.id] ?: 0 else Int.MAX_VALUE }.then(byName)
                )
            }
        }
    }

    val connection = remember(drawer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drawer.progress < 1f && available.y < 0f) {
                    drawer.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    drawer.dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawer.progress < 1f) {
                    drawer.settle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && drawer.progress >= 1f) {
                    drawer.settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val gridState = rememberLazyGridState()
    // Every opening starts at the top, as it did when the drawer was rebuilt each time.
    LaunchedEffect(drawer.isOpen) { if (!drawer.isOpen && (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)) gridState.scrollToItem(0) }
    val showSuggested = !searching && settings.predictions && predicted.isNotEmpty() && tab == DrawerTab.Personal
    val appsById = remember(apps) { apps.associateBy { it.id } }
    val homeIds = remember(items) { items.mapTo(HashSet()) { it.id } }
    val favoriteApps = remember(settings.favorites, appsById, settings.hiddenApps) {
        settings.favorites.mapNotNull(appsById::get).filter { it.id !in settings.hiddenApps }.sortedBy { it.label.lowercase() }
    }
    val showFavorites = !searching && favoriteApps.isNotEmpty() && tab == DrawerTab.Personal
    val headerCount = (if (showFavorites) 2 else 0) + (if (showSuggested) 3 else 0)
    val groups by viewModel.libraryGroups.collectAsState()
    val byCategory = settings.drawerMode == DrawerMode.Categories && !searching
    val categorySections = remember(groups, tab, hasWork) {
        groups.map { g ->
            g.copy(apps = if (hasWork) g.apps.filter { viewModel.isWorkApp(it) == (tab == DrawerTab.Work) } else g.apps)
        }.filter { it.apps.isNotEmpty() }
    }
    val listMode = settings.drawerMode == DrawerMode.List

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - drawer.progress) * size.height
                // Rounded corners only matter while the sheet is still rising; skip the clip once it is full-screen.
                if (drawer.progress < 0.995f) {
                    shape = RoundedCornerShape(topStart = 32.dp * (1f - drawer.progress), topEnd = 32.dp * (1f - drawer.progress))
                    clip = true
                }
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = settings.drawerOpacity))
            // Parked off-screen while closed: invisible to touch and to screen readers.
            .then(if (drawer.isOpen) Modifier else Modifier.clearAndSetSemantics { })
            .nestedScroll(connection)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = settings.drawerTopPadding.dp)) {
            Spacer(Modifier.height(12.dp))
            if (!settings.hideDrawerSearch) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    focusRequester = searchFocus,
                    onSearch = {
                        val first = (entries.firstOrNull { it is DrawerEntry.App } as? DrawerEntry.App)?.app
                        when {
                            first != null -> viewModel.launch(first)
                            searching && settings.webSearch -> viewModel.searchWeb(query.trim())
                        }
                    },
                )
            }
            if (hasWork && !searching) {
                TabRow(tab, onSelect = { tab = it })
            }
            val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)
            val margin = settings.drawerSideMargin.dp
            if (hasWork && tab == DrawerTab.Work && !searching) {
                WorkBanner(paused = workPaused, onToggle = { viewModel.setWorkPaused(!workPaused) })
            }
            if (hasWork && tab == DrawerTab.Work && workPaused && !searching) {
                Text(
                    "Work apps are paused",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else if (searching) {
                SearchResults(
                    apps = matches.take(8),
                    contacts = contacts,
                    settingsHits = settingsHits,
                    suggestions = if (settings.webSearch) suggestions else emptyList(),
                    webLabel = if (settings.webSearch) "Search ${settings.searchProvider.label} for “${query.trim()}”" else null,
                    viewModel = viewModel,
                    onWeb = { viewModel.searchWeb(query.trim()) },
                    bottomPadding = bottomInsets.asPaddingValues().calculateBottomPadding(),
                )
            } else Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(if (listMode) 1 else settings.drawerColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = margin, end = margin + if (settings.drawerScroller) 20.dp else 0.dp, top = 4.dp,
                        bottom = bottomInsets.asPaddingValues().calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    if (showFavorites) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "favorites-header") { SectionTitle("Favourites") }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "favorites-row") {
                            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = settings.drawerColumns) {
                                favoriteApps.forEach { app ->
                                    Box(Modifier.weight(1f, fill = false).width(maxStripCellWidth(settings.drawerColumns))) {
                                        DrawerCell(app, viewModel, settings, isOnHome = app.id in homeIds, folderId = null, drawerFolders, list = false)
                                    }
                                }
                            }
                        }
                    }
                    if (showSuggested) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "suggested-header") { SectionTitle("Suggested") }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "suggested-row") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    .nivoCard(RoundedCornerShape(24.dp), MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(vertical = 4.dp),
                            ) {
                                predicted.take(settings.drawerColumns).forEach { app ->
                                    Box(Modifier.weight(1f)) {
                                        DrawerCell(app, viewModel, settings, isOnHome = app.id in homeIds, folderId = null, drawerFolders, list = false)
                                    }
                                }
                                repeat(settings.drawerColumns - predicted.size.coerceAtMost(settings.drawerColumns)) {
                                    Box(Modifier.weight(1f))
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "all-header") { SectionTitle("All apps") }
                    }
                    if (byCategory) {
                        categorySections.forEach { section ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "cat-${section.id}") { SectionTitle("${section.name} · ${section.apps.size}") }
                            items(section.apps, key = { "cat-${section.id}-${it.id}" }) { app ->
                                DrawerCell(app, viewModel, settings, isOnHome = app.id in homeIds, folderId = null, drawerFolders, list = listMode)
                            }
                        }
                    }
                    items(if (byCategory) emptyList() else entries, key = { it.key }) { entry ->
                        when (entry) {
                            is DrawerEntry.App ->
                                DrawerCell(entry.app, viewModel, settings, isOnHome = entry.app.id in homeIds, folderId = null, drawerFolders, list = listMode)
                            is DrawerEntry.Folder -> Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { openFolderId = entry.folder.id }
                                    .padding(vertical = settings.drawerCellPadding.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                FolderIconView(
                                    entry.folder, appsById, settings.drawerIconScale,
                                    settings.showDrawerLabels, onWallpaper = false,
                                )
                            }
                        }
                    }
                    if (contacts.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "contacts-header") { SectionTitle("Contacts") }
                        items(contacts, key = { "contact-${it.contactId}" }, span = { GridItemSpan(maxLineSpan) }) { contact ->
                            ResultRow(contact.name.take(1).uppercase(), contact.name) { viewModel.openContact(contact) }
                        }
                    }
                    if (settingsHits.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "settings-header") { SectionTitle("Settings") }
                        items(settingsHits, key = { "setting-${it.action}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                            ResultRow("⚙", entry.title) { viewModel.openSettingsEntry(entry) }
                        }
                    }
                    if (searching && settings.webSearch) {
                        items(suggestions, key = { "suggest-$it" }, span = { GridItemSpan(maxLineSpan) }) { text ->
                            ResultRow("🔍", text) { viewModel.searchWeb(text) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "web") {
                            ResultRow("↗", "Search ${settings.searchProvider.label} for “${query.trim()}”") {
                                viewModel.searchWeb(query.trim())
                            }
                        }
                    }
                }
                if (settings.drawerScroller && !searching && !byCategory && settings.drawerSort == DrawerSort.AToZ) {
                    FastScroller(
                        entries = entries.map { it.label },
                        headerCount = headerCount,
                        gridState = gridState,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(
                            bottom = bottomInsets.asPaddingValues().calculateBottomPadding(),
                        ),
                    )
                }
            }
        }
    }

    }

    openFolderId?.let { id ->
        val folder = drawerFolders[id]
        if (folder == null) openFolderId = null
        else DrawerFolderDialog(folder, apps.associateBy { it.id }, viewModel, settings) { openFolderId = null }
    }
}

@Composable
private fun TabRow(selected: DrawerTab, onSelect: (DrawerTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DrawerTab.entries.forEach { tab ->
            val on = tab == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(if (on) scheme.primary else scheme.surfaceContainerHigh, CircleShape)
                    .clip(CircleShape)
                    .clickable { onSelect(tab) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (tab == DrawerTab.Personal) Icons.Outlined.Person else Icons.Outlined.BusinessCenter, null,
                    tint = if (on) scheme.onPrimary else scheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
                )
                Text(
                    tab.name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium,
                    color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WorkBanner(paused: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (paused) "Work profile is off" else "Work profile is on", Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onToggle) { Text(if (paused) "Turn on" else "Pause") }
    }
}

/** A–Z strip on the right edge; touch or drag to jump. */
@Composable
private fun FastScroller(
    entries: List<String>,
    headerCount: Int,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val letters = remember(entries) {
        entries.map { l -> l.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#' }.distinct().sorted()
    }
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf(1f) }
    var active by remember { mutableStateOf<Char?>(null) }
    var touchY by remember { mutableStateOf(0f) }
    val scheme = MaterialTheme.colorScheme

    fun jump(y: Float) {
        if (letters.isEmpty()) return
        val letter = letters[(y / height * letters.size).toInt().coerceIn(0, letters.size - 1)]
        active = letter
        touchY = y
        val index = entries.indexOfFirst { label ->
            val first = label.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (first in 'A'..'Z') first else '#'
            key == letter
        }
        if (index >= 0) scope.launch { gridState.scrollToItem(index + headerCount) }
    }

    Box(modifier.fillMaxHeight(0.8f).width(28.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(28.dp)
                .onSizeChanged { height = it.height.toFloat() }
                .pointerInput(entries) {
                    detectVerticalDragGestures(
                        onDragStart = { jump(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onVerticalDrag = { change, _ -> change.consume(); jump(change.position.y) },
                    )
                }
                .pointerInput(entries) { detectTapGestures(onPress = { jump(it.y); tryAwaitRelease(); active = null }) },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { c ->
                Text(
                    c.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (c == active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        active?.let { c ->
            // A teardrop bubble that follows the finger.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(-44.dp.roundToPx(), (touchY - 30.dp.toPx()).toInt()) }
                    .size(60.dp)
                    .background(scheme.primary, RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, bottomEndPercent = 50, topEndPercent = 8)),
                contentAlignment = Alignment.Center,
            ) { Text(c.toString(), style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimary) }
        }
    }
}

@Composable
private fun DrawerFolderDialog(
    folder: HomeFolder,
    appsById: Map<String, AppInfo>,
    viewModel: LauncherViewModel,
    settings: com.snapkode.nivolauncher.data.LauncherSettings,
    onDismiss: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val latestName by androidx.compose.runtime.rememberUpdatedState(name)
    DisposableEffect(folder.id) { onDispose { viewModel.renameDrawerFolder(folder.id, latestName) } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 400.dp).padding(top = 8.dp)) {
                    items(folder.appIds.mapNotNull(appsById::get), key = { it.id }) { app ->
                        DrawerCell(app, viewModel, settings, isOnHome = viewModel.isOnHome(app.id), folderId = folder.id, emptyMap())
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.deleteDrawerFolder(folder.id); onDismiss() },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Delete folder") }
            }
        }
    }
}

/** Lets the user put an app into an existing drawer folder or start a new one. */
@Composable
private fun FolderChooser(
    app: AppInfo,
    folders: Map<String, HomeFolder>,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to folder") },
        text = {
            Column {
                folders.values.forEach { folder ->
                    Text(
                        folder.name,
                        Modifier.fillMaxWidth().clickable { viewModel.addToDrawerFolder(folder.id, app.id); onDismiss() }.padding(vertical = 12.dp),
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = newName, onValueChange = { newName = it.take(24) }, singleLine = true,
                    label = { Text("New folder") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = newName.isNotBlank(),
                onClick = { viewModel.createDrawerFolder(newName, app.id); onDismiss() },
            ) { Text("Create") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).focusRequester(focusRequester),
        placeholder = { Text("Search your phone and more", style = MaterialTheme.typography.bodyLarge) },
        textStyle = MaterialTheme.typography.bodyLarge,
        leadingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            else Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            } else Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerCell(
    app: AppInfo,
    viewModel: LauncherViewModel,
    settings: com.snapkode.nivolauncher.data.LauncherSettings,
    isOnHome: Boolean,
    folderId: String?,
    folders: Map<String, HomeFolder>,
    list: Boolean = false,
) {
    var choosing by remember { mutableStateOf(false) }
    val drag = LocalDragState.current ?: remember { DragState() }
    val selection = LocalSelection.current
    val extras = buildList {
        if (selection != null) add(MenuAction("Select") { selection.start(app.id, SelectionSource.Drawer) })
        if (!isOnHome) add(MenuAction("Add to Home") { viewModel.addToHome(app.id) })
        if (folderId != null) add(MenuAction("Remove from folder") { viewModel.removeFromDrawerFolder(app.id) })
        else add(MenuAction("Add to folder…") { choosing = true })
        add(
            if (app.id in settings.favorites) MenuAction("Remove from favourites") { viewModel.toggleFavorite(app.id) }
            else MenuAction("Add to favourites") { viewModel.toggleFavorite(app.id) }
        )
        add(MenuAction("Hide app") { viewModel.setHidden(app.id, true) })
    }
    // Hold to drag the app onto a home page; let go without moving for the menu.
    DragCell(
        id = app.id,
        drag = drag,
        modifier = Modifier.fillMaxWidth().padding(vertical = settings.drawerCellPadding.dp),
        description = app.label,
        fromDrawer = true,
        onTap = { viewModel.launch(app) },
        menu = { expanded, dismiss -> AppMenu(expanded, app, viewModel, extras, dismiss) },
    ) {
        if (list) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(app.icon, app.label, Modifier.size((44 * settings.drawerIconScale).dp))
                Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            AppIconView(app, settings.drawerIconScale, showLabel = settings.showDrawerLabels, onWallpaper = false)
        }
    }
    if (choosing) FolderChooser(app, viewModel.drawerFolders.collectAsState().value, viewModel) { choosing = false }
}

/** Width of one cell in a favourites row, so rows line up with the main grid. */
@Composable
private fun maxStripCellWidth(columns: Int): androidx.compose.ui.unit.Dp {
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    return (screen - 16.dp) / columns
}

@Composable
private fun SectionTitle(text: String) {
    SectionLabel(text, Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun ResultRow(badge: String, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(badge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(title, Modifier.padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Card-based results: apps strip, contacts, settings, web. Everything searched stays on the device. */
@Composable
private fun SearchResults(
    apps: List<AppInfo>,
    contacts: List<ContactResult>,
    settingsHits: List<SearchTools.SettingsEntry>,
    suggestions: List<String>,
    webLabel: String?,
    viewModel: LauncherViewModel,
    onWeb: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(28.dp)
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (apps.isNotEmpty()) item(key = "apps") {
            Row(
                Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, cardShape).padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                apps.take(4).forEach { app ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { viewModel.launch(app) }, contentAlignment = Alignment.Center) {
                        AppIconView(app, 1f, showLabel = true, onWallpaper = false)
                    }
                }
                repeat(4 - apps.take(4).size) { Box(Modifier.weight(1f)) }
            }
        }
        if (apps.size > 4) item(key = "more-apps") {
            Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, cardShape).padding(vertical = 8.dp)) {
                apps.drop(4).forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.launch(app) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.Image(app.icon, null, Modifier.size(40.dp))
                        Text(app.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (contacts.isNotEmpty()) item(key = "contacts") {
            Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, cardShape).padding(vertical = 8.dp)) {
                contacts.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.openContact(c) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).background(scheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Text(c.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = scheme.onTertiaryContainer)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Contact", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        Box(Modifier.size(52.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Call, "Open ${c.name}", tint = scheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
        if (settingsHits.isNotEmpty()) item(key = "settings") {
            Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, cardShape).padding(vertical = 8.dp)) {
                settingsHits.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.openSettingsEntry(entry) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).background(scheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Tune, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text("Settings", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (suggestions.isNotEmpty() || webLabel != null) item(key = "web") {
            Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, cardShape).padding(vertical = 8.dp)) {
                suggestions.forEach { text -> ResultRow("🔍", text) { viewModel.searchWeb(text) } }
                if (webLabel != null) ResultRow("↗", webLabel, onWeb)
            }
        }
        item(key = "footer") {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text("Results from this device only", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
