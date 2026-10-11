package com.snapkode.nivolauncher.ui

import com.snapkode.nivolauncher.ui.design.NivoShapes
import com.snapkode.nivolauncher.ui.design.NivoSpace
import com.snapkode.nivolauncher.ui.design.LocalMotion
import com.snapkode.nivolauncher.ui.design.NivoMark
import com.snapkode.nivolauncher.ui.design.pressFeedback
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.snapkode.nivolauncher.ui.design.nivoCard
import com.snapkode.nivolauncher.LauncherViewModel
import com.snapkode.nivolauncher.LockScreenService
import com.snapkode.nivolauncher.data.*
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch

private data class Category(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val keywords: String,
    val summary: (LauncherSettings) -> String,
)

private val categories = listOf(
    Category("home", "Home screen", Icons.Default.Home, "grid columns rows padding labels wallpaper status bar glance page lock") {
        "${it.columns}×${it.rows} grid · icons ${(it.iconScale * 100).toInt()}%"
    },
    Category("dock", "Dock", Icons.Default.Dashboard, "dock pages opacity radius search labels") {
        if (it.showDock) "${it.dockSlots} icons · ${it.dockPages} page${if (it.dockPages > 1) "s" else ""}" else "Hidden"
    },
    Category("folders", "Folders", Icons.Default.Folder, "folder shape colour opacity columns") { "${it.folderShape.label} · ${it.folderColumns} columns" },
    Category("library", "App Library", Icons.Default.GridView, "library categories classify organise page") {
        if (it.libraryPage) "Page right of Home" else "Off"
    },
    Category("drawer", "App drawer", Icons.Default.Apps, "drawer sort scroller columns search hidden keyboard suggested") {
        "${it.drawerColumns} columns · ${it.drawerSort.label}"
    },
    Category("icons", "Icons", Icons.Default.Category, "icon shape pack themed monochrome legacy reload") {
        "${it.iconShape.label}${if (it.themedIcons) " · themed" else ""}"
    },
    Category("wallpaper", "Dynamic wallpaper", Icons.Default.Wallpaper, "wallpaper morning afternoon evening night time of day rotate background") { "Changes with the time of day" },
    Category("colours", "Colours & fonts", Icons.Default.Palette, "theme dark light accent colour material font") {
        "${it.themeMode.name} · ${when (it.colorStyle) { ColorStyle.Custom -> it.accent.label; ColorStyle.Nivo -> "Nivo ${it.accent.label}"; ColorStyle.Dynamic -> "wallpaper colours" }}"
    },
    Category("motion", "Motion & haptics", Icons.Default.Animation, "motion animation reduced smooth snappy calm haptic vibration") {
        "${it.motionStyle.label}${if (it.hapticsEnabled) " · haptics" else ""}"
    },
    Category("gestures", "Gestures", Icons.Default.Gesture, "swipe double tap home button lock notifications") { "Swipe, tap and home button" },
    Category("search", "Search", Icons.Default.Search, "search contacts web provider fuzzy suggestions settings") { "${it.searchProvider.label}${if (it.contactsSearch) " · contacts" else ""}" },
    Category("widgets", "Widgets", Icons.Default.Widgets, "widgets rounded corners") { if (it.roundedWidgets) "Rounded corners" else "Square corners" },
    Category("badges", "Notification badges", Icons.Default.Notifications, "notification dots count badge colour access") {
        if (it.notificationDots) (if (it.notificationCount) "Counts" else "Dots") else "Off"
    },
    Category("backup", "Backup & system", Icons.Default.Backup, "backup restore default home wallpaper accessibility") { "Back up, restore, defaults" },
)

@Composable
fun SettingsScreen(viewModel: LauncherViewModel, initialRoute: String? = null, onClose: () -> Unit) {
    var route by remember { mutableStateOf(initialRoute) }
    val motion = LocalMotion.current
    BackHandler {
        when (route) {
            null -> onClose()
            "hidden" -> route = "drawer"
            else -> route = null
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // Keep lines readable on tablets and in landscape by capping the content width.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = targetState != null && initialState == null || targetState == "hidden"
                val ms = motion.duration(280)
                if (motion.reduced) fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                else (slideInHorizontally(tween(ms)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(ms))) togetherWith
                    (slideOutHorizontally(tween(ms)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(ms * 3 / 5)))
            },
            label = "settingsRoute",
        ) { current ->
            when {
                current == null -> Dashboard(viewModel, onClose, open = { route = it })
                current == "hidden" -> PageScaffold("Hidden apps", onBack = { route = "drawer" }, preview = null) { HiddenApps(viewModel) }
                else -> {
                    val category = categories.first { it.id == current }
                    CategoryPage(category, viewModel, onBack = { route = null }, openHidden = { route = "hidden" })
                }
            }
        }
        }
        }
    }
}

private val lookIds = listOf("colours", "wallpaper", "home", "dock", "icons", "folders")
private val behaviourIds = listOf("drawer", "library", "gestures", "search", "motion", "widgets", "badges")

@Composable
private fun Dashboard(viewModel: LauncherViewModel, onClose: () -> Unit, open: (String) -> Unit) {
    val settings by viewModel.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val searching = query.isNotBlank()
    val visible = categories.filter {
        !searching || it.title.contains(query, true) || it.keywords.contains(query.trim(), true)
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = NivoSpace.l, end = NivoSpace.l, bottom = NivoSpace.xxl),
    ) {
        item {
            Column {
                IconButton(onClick = onClose, Modifier.padding(top = NivoSpace.s)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    "Settings", style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = NivoSpace.s, top = NivoSpace.xl, bottom = NivoSpace.l).semantics { heading() },
                )
            }
        }
        item {
            TextField(
                value = query, onValueChange = { query = it },
                // Min height, not a fixed one, so large system font sizes never clip the text.
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }
                } else null,
                singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainer, unfocusedContainerColor = scheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        if (!searching) {
            item {
                // Identity card: the accent this launcher is currently drawing from.
                Row(
                    Modifier.fillMaxWidth().padding(top = NivoSpace.l)
                        .clip(NivoShapes.Card)
                        .background(scheme.surfaceContainer)
                        // A faint wash of the accent from the mark's side: the card is about the colours you chose.
                        .background(Brush.horizontalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), Color.Transparent)))
                        .border(1.dp, scheme.outlineVariant, NivoShapes.Card)
                        .padding(20.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The brand mark, as on the app icon: the one place in Settings that says whose launcher this is.
                    NivoMark(56.dp)
                    Column(Modifier.weight(1f).padding(horizontal = NivoSpace.l)) {
                        Text("Nivo Launcher", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${settings.accent.label} accent · ${settings.clockStyle.label} clock",
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.size(14.dp).background(scheme.primary, CircleShape))
                }
            }
            item {
                Column(Modifier.padding(top = NivoSpace.m), verticalArrangement = Arrangement.spacedBy(NivoSpace.s)) {
                    QuickToggle(
                        "Themed icons", "Tint app icons with wallpaper colours", Icons.Default.Contrast,
                        settings.themedIcons, NivoShapes.GroupSingle,
                    ) { v -> viewModel.updateSettings { it.copy(themedIcons = v) } }
                    QuickToggle(
                        "Library page", "Auto-sorted categories right of Home", Icons.Default.GridView,
                        settings.libraryPage, NivoShapes.GroupSingle,
                    ) { v -> viewModel.updateSettings { it.copy(libraryPage = v) } }
                }
            }
        }
        val sections = if (searching) listOf(null to visible) else listOf(
            "Look" to visible.filter { it.id in lookIds }.sortedBy { lookIds.indexOf(it.id) },
            "Behaviour" to visible.filter { it.id in behaviourIds }.sortedBy { behaviourIds.indexOf(it.id) },
            "System" to visible.filter { it.id !in lookIds && it.id !in behaviourIds },
        )
        sections.forEach { (title, list) ->
            if (list.isEmpty()) return@forEach
            if (title != null) item(key = "h-$title") {
                SectionLabel(title, Modifier.padding(start = NivoSpace.s, top = 28.dp, bottom = NivoSpace.s))
            } else item(key = "h-top") { Spacer(Modifier.height(NivoSpace.l)) }
            itemsIndexed(list, key = { _, c -> c.id }) { index, category ->
                CategoryRow(category, settings, index, list.size, NivoShapes.group(index, list.size)) { open(category.id) }
                Spacer(Modifier.height(NivoSpace.s))
            }
        }
        if (visible.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xxl, vertical = NivoSpace.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(64.dp).background(scheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(30.dp), tint = scheme.onSurfaceVariant)
                    }
                    Text(
                        "No settings match “${query.trim()}”", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = NivoSpace.l),
                    )
                    Text(
                        "Try a simpler word, like “grid”, “dock” or “font”.", style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = NivoSpace.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToggle(title: String, subtitle: String, icon: ImageVector, checked: Boolean, shape: androidx.compose.ui.graphics.Shape, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressFeedback(press).nivoCard(shape, scheme.surfaceContainer, scheme.outlineVariant)
            .toggleable(value = checked, interactionSource = press, indication = ripple(), role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 72.dp)
            .padding(horizontal = NivoSpace.l, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(if (checked) scheme.primaryContainer else scheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (checked) scheme.onPrimaryContainer else scheme.primary)
        }
        Column(Modifier.weight(1f).padding(horizontal = NivoSpace.l)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = null,
            thumbContent = if (checked) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = scheme.primaryContainer, checkedIconColor = scheme.onPrimaryContainer,
            ),
        )
    }
}

/** One stacked list row: tinted icon disc, title, summary. */
@Composable
private fun CategoryRow(category: Category, settings: LauncherSettings, index: Int, count: Int, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressFeedback(press).nivoCard(shape, scheme.surfaceContainer, scheme.outlineVariant)
            .clickable(interactionSource = press, indication = ripple(), role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(start = NivoSpace.l, end = NivoSpace.m, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(scheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(category.icon, null, tint = scheme.primary) }
        Column(Modifier.weight(1f).padding(horizontal = NivoSpace.l)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium)
            Text(category.summary(settings), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Chevron: this row leads somewhere.
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

/** Small "live preview" tag so people know the picture above responds to their changes. */
@Composable
private fun LivePill(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = NivoSpace.m, vertical = NivoSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text("Live preview", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryCard(category: Category, settings: LauncherSettings, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxHeight().padding(16.dp).heightIn(min = 120.dp)) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(category.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(12.dp))
            Text(category.title, style = MaterialTheme.typography.titleMedium)
            Text(
                category.summary(settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    preview: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = NivoSpace.xs, vertical = NivoSpace.s), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = NivoSpace.xs, end = NivoSpace.l).semantics { heading() },
            )
        }
        if (preview != null) {
            // Pinned: stays visible while the settings below scroll, so every change is seen live.
            Box(
                Modifier.fillMaxWidth().padding(top = NivoSpace.xs, bottom = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    preview()
                    LivePill(Modifier.padding(top = NivoSpace.m))
                }
            }
        }
        // A hairline fades in once the list slides under the pinned header, so the edge reads as an edge.
        val scrolled by remember { derivedStateOf { scroll.value > 0 } }
        val edge by animateFloatAsState(if (scrolled) 1f else 0f, tween(LocalMotion.current.duration(180)), label = "settingsEdge")
        Box(Modifier.fillMaxWidth().height(1.dp).alpha(edge).background(MaterialTheme.colorScheme.outlineVariant))
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(top = NivoSpace.xs, bottom = NivoSpace.xl), content = content)
    }
}

@Composable
private fun CategoryPage(category: Category, viewModel: LauncherViewModel, onBack: () -> Unit, openHidden: () -> Unit) {
    val s by viewModel.settings.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val groups by viewModel.libraryGroups.collectAsState()
    val sampleFolder = remember(apps) { HomeFolder("preview", "Folder", apps.drop(2).take(6).map { it.id }) }
    fun update(block: (LauncherSettings) -> LauncherSettings) = viewModel.updateSettings(block)

    val preview: (@Composable () -> Unit)? = when (category.id) {
        "home", "dock" -> { { HomeIllustration(s) } }
        "folders" -> { { FolderPreview(s, apps, sampleFolder, height = 250.dp) } }
        "drawer" -> { { DrawerPreview(s, apps, height = 250.dp) } }
        "library" -> { { LibraryPreview(groups) } }
        "motion" -> { { MotionPreview() } }
        "icons" -> { { IconsPreview(s, apps) } }
        "colours" -> { { ThemePreview() } }
        "gestures" -> { { GesturePreview(s) } }
        "search" -> { { SearchPreview(s, apps) } }
        "widgets" -> { { WidgetPreview(s) } }
        "badges" -> { { BadgePreview(s, apps) } }
        else -> null
    }

    PageScaffold(category.title, onBack, preview) {
        when (category.id) {
            "home" -> HomeSettings(s, viewModel, ::update)
            "dock" -> DockSettings(s, ::update)
            "folders" -> FolderSettings(s, ::update)
            "drawer" -> DrawerSettings(s, ::update, openHidden)
            "library" -> LibrarySettings(s, viewModel, ::update)
            "motion" -> MotionSettings(s, ::update)
            "icons" -> IconSettings(s, viewModel, ::update)
            "colours" -> ColourSettings(s, viewModel, ::update)
            "gestures" -> GestureSettings(s, viewModel, ::update)
            "search" -> SearchSettings(s, ::update)
            "widgets" -> SettingsCard("Widgets") {
                ToggleRow("Rounded widget corners", s.roundedWidgets, "Clip widgets to a rounded rectangle") { v -> update { it.copy(roundedWidgets = v) } }
                InfoRow("Add or manage widgets", "Press and hold an empty spot on the home screen, then choose Widgets", Icons.Default.Widgets)
            }
            "badges" -> BadgeSettings(s, viewModel, ::update)
            "wallpaper" -> DynamicWallpaperSettings(viewModel)
            "backup" -> BackupSettings(viewModel)
        }
    }
}

@Composable
internal fun PlaceSearchDialog(
    viewModel: LauncherViewModel,
    onPick: (Place) -> Unit = { viewModel.chooseWeatherPlace(it) },
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun search() {
        if (query.isBlank()) return
        busy = true
        scope.launch { results = viewModel.searchPlaces(query.trim()); busy = false }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Choose a city") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, label = { Text("City name") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                )
                if (busy) Text("Searching…", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                results?.let { list ->
                    if (list.isEmpty() && !busy) Text("No matches (or no internet).", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { place ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(place); onDone() }.padding(vertical = 10.dp)) {
                            Text(place.name, style = MaterialTheme.typography.bodyLarge)
                            if (place.detail.isNotEmpty()) Text(place.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { search() }) { Text("Search") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(title: String, minutes: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = "%02d:%02d".format(minutes / 60, minutes % 60)
    ActionRow(title, label) { open = true }
    if (open) {
        val state = androidx.compose.material3.rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { androidx.compose.material3.TimePicker(state = state) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onPick(state.hour * 60 + state.minute); open = false }) { Text("Set") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

private typealias Update = ((LauncherSettings) -> LauncherSettings) -> Unit

@Composable
private fun ColumnScope.HomeSettings(s: LauncherSettings, viewModel: LauncherViewModel, update: Update) {
    PageletSettings(viewModel)
    SettingsCard("Grid") {
        IntSliderRow("Columns", s.columns, 3..6) { v -> update { it.copy(columns = v) } }
        IntSliderRow("Rows", s.rows, 4..7) { v -> update { it.copy(rows = v) } }
    }
    SettingsCard("Clock") {
        ChipPicker("Style", ClockStyle.entries, s.clockStyle, { it.label }) { v -> update { it.copy(clockStyle = v) } }
        if (s.clockStyle != ClockStyle.Off) {
            ToggleRow("Greeting", s.clockGreeting, "“Good morning” above the time") { v -> update { it.copy(clockGreeting = v) } }
            if (s.clockStyle != ClockStyle.Compact) {
                ToggleRow("Show seconds", s.clockSeconds, "Updates every second, so uses a little more battery") { v -> update { it.copy(clockSeconds = v) } }
            }
        }
    }
    SettingsCard("Icons and labels") {
        PercentSliderRow("Icon size", s.iconScale, 0.7f..1.4f) { v -> update { it.copy(iconScale = v) } }
        SliderRow("Label size", "${s.labelSize.toInt()} sp", s.labelSize, 9f..16f) { v -> update { it.copy(labelSize = v) } }
        ToggleRow("Show labels", s.showLabels) { v -> update { it.copy(showLabels = v) } }
        ToggleRow("Dark text", s.darkHomeText, "For light wallpapers") { v -> update { it.copy(darkHomeText = v) } }
    }
    SettingsCard("Layout") {
        IntSliderRow("Horizontal padding", s.homePaddingH, 0..32, "dp") { v -> update { it.copy(homePaddingH = v) } }
        IntSliderRow("Vertical padding", s.homePaddingV, 0..32, "dp") { v -> update { it.copy(homePaddingV = v) } }
        ToggleRow("Page indicator", s.showPageDots) { v -> update { it.copy(showPageDots = v) } }
        OptionRow("Default page", s.defaultPage, listOf(0, 1, 2, 3, 4), { "Page ${it + 1}" }) { v -> update { it.copy(defaultPage = v) } }
        ToggleRow("Lock home screen", s.lockHome, "Stops icons and widgets being moved or removed") { v -> update { it.copy(lockHome = v) } }
        ToggleRow("Scroll wallpaper with pages", s.wallpaperScroll) { v -> update { it.copy(wallpaperScroll = v) } }
        ToggleRow("Readability shading", s.readabilityScrim, "Soft shading behind the clock and dock on bright wallpapers") { v -> update { it.copy(readabilityScrim = v) } }
    }
    SettingsCard("Status bar") {
        ToggleRow("Hide status bar", s.hideStatusBar, "Swipe from the top to peek") { v -> update { it.copy(hideStatusBar = v) } }
        ToggleRow("Dark status bar icons", s.darkStatusIcons, "For light wallpapers") { v -> update { it.copy(darkStatusIcons = v) } }
    }
    SettingsCard("At a glance") {
        ToggleRow("Show at top of home screen", s.smartspace, "Date, next event and battery") { v -> update { it.copy(smartspace = v) } }
        if (s.smartspace) {
            val ctx = LocalContext.current
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                update { it.copy(smartspaceCalendar = granted) }
            }
            ToggleRow("Next calendar event", s.smartspaceCalendar, "Needs calendar permission") { want ->
                when {
                    !want -> update { it.copy(smartspaceCalendar = false) }
                    CalendarSource.hasPermission(ctx) -> update { it.copy(smartspaceCalendar = true) }
                    else -> ask.launch(android.Manifest.permission.READ_CALENDAR)
                }
            }
            ToggleRow("Battery when charging or low", s.smartspaceBattery) { v -> update { it.copy(smartspaceBattery = v) } }
            var choosingPlace by remember { mutableStateOf(false) }
            ToggleRow(
                "Weather", s.weatherEnabled && s.weatherPlace.isNotEmpty(),
                "Shows current conditions for a city you choose. Contacts open-meteo.com; only the rounded coordinates are sent.",
            ) { want ->
                if (!want) update { it.copy(weatherEnabled = false) } else choosingPlace = true
            }
            if (s.weatherEnabled && s.weatherPlace.isNotEmpty()) {
                ActionRow("City", s.weatherPlace, Icons.Default.Place) { choosingPlace = true }
                ToggleRow("Use Fahrenheit", s.weatherFahrenheit) { v -> update { it.copy(weatherFahrenheit = v) } }
            }
            if (choosingPlace) PlaceSearchDialog(viewModel) { choosingPlace = false }
            ToggleRow("Now playing", s.smartspaceMedia, "Music controls at the top of home. Needs notification access (see Notification badges)") { v ->
                update { it.copy(smartspaceMedia = v) }
            }
        }
    }
}

/** Every Pagelet on the home screen, with its settings, and a way to add another. */
@Composable
private fun ColumnScope.PageletSettings(viewModel: LauncherViewModel) {
    val items by viewModel.items.collectAsState()
    val configs by viewModel.pagelets.collectAsState()
    val homeLocked by viewModel.settings.collectAsState()
    val pagelets = items.filter { com.snapkode.nivolauncher.pagelet.isPageletId(it.id) }.sortedBy { it.page }
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    confirmRemove?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove this Pagelet?") },
            text = { Text("Its page and settings are removed from your home screen.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.removePagelet(id); viewModel.clearPageletUndo(); confirmRemove = null; editing = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
    SettingsCard("Pagelets") {
        if (pagelets.isEmpty()) {
            Text(
                "A Pagelet is a widget that fills a whole home page: the day under a living sky, or the headlines you follow.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        pagelets.forEach { item ->
            val type = com.snapkode.nivolauncher.pagelet.pageletTypeOf(item.id) ?: return@forEach
            val cfg = configs[item.id] ?: com.snapkode.nivolauncher.pagelet.PageletConfig.defaultsFor(type)
            ActionRow(
                "${type.title} Pagelet",
                "Page ${item.page + 1} · " + (if (type == com.snapkode.nivolauncher.pagelet.PageletType.HEADLINES) com.snapkode.nivolauncher.data.news.NewsCatalog.summary(cfg.news) + " · " else "") + if (cfg.hideDock) "uses the whole page" else "dock stays",
                if (type == com.snapkode.nivolauncher.pagelet.PageletType.HEADLINES) Icons.AutoMirrored.Filled.Article else Icons.Outlined.AutoAwesome,
            ) { editing = item.id }
        }
        if (homeLocked.lockHome) InfoRow("Add a Pagelet", "Unlock the home screen layout to add one", Icons.Default.Add)
        else if (viewModel.addablePagelets().isEmpty()) InfoRow("Add a Pagelet", "Every Pagelet is already on your home screen", Icons.Default.Add)
        else ActionRow("Add a Pagelet", "Fills a whole home page", Icons.Default.Add) { adding = true }
    }
    editing?.let { id ->
        val type = com.snapkode.nivolauncher.pagelet.pageletTypeOf(id)
        if (type == null || pagelets.none { it.id == id }) editing = null else
            com.snapkode.nivolauncher.ui.pagelet.PageletOptionsSheet(
                type = type, config = configs[id] ?: com.snapkode.nivolauncher.pagelet.PageletConfig.defaultsFor(type),
                onChange = { c -> viewModel.updatePagelet(id) { c } },
                onDismiss = { editing = null },
                weather = { com.snapkode.nivolauncher.ui.pagelet.WeatherLocationCard(viewModel) },
                checkFeed = { viewModel.checkFeed(it) },
                // Home (and its Undo) is hidden behind Settings, so removing from here asks first.
                onRemove = if (homeLocked.lockHome) null else ({ confirmRemove = id }),
            )
    }
    if (adding) com.snapkode.nivolauncher.ui.pagelet.PageletGallerySheet(
        onAdd = { type, cfg ->
            adding = false
            if (viewModel.addPagelet(type, cfg) == null) android.widget.Toast.makeText(context, if (homeLocked.lockHome) "Home layout is locked" else "Couldn't add the Pagelet", android.widget.Toast.LENGTH_SHORT).show()
        },
        onDismiss = { adding = false },
        checkFeed = { viewModel.checkFeed(it) },
        taken = com.snapkode.nivolauncher.pagelet.PageletType.entries.filterTo(HashSet()) { viewModel.hasPagelet(it) },
    )
}

@Composable
private fun ColumnScope.DockSettings(s: LauncherSettings, update: Update) {
    SettingsCard("Dock") {
        ToggleRow("Show dock", s.showDock) { v -> update { it.copy(showDock = v) } }
        if (s.showDock) {
            ChipPicker("Style", DockStyle.entries, s.dockStyle, { it.label }) { v -> update { it.copy(dockStyle = v) } }
            IntSliderRow("Icons per page", s.dockSlots, 3..6) { v -> update { it.copy(dockSlots = v) } }
            IntSliderRow("Pages", s.dockPages, 1..3) { v -> update { it.copy(dockPages = v) } }
            ToggleRow("Labels", s.dockLabels) { v -> update { it.copy(dockLabels = v) } }
        }
    }
    if (s.showDock) {
        SettingsCard("Appearance") {
            SwatchPicker("Colour", tintOptions(), s.dockTint) { v -> update { it.copy(dockTint = v) } }
            PercentSliderRow("Opacity", s.dockOpacity) { v -> update { it.copy(dockOpacity = v) } }
            IntSliderRow("Corner radius", s.dockRadius, 0..40, "dp") { v -> update { it.copy(dockRadius = v) } }
            IntSliderRow("Bottom spacing", s.dockBottomSpace, 0..32, "dp") { v -> update { it.copy(dockBottomSpace = v) } }
        }
    }
}

@Composable
private fun tintOptions(): List<Pair<Tint, Color>> =
    Tint.entries.map { it to (if (it == Tint.Black) Color(0xFF1B1B1F) else it.color()) }

@Composable
private fun ColumnScope.FolderSettings(s: LauncherSettings, update: Update) {
    SettingsCard("Closed folder") {
        ShapePicker("Shape", s.folderShape) { v -> update { it.copy(folderShape = v) } }
        SwatchPicker("Colour", tintOptions(), s.folderTint) { v -> update { it.copy(folderTint = v) } }
        PercentSliderRow("Opacity", s.folderTileOpacity) { v -> update { it.copy(folderTileOpacity = v) } }
    }
    SettingsCard("Open folder") {
        PercentSliderRow("Background opacity", s.folderBgOpacity) { v -> update { it.copy(folderBgOpacity = v) } }
        IntSliderRow("Columns", s.folderColumns, 2..5) { v -> update { it.copy(folderColumns = v) } }
        ToggleRow("Labels", s.showFolderLabels) { v -> update { it.copy(showFolderLabels = v) } }
    }
}

@Composable
private fun ColumnScope.DrawerSettings(s: LauncherSettings, update: Update, openHidden: () -> Unit) {
    SettingsCard("Order") {
        ChipPicker("Layout", DrawerMode.entries, s.drawerMode, { it.label }) { v -> update { it.copy(drawerMode = v) } }
        ChipPicker("Sort apps", DrawerSort.entries, s.drawerSort, { it.label }) { v -> update { it.copy(drawerSort = v) } }
        ToggleRow("A–Z fast scroller", s.drawerScroller) { v -> update { it.copy(drawerScroller = v) } }
        InfoRow("Favourites", "${s.favorites.size} pinned. Press and hold an app in the drawer, then choose Add to favourites", Icons.Default.Star)
        ToggleRow("Suggested apps", s.predictions, "Your most-used apps at the top") { v -> update { it.copy(predictions = v) } }
    }
    SettingsCard("Grid") {
        IntSliderRow("Columns", s.drawerColumns, 3..6) { v -> update { it.copy(drawerColumns = v) } }
        PercentSliderRow("Icon size", s.drawerIconScale, 0.7f..1.4f) { v -> update { it.copy(drawerIconScale = v) } }
        SliderRow("Label size", "${s.drawerLabelSize.toInt()} sp", s.drawerLabelSize, 9f..16f) { v -> update { it.copy(drawerLabelSize = v) } }
        ToggleRow("Show labels", s.showDrawerLabels) { v -> update { it.copy(showDrawerLabels = v) } }
        IntSliderRow("Row spacing", s.drawerCellPadding, 0..24, "dp") { v -> update { it.copy(drawerCellPadding = v) } }
        IntSliderRow("Side margin", s.drawerSideMargin, 0..32, "dp") { v -> update { it.copy(drawerSideMargin = v) } }
        IntSliderRow("Top padding", s.drawerTopPadding, 0..48, "dp") { v -> update { it.copy(drawerTopPadding = v) } }
        PercentSliderRow("Background opacity", s.drawerOpacity) { v -> update { it.copy(drawerOpacity = v) } }
    }
    SettingsCard("Search bar") {
        ToggleRow("Hide search bar", s.hideDrawerSearch) { v -> update { it.copy(hideDrawerSearch = v) } }
        ToggleRow("Show keyboard on open", s.autoShowKeyboard) { v -> update { it.copy(autoShowKeyboard = v) } }
    }
    SettingsCard("Apps") {
        ActionRow("Hidden apps", "${s.hiddenApps.size} hidden", Icons.Default.VisibilityOff, openHidden)
    }
}

@Composable
private fun ColumnScope.LibrarySettings(s: LauncherSettings, viewModel: LauncherViewModel, update: Update) {
    val config by viewModel.libraryConfig.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset categories?") },
            text = { Text("Moved apps go back to their automatic category, and custom or hidden categories are removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetLibrary(); confirmReset = false }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    SettingsCard("App Library") {
        ToggleRow("Show the App Library page", s.libraryPage, "A page just right of Home, with every app sorted into a category") { v -> update { it.copy(libraryPage = v) } }
    }
    SettingsCard("Categories") {
        ActionRow(
            "Reset categories",
            "${config.overrides.size} moved apps · ${config.custom.size} custom · ${config.hidden.size} hidden",
            Icons.Default.Refresh,
        ) { confirmReset = true }
        InfoRow("How to edit", "Press and hold a category card to rename, move or hide it. Press and hold an app inside to move it to another category.", Icons.Default.Info)
    }
}

@Composable
private fun ColumnScope.MotionSettings(s: LauncherSettings, update: Update) {
    SettingsCard("Feel") {
        ChipPicker("Motion style", MotionStyle.entries, s.motionStyle, { it.label }) { v -> update { it.copy(motionStyle = v) } }
        Text(
            when (s.motionStyle) {
                MotionStyle.Default -> "Balanced and natural."
                MotionStyle.Smooth -> "Softer, longer settles."
                MotionStyle.Snappy -> "Quick and direct."
                MotionStyle.Calm -> "Slow and gentle, no page parallax."
                MotionStyle.Reduced -> "Short fades only. No bouncing, scaling or staggering."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
    SettingsCard("Haptics") {
        ToggleRow("Haptic feedback", s.hapticsEnabled, "A light tap when you pick an icon up") { v -> update { it.copy(hapticsEnabled = v) } }
    }
    SettingsCard("Accessibility") {
        InfoRow("System animations", "Turning animations off in Android's accessibility or developer settings switches Nivo to Reduced motion automatically.", Icons.Default.Info)
    }
}

@Composable
private fun ColumnScope.IconSettings(s: LauncherSettings, viewModel: LauncherViewModel, update: Update) {
    SettingsCard("Shape") {
        ShapePicker("Icon shape", s.iconShape) { v -> update { it.copy(iconShape = v) } }
        ToggleRow("Wrap old-style icons", s.wrapLegacyIcons, "Place square icons on the chosen shape") { v -> update { it.copy(wrapLegacyIcons = v) } }
    }
    SettingsCard("Style") {
        val packs = remember { viewModel.iconPacks() }
        OptionRow(
            "Icon pack", s.iconPack, listOf("") + packs.map { it.packageName },
            { pkg -> packs.firstOrNull { it.packageName == pkg }?.label ?: "System icons" },
        ) { v -> update { it.copy(iconPack = v) } }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ToggleRow("Themed icons", s.themedIcons, "Tint icons with your colours (Android 13+)") { v -> update { it.copy(themedIcons = v) } }
        }
        OptionRow("Label lines", s.labelLines, listOf(1, 2), { "$it" }) { v -> update { it.copy(labelLines = v) } }
        ActionRow("Reload icons", "Re-draw every icon", Icons.Default.Refresh) { viewModel.refreshIcons() }
    }
}

@Composable
private fun ColumnScope.ColourSettings(s: LauncherSettings, viewModel: LauncherViewModel, update: Update) {
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFont(uri)
    }
    SettingsCard("Appearance") {
        ThemeModePicker(s.themeMode) { v -> update { it.copy(themeMode = v) } }
        ChipPicker("Colours", ColorStyle.entries.filter { it != ColorStyle.Dynamic }, s.colorStyle, { it.label.substringBefore(" (") }) { v -> update { it.copy(colorStyle = v) } }
        if (s.colorStyle != ColorStyle.Dynamic) {
            AccentPicker("Accent", s.accent) { v -> update { it.copy(accent = v) } }
        }
    }
    SettingsCard("Schedule") {
        ChipPicker("Switch automatically", ThemeSchedule.entries, s.themeSchedule, { it.label }) { v -> update { it.copy(themeSchedule = v) } }
        if (s.themeSchedule == ThemeSchedule.Custom) {
            TimeRow("Dark from", s.scheduleStart) { m -> update { it.copy(scheduleStart = m) } }
            TimeRow("Light from", s.scheduleEnd) { m -> update { it.copy(scheduleEnd = m) } }
        }
        if (s.themeSchedule != ThemeSchedule.Off) {
            Text(
                "While a schedule is on, it decides light or dark. Your choice above (Dark or AMOLED) sets which dark theme is used at night.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
    SettingsCard("Font") {
        FontPicker(s.fontChoice) { v ->
            if (v != FontChoice.Custom || s.customFontPath.isNotEmpty()) update { it.copy(fontChoice = v) }
            else pickFont.launch(arrayOf("*/*"))
        }
        ActionRow("Choose font file…", "A .ttf or .otf file from your device", Icons.Default.TextFields) { pickFont.launch(arrayOf("*/*")) }
    }
}

@Composable
private fun ColumnScope.GestureSettings(s: LauncherSettings, viewModel: LauncherViewModel, update: Update) {
    val allApps by viewModel.apps.collectAsState()
    @Composable
    fun Gesture(title: String, key: String, current: GestureAction, set: (GestureAction) -> Unit) {
        OptionRow(title, current, GestureAction.entries, { it.label }, set)
        if (current == GestureAction.OpenApp) {
            OptionRow(
                "  App to open", s.gestureApps[key] ?: "", listOf("") + allApps.map { it.id },
                { id -> allApps.firstOrNull { it.id == id }?.label ?: "Choose…" },
            ) { id -> if (id.isNotEmpty()) viewModel.setGestureApp(key, id) }
        }
    }
    SettingsCard("Gestures") {
        Gesture("Swipe down", "swipeDown", s.swipeDown) { v -> update { it.copy(swipeDown = v) } }
        Gesture("Swipe up", "swipeUp", s.swipeUp) { v -> update { it.copy(swipeUp = v) } }
        Gesture("Double tap", "doubleTap", s.doubleTap) { v -> update { it.copy(doubleTap = v) } }
        Gesture("Home button on home screen", "homePress", s.homePress) { v -> update { it.copy(homePress = v) } }
        OptionRow("Press and hold empty space", s.longPressEmpty, EmptyLongPress.entries, { it.label }) { v -> update { it.copy(longPressEmpty = v) } }
    }
    SettingsCard("Lasso selection") {
        ToggleRow(
            "Radial action menu", s.lassoRadialMenu,
            "Show actions in a ring around the selection. The bottom toolbar is used instead when a screen reader is on.",
        ) { v -> update { it.copy(lassoRadialMenu = v) } }
        Text(
            "Draw a loop around icons to select them, then drag them together or arrange, group or remove them. " +
                "Start it from the edit menu, or set a gesture above to “Select icons (lasso)”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    val needsService = listOf(s.swipeDown, s.swipeUp, s.doubleTap, s.homePress).any {
        it == GestureAction.LockScreen || it == GestureAction.Recents
    }
    if (needsService) {
        SettingsCard("Permission") {
            ActionRow(
                "Screen lock service",
                if (LockScreenService.isEnabled) "Enabled" else "Needed to lock the screen or open recent apps. Tap to turn on",
                Icons.Default.Lock,
            ) { viewModel.openAccessibilitySettings() }
        }
    }
}

@Composable
private fun ColumnScope.SearchSettings(s: LauncherSettings, update: Update) {
    val ctx = LocalContext.current
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update { it.copy(contactsSearch = granted) }
    }
    SettingsCard("Where to look") {
        ToggleRow("Settings", s.settingsSearch, "Find system settings like Wi-Fi") { v -> update { it.copy(settingsSearch = v) } }
        ToggleRow("Contacts", s.contactsSearch, "Needs contacts permission") { want ->
            when {
                !want -> update { it.copy(contactsSearch = false) }
                ContactsSearch.hasPermission(ctx) -> update { it.copy(contactsSearch = true) }
                else -> askContacts.launch(android.Manifest.permission.READ_CONTACTS)
            }
        }
        ToggleRow("Fuzzy matching", s.fuzzySearch, "Find apps even with a typo") { v -> update { it.copy(fuzzySearch = v) } }
        ToggleRow("Include hidden apps", s.hiddenAppsInSearch) { v -> update { it.copy(hiddenAppsInSearch = v) } }
    }
    SettingsCard("Web") {
        ToggleRow("Web search", s.webSearch) { v -> update { it.copy(webSearch = v) } }
        OptionRow("Provider", s.searchProvider, SearchProvider.entries, { it.label }) { v -> update { it.copy(searchProvider = v) } }
        if (s.searchProvider == SearchProvider.Custom) {
            var editing by remember { mutableStateOf(false) }
            ActionRow("Custom search URL", s.customSearchUrl.ifBlank { "Use %s where the query goes" }) { editing = true }
            if (editing) {
                var text by remember { mutableStateOf(s.customSearchUrl) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { editing = false },
                    title = { Text("Custom search URL") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = text, onValueChange = { text = it }, singleLine = true,
                            placeholder = { Text("https://example.com/search?q=%s") },
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            update { it.copy(customSearchUrl = text.trim()) }; editing = false
                        }) { Text("Save") }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = false }) { Text("Cancel") } },
                )
            }
        }
        ToggleRow("Live suggestions", s.webSuggestions, "Sends what you type to the provider") { v -> update { it.copy(webSuggestions = v) } }
    }
}

@Composable
private fun ColumnScope.BadgeSettings(s: LauncherSettings, viewModel: LauncherViewModel, update: Update) {
    val ctx = LocalContext.current
    val listenerOn = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    SettingsCard("Access") {
        ActionRow(
            "Notification access",
            if (listenerOn) "Allowed" else "Needed for dots and counts. Tap to allow",
            Icons.Default.Notifications,
        ) { viewModel.openNotificationAccessSettings() }
    }
    SettingsCard("Badges") {
        ToggleRow("Show notification dots", s.notificationDots) { v -> update { it.copy(notificationDots = v) } }
        if (s.notificationDots) {
            ToggleRow("Show count instead of dot", s.notificationCount) { v -> update { it.copy(notificationCount = v) } }
            SwatchPicker("Colour", tintOptions(), s.badgeColor) { v -> update { it.copy(badgeColor = v) } }
        }
    }
}

@Composable
private fun ColumnScope.BackupSettings(viewModel: LauncherViewModel) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }
    SettingsCard("Backup") {
        ActionRow("Back up", "Save your layout, folders and settings to a file", Icons.Default.Backup) { export.launch("nivo-launcher-backup.json") }
        ActionRow("Restore", "Replace everything from a backup file", Icons.Default.Restore) { import.launch(arrayOf("application/json", "*/*")) }
    }
    SettingsCard("Data sources") {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        ActionRow(
            "Weather data: MET Norway",
            "Forecasts for the Today Pagelet. CC BY 4.0 and NLOD 2.0. Tap to read the licence",
            Icons.Default.Info,
        ) { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://api.met.no/doc/License")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    SettingsCard("System") {
        ActionRow("Default home app", "Make Nivo Launcher your home app", Icons.Default.Home) { viewModel.openLauncherSettingsForDefault() }
        ActionRow("Wallpaper", "Change your wallpaper", Icons.Default.Image) { viewModel.pickWallpaper() }
        ActionRow("Replay introduction", "Show the welcome tour again", Icons.Default.Info) { viewModel.replayOnboarding() }
    }
}

@Composable
private fun HiddenApps(viewModel: LauncherViewModel) {
    val apps by viewModel.apps.collectAsState()
    val s by viewModel.settings.collectAsState()
    Text(
        "Hidden apps stay installed but leave the drawer and search. Tick an app to hide it.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = NivoSpace.xs),
    )
    SettingsCard {
        apps.forEach { app ->
            val hidden = app.id in s.hiddenApps
            Row(
                // One checkbox for assistive tech: the whole row toggles.
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = hidden, role = Role.Checkbox) { viewModel.setHidden(app.id, it) }
                    .padding(horizontal = 20.dp, vertical = NivoSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NivoSpace.l),
            ) {
                Image(app.icon, null, Modifier.size(40.dp).alpha(if (hidden) 0.6f else 1f))
                Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Checkbox(checked = hidden, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun ColumnScope.DynamicWallpaperSettings(viewModel: LauncherViewModel) {
    val state by viewModel.dynamicWallpaper.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var picking by remember { mutableStateOf<DayPeriod?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val period = picking
        picking = null
        if (uri != null && period != null) viewModel.setPeriodWallpaper(period, uri)
    }
    val now = DayPeriod.at(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    SettingsCard {
        ToggleRow("Change wallpaper through the day", state.enabled, "Applies the picture you choose for each part of the day") { v ->
            viewModel.setDynamicWallpaperEnabled(v)
        }
    }
    SettingsCard("Pictures") {
        DayPeriod.entries.forEachIndexed { index, period ->
            val version = state.versions[period] ?: 0L
            if (index > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val thumb by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, version) {
                    value = if (version == 0L) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        DynamicWallpaper.thumbnail(context, period)?.asImageBitmap()
                    }
                }
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.size(width = 64.dp, height = 88.dp).clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        .clickable(onClickLabel = "Choose ${period.label} wallpaper", role = Role.Button) { picking = period; gallery.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = thumb
                    if (bitmap != null) androidx.compose.foundation.Image(bitmap, period.label, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Default.Add, "Choose ${period.label} wallpaper", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(period.label, style = MaterialTheme.typography.titleMedium)
                        if (state.enabled && period == now) {
                            Text(
                                "NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(period.range, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.offset(x = (-12).dp)) {
                        TextButton(onClick = { picking = period; gallery.launch("image/*") }) {
                            Text(if (version == 0L) "Choose" else "Change")
                        }
                        if (version != 0L) TextButton(onClick = { viewModel.clearPeriodWallpaper(period) }) { Text("Remove") }
                    }
                }
            }
        }
    }
    Text(
        "Morning starts at 5 AM, afternoon at 12, evening at 5 PM and night at 9 PM. A part of the day with no picture keeps whatever wallpaper is already set.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
    )
}
