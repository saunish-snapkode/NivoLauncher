package com.snapkode.nivolauncher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snapkode.nivolauncher.data.AppClassifier
import com.snapkode.nivolauncher.data.AppInfo
import com.snapkode.nivolauncher.data.LibraryConfig
import com.snapkode.nivolauncher.data.LibraryGroup
import com.snapkode.nivolauncher.data.AppKey
import com.snapkode.nivolauncher.data.AppRepository
import com.snapkode.nivolauncher.data.FOLDER_PREFIX
import com.snapkode.nivolauncher.data.FontChoice
import com.snapkode.nivolauncher.data.GestureAction
import com.snapkode.nivolauncher.data.HomeFolder
import com.snapkode.nivolauncher.data.HomeItem
import com.snapkode.nivolauncher.data.appWidgetIdOf
import com.snapkode.nivolauncher.data.isFolderId
import com.snapkode.nivolauncher.data.isAnyWidgetId
import com.snapkode.nivolauncher.lasso.DropPlan
import com.snapkode.nivolauncher.lasso.Grid
import com.snapkode.nivolauncher.lasso.GroupDropPlanner
import com.snapkode.nivolauncher.lasso.LayoutHistory
import com.snapkode.nivolauncher.lasso.LayoutOps
import com.snapkode.nivolauncher.lasso.LayoutSnapshot
import com.snapkode.nivolauncher.lasso.OpResult
import com.snapkode.nivolauncher.ui.lasso.LassoSession
import com.snapkode.nivolauncher.data.isNivoWidgetId
import com.snapkode.nivolauncher.data.isWidgetId
import com.snapkode.nivolauncher.data.newNivoClockId
import com.snapkode.nivolauncher.data.widgetItemId
import com.snapkode.nivolauncher.data.LauncherSettings
import com.snapkode.nivolauncher.data.LayoutStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import com.snapkode.nivolauncher.pagelet.PageletConfig
import com.snapkode.nivolauncher.pagelet.PageletRepository
import com.snapkode.nivolauncher.pagelet.PageletType
import com.snapkode.nivolauncher.pagelet.isPageletId
import com.snapkode.nivolauncher.pagelet.newPageletId
import com.snapkode.nivolauncher.pagelet.pageletTypeOf

/** Where a dragged item is dropped. */
sealed interface DropTarget {
    data class Cell(val page: Int, val col: Int, val row: Int) : DropTarget
    data class Dock(val slot: Int) : DropTarget
}

private const val WIDGET_HOST_ID = 4242
private const val MAX_PREDICTED = 6
private const val DRAWER_FOLDERS_KEY = "drawerFolders"

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository(application)

    /** True once the installed apps have been read for the first time; until then the loading screen shows. */
    val appsReady: StateFlow<Boolean> get() = repo.loaded
    private val store = LayoutStore(application)
    private val launcherApps = application.getSystemService(LauncherApps::class.java)

    // Declared before `init`: the init block starts collecting at once, and a collector that runs immediately calls
    // refreshWorkState(), which writes these. Declared below, they would still be null at that moment.
    private val _hasWorkProfile = MutableStateFlow(false)
    val hasWorkProfile: StateFlow<Boolean> = _hasWorkProfile.asStateFlow()

    private val _workPaused = MutableStateFlow(false)
    val workPaused: StateFlow<Boolean> = _workPaused.asStateFlow()

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    /** Installed apps with the user's custom labels applied, sorted by displayed name. */
    val apps: StateFlow<List<AppInfo>> = combine(repo.apps, _settings.map { it.labelOverrides }.distinctUntilChanged()) { list, overrides ->
        // Depends only on the rename map, so moving an unrelated slider doesn't re-sort every app.
        if (overrides.isEmpty()) list
        else list.map { app -> overrides[app.id]?.let { app.copy(label = it) } ?: app }
            .sortedBy { it.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _items = MutableStateFlow(store.loadItems().orEmpty())
    val items: StateFlow<List<HomeItem>> = _items.asStateFlow()

    private val _pageCount = MutableStateFlow(store.loadPageCount())

    /** Number of workspace pages: never fewer than the pages that hold items, so layouts migrate cleanly. */
    val pages: StateFlow<Int> = combine(_items, _pageCount) { items, stored ->
        maxOf(1, stored, (items.filter { !it.inDock }.maxOfOrNull { it.page } ?: -1) + 1)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val _folders = MutableStateFlow(store.loadFolders())
    val folders: StateFlow<Map<String, HomeFolder>> = _folders.asStateFlow()

    val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(application)
    val widgetHost = AppWidgetHost(application, WIDGET_HOST_ID)

    private val _widgetRequests = MutableSharedFlow<AppWidgetProviderInfo>(extraBufferCapacity = 1)
    val widgetRequests: SharedFlow<AppWidgetProviderInfo> = _widgetRequests.asSharedFlow()

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed.asSharedFlow()

    init {
        applyIconStyle(_settings.value)
        viewModelScope.launch {
            kotlinx.coroutines.yield() // let the rest of construction finish before touching later properties
            repo.loaded.first { it }
            if (store.loadItems() == null) { seedDefaults(); store.markAutoHeadlinesCleaned(); store.markPageletsKeepDockApplied() } else migrateLegacyNews()
        }
        viewModelScope.launch {
            kotlinx.coroutines.yield()
            repo.apps.collect { list ->
                refreshWorkState()
                if (list.isNotEmpty()) {
                    pruneMissing(list)
                    val ids = list.mapTo(HashSet()) { it.id }
                    val pruned = _drawerFolders.value.mapValues { (_, f) -> f.copy(appIds = f.appIds.filter { it in ids }) }
                    if (pruned != _drawerFolders.value) saveDrawerFolders(pruned)
                }
            }
        }
    }

    fun onHomePressed() {
        _homePressed.tryEmit(Unit)
    }

    // ---- Launching -------------------------------------------------------------------------

    private val _launchCounts = MutableStateFlow(store.loadLaunchCounts())
    val launchCounts: StateFlow<Map<String, Int>> = _launchCounts.asStateFlow()

    /** Apps the user opens most, for the "Suggested" row. Counts stay on the device. */
    val predicted: StateFlow<List<AppInfo>> =
        combine(apps, _launchCounts, _settings.map { it.hiddenApps }.distinctUntilChanged()) { list, counts, hidden ->
            list.filter { it.id !in hidden && (counts[it.id] ?: 0) > 0 }
                .sortedByDescending { counts[it.id] }
                .take(MAX_PREDICTED)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Supplied by the activity: builds the zoom-from-icon launch transition for the given icon bounds. */
    var launchOptions: ((Rect?) -> android.os.Bundle?)? = null

    fun launch(app: AppInfo, bounds: Rect? = null) {
        val counts = _launchCounts.value
        _launchCounts.value = counts + (app.id to (counts[app.id] ?: 0) + 1)
        store.saveLaunchCounts(_launchCounts.value)
        runCatching { launcherApps.startMainActivity(app.key.component, app.user, bounds, launchOptions?.invoke(bounds)) }
    }

    fun showAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.key.component, app.user, null, null) }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    /**
     * Runs a gesture action. Returns false for actions the UI owns (drawer / search), which the caller
     * must handle itself. [gestureKey] picks the app for "Open an app".
     */
    fun perform(action: GestureAction, gestureKey: String = ""): Boolean {
        when (action) {
            GestureAction.Notifications -> expandPanel("expandNotificationsPanel")
            GestureAction.QuickSettings -> expandPanel("expandSettingsPanel")
            GestureAction.LockScreen -> if (!LockScreenService.lock()) openAccessibilitySettings()
            GestureAction.Recents -> if (!LockScreenService.recents()) openAccessibilitySettings()
            GestureAction.Assistant -> {
                val intent = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { getApplication<Application>().startActivity(intent) }
            }
            GestureAction.OpenApp -> _settings.value.gestureApps[gestureKey]?.let(::appFor)?.let { launch(it) }
            // The UI owns these: they open the drawer, search, or lasso mode.
            GestureAction.None, GestureAction.AppDrawer, GestureAction.Search, GestureAction.Lasso -> return false
        }
        return true
    }

    fun setGestureApp(gestureKey: String, appId: String) = updateSettings {
        it.copy(gestureApps = it.gestureApps + (gestureKey to appId))
    }

    @Suppress("WrongConstant")
    private fun expandPanel(method: String) {
        runCatching {
            val service = getApplication<Application>().getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        }
    }

    fun searchWeb(query: String) {
        val settings = _settings.value
        val template = if (settings.searchProvider == com.snapkode.nivolauncher.data.SearchProvider.Custom) settings.customSearchUrl
        else settings.searchProvider.urlTemplate
        if (!template.contains("%s")) return
        val url = template.replace("%s", Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    suspend fun webSuggestions(query: String): List<String> {
        val s = _settings.value
        if (!s.webSuggestions || query.length < 2) return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.snapkode.nivolauncher.data.SearchTools.webSuggestions(s.searchProvider, query)
        }
    }

    fun openSettingsEntry(entry: com.snapkode.nivolauncher.data.SearchTools.SettingsEntry) {
        val intent = Intent(entry.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun searchContacts(query: String) =
        com.snapkode.nivolauncher.data.ContactsSearch.search(getApplication(), query)

    fun openContact(contact: com.snapkode.nivolauncher.data.ContactResult) =
        com.snapkode.nivolauncher.data.ContactsSearch.open(getApplication(), contact)

    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun openLauncherSettingsForDefault() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun pickWallpaper() {
        val picker = Intent(Intent.ACTION_SET_WALLPAPER)
        val chooser = Intent.createChooser(picker, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(chooser) }
    }

    // ---- Shortcuts (app long-press actions) -----------------------------------------------

    data class AppShortcut(val id: String, val label: String, val pkg: String, val app: AppInfo)

    fun shortcutsFor(app: AppInfo): List<AppShortcut> {
        // App shortcuts need Android 7.1; on 7.0 there simply are none.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N_MR1) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        return try {
            launcherApps.getShortcuts(query, app.user).orEmpty()
                .filter { it.isEnabled }
                .take(4)
                .map { AppShortcut(it.id, (it.shortLabel ?: it.id).toString(), it.`package`, app) }
        } catch (_: SecurityException) {
            emptyList() // Only the default home app may read shortcuts.
        }
    }

    fun startShortcut(shortcut: AppShortcut) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N_MR1) return
        runCatching {
            launcherApps.startShortcut(shortcut.pkg, shortcut.id, null, null, shortcut.app.user)
        }
    }

    // ---- Settings --------------------------------------------------------------------------

    private var saveJob: kotlinx.coroutines.Job? = null

    /** Slider drags change settings dozens of times a second; write them once things settle. */
    private fun scheduleSettingsSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            store.saveSettings(_settings.value)
        }
    }

    /** Writes any pending settings immediately (called when the launcher goes to the background). */
    fun flushSettings() {
        saveJob?.cancel()
        store.saveSettings(_settings.value)
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val updated = transform(_settings.value)
        applyIconStyle(updated)
        _settings.value = updated
        scheduleSettingsSave()
        reflow()
    }

    private fun applyIconStyle(s: LauncherSettings) =
        repo.setStyle(AppRepository.IconStyle(s.iconShape, s.iconPack, s.themedIcons, s.wrapLegacyIcons, s.iconOverrides))

    fun refreshIcons() = repo.reload()

    private fun toast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(getApplication(), message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson(context.filesDir).toByteArray()) }
            }.isSuccess
            toast(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val ok = text != null && store.importJson(text, context.filesDir)
            if (ok) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { reloadFromStore() }
            toast(if (ok) "Backup restored" else "That isn't a Nivo Launcher backup")
        }
    }

    /** Re-reads everything from storage after a restore. */
    private fun reloadFromStore() {
        var settings = store.loadSettings()
        // A restored custom font path may not exist on this device.
        if (settings.fontChoice == FontChoice.Custom && !java.io.File(settings.customFontPath).exists()) {
            settings = settings.copy(fontChoice = FontChoice.System, customFontPath = "")
            store.saveSettings(settings)
        }
        _settings.value = settings
        _items.value = store.loadItems().orEmpty()
        _folders.value = store.loadFolders()
        _drawerFolders.value = store.loadFolders(DRAWER_FOLDERS_KEY)
        _libraryConfig.value = store.loadLibrary()
        _pageCount.value = store.loadPageCount()
        pageletRepo.reload()
        migrateLegacyNews()   // a backup from before News became a Pagelet still carries the old settings
        _launchCounts.value = store.loadLaunchCounts()
        applyIconStyle(settings)
        repo.reload()
    }

    /** Sets a custom icon spec for [id] ("file:…" or "pack:…"), or clears it when [spec] is null. */
    fun setIconOverride(id: String, spec: String?) = updateSettings {
        it.copy(iconOverrides = if (spec == null) it.iconOverrides - id else it.iconOverrides + (id to spec))
    }

    /** Copies a picked image into app storage as a small PNG and uses it as the app's icon. */
    fun importCustomIcon(id: String, uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val context = getApplication<Application>()
                val source = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    ?: return@runCatching
                val side = minOf(source.width, source.height)
                val square = android.graphics.Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
                val out = android.graphics.Bitmap.createScaledBitmap(square, 192, 192, true)
                val dir = java.io.File(context.filesDir, "icons").apply { mkdirs() }
                val name = "${java.util.UUID.randomUUID()}.png"
                java.io.File(dir, name).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                setIconOverride(id, "file:$name")
            }
        }
    }

    /** Copies a picked .ttf/.otf into app storage and switches the font to it. */
    fun importFont(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val context = getApplication<Application>()
                val dir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = java.io.File(dir, "custom_${System.currentTimeMillis()}.ttf")
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                updateSettings { it.copy(fontChoice = FontChoice.Custom, customFontPath = file.path) }
            }
        }
    }

    fun iconPacks() = com.snapkode.nivolauncher.data.IconPack.installed(getApplication())

    /** Sets a custom label for [id]; a blank [name] restores the original. */
    fun renameApp(id: String, name: String) = updateSettings {
        it.copy(labelOverrides = if (name.isBlank()) it.labelOverrides - id else it.labelOverrides + (id to name.trim()))
    }

    fun finishOnboarding() = updateSettings { it.copy(onboarded = true) }

    fun replayOnboarding() = updateSettings { it.copy(onboarded = false) }

    fun toggleFavorite(id: String) = updateSettings {
        it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id)
    }

    fun setHidden(id: String, hidden: Boolean) = updateSettings {
        it.copy(hiddenApps = if (hidden) it.hiddenApps + id else it.hiddenApps - id)
    }

    // ---- Home layout -----------------------------------------------------------------------

    fun isOnHome(id: String) =
        _items.value.any { it.id == id } || _folders.value.values.any { id in it.appIds }

    /** Adds [id] to the first free workspace cell, creating a new page if needed. */
    fun addToHome(id: String) {
        if (isOnHome(id)) return
        commit(_items.value + (freeCell(_items.value, id) ?: return))
    }

    fun removeFromHome(id: String) {
        releaseWidgets(listOf(id))
        commit(_items.value.filterNot { it.id == id })
        trimTrailingEmptyPages()
    }

    // ---- Lasso selection ---------------------------------------------------------------------------------------

    /** Lasso mode's state. Held here so it survives the activity being recreated. */
    val lasso = LassoSession()

    private val lassoHistory = LayoutHistory()
    private val _lassoUndo = MutableStateFlow<String?>(null)

    /** Label of the action Undo would reverse ("Align", "Create folder"...), or null when there is nothing to undo. */
    val lassoUndo: StateFlow<String?> = _lassoUndo.asStateFlow()

    private fun layoutSnapshot() = LayoutSnapshot(_items.value, _folders.value, _pageCount.value)

    /** Puts a snapshot back exactly: icons, folders and the number of pages. */
    private fun restoreLayout(snapshot: LayoutSnapshot) {
        commit(snapshot.items, snapshot.folders)
        setPageCount(snapshot.pageCount)
    }

    /** Enter lasso mode. False if the home screen is locked or the mode is already on. */
    fun lassoStart(): Boolean {
        if (_settings.value.lockHome) return false
        resizingWidgetId.value = null
        lassoHistory.clear()
        _lassoUndo.value = null
        return lasso.arm(layoutSnapshot())
    }

    /** Enter lasso mode with one icon already selected. */
    fun lassoStartWith(id: String): Boolean {
        if (_settings.value.lockHome) return false
        val item = _items.value.firstOrNull { it.id == id && !it.inDock } ?: return false
        lassoHistory.clear()
        _lassoUndo.value = null
        return lasso.armWith(id, item.page, layoutSnapshot())
    }

    /** Done: leave the mode and keep every change made in it. */
    fun lassoDone() = lasso.exit()

    /** Cancel: leave the mode and put the layout back exactly as it was when the mode began. */
    fun lassoCancel() {
        val transaction = lasso.transaction
        lasso.exit()
        if (transaction != null && transaction.isDirty(layoutSnapshot())) restoreLayout(transaction.baseline)
        lassoHistory.clear()
        _lassoUndo.value = null
    }

    /**
     * Runs a batch layout operation and, if it changed anything, commits it atomically and records an undo step.
     * Nothing is written when the operation fails or changes nothing, so a failure can never leave a half-done layout.
     */
    fun lassoRun(label: String, op: (List<HomeItem>, Map<String, HomeFolder>, Grid) -> OpResult): OpResult {
        val before = layoutSnapshot()
        val s = _settings.value
        val result = op(before.items, before.folders, Grid(s.columns, s.rows))
        if (result is OpResult.Ok && result.changed > 0) {
            lassoHistory.push(label, before)
            _lassoUndo.value = label
            commit(result.items, result.folders ?: _folders.value)
            trimTrailingEmptyPages()
        }
        return result
    }

    /** Drops a dragged group. False (and no change) for an invalid plan. One commit, one undo step. */
    fun lassoCommitMove(plan: DropPlan): Boolean {
        val before = layoutSnapshot()
        val moved = GroupDropPlanner.apply(before.items, plan) ?: return false
        if (moved == before.items) return true
        lassoHistory.push("Move", before)
        _lassoUndo.value = "Move"
        commit(moved)
        trimTrailingEmptyPages()
        return true
    }

    /** Reverse the most recent batch action. */
    fun lassoUndoLast(): Boolean {
        val entry = lassoHistory.pop() ?: return false
        restoreLayout(entry.snapshot)
        _lassoUndo.value = lassoHistory.peek()?.label
        // Anything selected that no longer exists after the restore falls out of the selection.
        lasso.retainOnly(_items.value.mapTo(HashSet()) { it.id })
        return true
    }

    fun lassoClearUndo() {
        lassoHistory.clear()
        _lassoUndo.value = null
    }

    fun newFolderId(): String = "$FOLDER_PREFIX${java.util.UUID.randomUUID()}"

    // ---- Drawer folders ----------------------------------------------------------------------

    private val _drawerFolders = MutableStateFlow(store.loadFolders(DRAWER_FOLDERS_KEY))
    val drawerFolders: StateFlow<Map<String, HomeFolder>> = _drawerFolders.asStateFlow()

    private fun saveDrawerFolders(folders: Map<String, HomeFolder>) {
        val nonEmpty = folders.filterValues { it.appIds.isNotEmpty() }
        _drawerFolders.value = nonEmpty
        store.saveFolders(nonEmpty.values, DRAWER_FOLDERS_KEY)
    }

    private fun withoutApp(appId: String) =
        _drawerFolders.value.mapValues { (_, f) -> f.copy(appIds = f.appIds - appId) }

    fun createDrawerFolder(name: String, appId: String) {
        val id = "$FOLDER_PREFIX${java.util.UUID.randomUUID()}"
        saveDrawerFolders(withoutApp(appId) + (id to HomeFolder(id, name.ifBlank { "Folder" }, listOf(appId))))
    }

    /** A new drawer folder holding every app in [ids]. */
    fun createDrawerFolderWith(name: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val id = "$FOLDER_PREFIX${java.util.UUID.randomUUID()}"
        var folders = _drawerFolders.value
        ids.forEach { appId -> folders = folders.mapValues { (_, f) -> f.copy(appIds = f.appIds - appId) } }
        saveDrawerFolders(folders + (id to HomeFolder(id, name.ifBlank { "Folder" }, ids)))
    }

    /** Turns the chosen home icons into one folder, placed where the first of them was. */
    fun groupIntoFolder(ids: List<String>) {
        val apps = ids.filter { !isFolderId(it) && !isAnyWidgetId(it) }
        val items = _items.value
        val present = apps.filter { id -> items.any { it.id == id } }
        if (present.size < 2) return
        val anchor = items.first { it.id == present.first() }
        val folder = HomeFolder("$FOLDER_PREFIX${java.util.UUID.randomUUID()}", "Folder", present)
        val rest = items.filterNot { it.id in present }
        commit(rest + anchor.copy(id = folder.id), _folders.value + (folder.id to folder))
    }

    fun addToDrawerFolder(folderId: String, appId: String) {
        val cleared = withoutApp(appId)
        val folder = cleared[folderId] ?: return
        saveDrawerFolders(cleared + (folderId to folder.copy(appIds = folder.appIds + appId)))
    }

    fun removeFromDrawerFolder(appId: String) = saveDrawerFolders(withoutApp(appId))

    fun renameDrawerFolder(id: String, name: String) {
        val folder = _drawerFolders.value[id] ?: return
        saveDrawerFolders(_drawerFolders.value + (id to folder.copy(name = name.ifBlank { "Folder" })))
    }

    fun deleteDrawerFolder(id: String) = saveDrawerFolders(_drawerFolders.value - id)

    // ---- App Library ---------------------------------------------------------------------------

    private val _libraryConfig = MutableStateFlow(store.loadLibrary())
    val libraryConfig: StateFlow<LibraryConfig> = _libraryConfig.asStateFlow()

    private val androidCategories = HashMap<String, Int>()

    private fun androidCategory(app: AppInfo): Int = androidCategories.getOrPut(app.packageName) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) AppClassifier.CAT_UNDEFINED
        else runCatching { getApplication<Application>().packageManager.getApplicationInfo(app.packageName, 0).category }
            .getOrDefault(AppClassifier.CAT_UNDEFINED)
    }

    /** Apps grouped into the user's categories (hidden apps excluded). */
    val libraryGroups: StateFlow<List<LibraryGroup>> =
        combine(apps, _libraryConfig, _settings.map { it.hiddenApps }.distinctUntilChanged()) { list, config, hidden ->
            val visible = list.filter { it.id !in hidden }
            AppClassifier.group(visible, { AppClassifier.classify(it.packageName, it.label, androidCategory(it)) }, config)
        }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun editLibrary(block: (LibraryConfig) -> LibraryConfig) {
        _libraryConfig.value = block(_libraryConfig.value)
        store.saveLibrary(_libraryConfig.value)
    }

    fun renameCategory(id: String, name: String) = editLibrary {
        it.copy(renamed = if (name.isBlank()) it.renamed - id else it.renamed + (id to name.trim()))
    }

    fun moveAppToCategory(appId: String, categoryId: String) = editLibrary { it.copy(overrides = it.overrides + (appId to categoryId)) }

    fun hideCategory(id: String) = editLibrary { it.copy(hidden = it.hidden + id) }

    fun createCategory(name: String): String {
        val id = "custom:${java.util.UUID.randomUUID()}"
        editLibrary { it.copy(custom = it.custom + (id to name.trim().ifBlank { "New category" })) }
        return id
    }

    fun deleteCategory(id: String) = editLibrary {
        it.copy(custom = it.custom - id, overrides = it.overrides.filterValues { v -> v != id }, order = it.order - id)
    }

    /** Moves a category one place earlier (-1) or later (+1) in the library. */
    fun moveCategory(id: String, delta: Int) {
        val current = libraryGroups.value.map { it.id }.toMutableList()
        val from = current.indexOf(id)
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (from < 0 || from == to) return
        current.add(to, current.removeAt(from))
        editLibrary { it.copy(order = current) }
    }

    fun resetLibrary() = editLibrary { LibraryConfig() }

    // ---- Dynamic wallpaper -----------------------------------------------------------------------

    private val _dynamicWallpaper = MutableStateFlow(
        com.snapkode.nivolauncher.data.DynamicWallpaper.state(getApplication())
    )
    val dynamicWallpaper: StateFlow<com.snapkode.nivolauncher.data.DynamicWallpaperState> = _dynamicWallpaper.asStateFlow()

    private fun refreshDynamicWallpaper() {
        _dynamicWallpaper.value = com.snapkode.nivolauncher.data.DynamicWallpaper.state(getApplication())
    }

    fun setDynamicWallpaperEnabled(enabled: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.snapkode.nivolauncher.data.DynamicWallpaper.setEnabled(getApplication(), enabled)
            refreshDynamicWallpaper()
        }
    }

    fun setPeriodWallpaper(period: com.snapkode.nivolauncher.data.DayPeriod, uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.snapkode.nivolauncher.data.DynamicWallpaper.import(getApplication(), period, uri)
            refreshDynamicWallpaper()
        }
    }

    fun clearPeriodWallpaper(period: com.snapkode.nivolauncher.data.DayPeriod) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.snapkode.nivolauncher.data.DynamicWallpaper.clear(getApplication(), period)
            refreshDynamicWallpaper()
        }
    }

    /** Re-checks the time of day whenever the launcher comes to the front. */
    fun syncDynamicWallpaper() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            com.snapkode.nivolauncher.data.DynamicWallpaper.applyNow(context)
            com.snapkode.nivolauncher.data.DynamicWallpaper.schedule(context)
        }
    }

    // ---- News ------------------------------------------------------------------------------------

    /** Each Headlines Pagelet's current list, by Pagelet id. */
    private val _newsStates = MutableStateFlow<Map<String, com.snapkode.nivolauncher.data.NewsState>>(emptyMap())
    val newsStates: StateFlow<Map<String, com.snapkode.nivolauncher.data.NewsState>> = _newsStates.asStateFlow()
    /** Each feed's last download, by address: stories and when. A feed that failed is remembered too, so it isn't hammered. Shared by every Headlines Pagelet. */
    private class CachedFeed(val at: Long, val feed: com.snapkode.nivolauncher.data.news.ParsedFeed?)
    private val feedCache = HashMap<String, CachedFeed>()
    private val newsJobs = HashMap<String, kotlinx.coroutines.Job>()

    private fun setNewsState(id: String, state: com.snapkode.nivolauncher.data.NewsState) { _newsStates.value = _newsStates.value + (id to state) }

    /** The topic actually shown: the chosen one if a source covers it, otherwise Top. */
    fun effectiveNewsTopic(c: PageletConfig): com.snapkode.nivolauncher.data.NewsTopic =
        c.newsTopic.takeIf { it in com.snapkode.nivolauncher.data.news.NewsCatalog.topicsFor(c.news) } ?: com.snapkode.nivolauncher.data.NewsTopic.Top

    private fun mergedFromCache(plan: List<com.snapkode.nivolauncher.data.news.NewsFeedRef>, prefs: com.snapkode.nivolauncher.data.news.NewsPrefs) =
        com.snapkode.nivolauncher.data.news.NewsMerge.merge(plan.map { feedCache[it.url]?.feed?.items.orEmpty() }, prefs)

    /** Shows the chosen topic from all chosen sources on Headlines Pagelet [id], refetching any feed older than ten minutes (or all of them if [force]). */
    fun loadNews(id: String, force: Boolean = false) {
        if (_items.value.none { it.id == id }) return
        val config = pageletRepo.configOf(id)
        val prefs = config.news
        val topic = effectiveNewsTopic(config)
        val plan = com.snapkode.nivolauncher.data.news.NewsCatalog.plan(prefs, topic)
        if (plan.isEmpty()) { newsJobs.remove(id)?.cancel(); setNewsState(id, com.snapkode.nivolauncher.data.NewsState()); return }
        val now = System.currentTimeMillis()
        val stale = plan.filter { force || feedCache[it.url]?.let { c -> com.snapkode.nivolauncher.data.NewsFormat.isFresh(c.at, now) } != true }
        val shown = mergedFromCache(plan, prefs)
        if (stale.isEmpty()) {
            setNewsState(id, com.snapkode.nivolauncher.data.NewsState(shown, failedSources = plan.filter { feedCache[it.url]?.feed == null }.map { it.sourceName }))
            return
        }
        setNewsState(id, com.snapkode.nivolauncher.data.NewsState(shown, loading = true))
        newsJobs.remove(id)?.cancel()
        newsJobs[id] = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val gate = kotlinx.coroutines.sync.Semaphore(6)            // a handful at a time: polite to publishers and to the radio
            kotlinx.coroutines.coroutineScope {
                stale.map { ref ->
                    async { gate.withPermit { ref.url to com.snapkode.nivolauncher.data.NewsSource.fetch(ref) } }
                }.awaitAll()
            }.forEach { (url, feed) ->
                // A failed refresh keeps the last good stories instead of blanking the feed.
                val previous = feedCache[url]?.feed
                feedCache[url] = CachedFeed(System.currentTimeMillis(), feed ?: previous)
                if (feed == null && previous == null) feedCache[url] = CachedFeed(System.currentTimeMillis() - 9 * 60_000L, null)   // retry soon
            }
            val items = mergedFromCache(plan, prefs)
            val failed = plan.filter { feedCache[it.url]?.feed == null }.map { it.sourceName }
            setNewsState(id, com.snapkode.nivolauncher.data.NewsState(items, error = items.isEmpty() && failed.isNotEmpty(), failedSources = failed))
        }
    }

    fun setNewsTopic(id: String, topic: com.snapkode.nivolauncher.data.NewsTopic) {
        updatePagelet(id) { it.copy(newsTopic = topic) }
        loadNews(id)
    }

    /** Changes any of a Headlines Pagelet's news choices. A change to which sources are read refetches; a change to looks only re-sorts what is already here. */
    fun updateNews(id: String, transform: (com.snapkode.nivolauncher.data.news.NewsPrefs) -> com.snapkode.nivolauncher.data.news.NewsPrefs) {
        val before = pageletRepo.configOf(id).news
        updatePagelet(id) { it.copy(news = transform(it.news)) }
        val after = pageletRepo.configOf(id).news
        if (before.providers != after.providers || before.custom != after.custom || before.perSource != after.perSource || before.order != after.order) loadNews(id)
    }

    /** Checks a feed address a user typed: the page it's served from must really be a feed with stories. Returns its title and story count, or null. */
    suspend fun checkFeed(url: String): Pair<String, Int>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        com.snapkode.nivolauncher.data.NewsSource.fetch(com.snapkode.nivolauncher.data.news.NewsFeedRef(com.snapkode.nivolauncher.data.news.NewsFeedUrl.nameFrom(url), url))
            ?.let { (it.title ?: com.snapkode.nivolauncher.data.news.NewsFeedUrl.nameFrom(url)) to it.items.size }
    }

    // ---- Weather ---------------------------------------------------------------------------------

    private val _weather = MutableStateFlow(store.loadWeatherCache())
    val weather: StateFlow<com.snapkode.nivolauncher.data.Weather?> = _weather.asStateFlow()
    private var weatherFetching = false

    /** Fetches weather if enabled, a place is chosen, and the cache is older than 30 minutes. */
    fun refreshWeatherIfStale(force: Boolean = false) {
        val s = _settings.value
        if (!s.weatherEnabled || s.weatherPlace.isEmpty() || weatherFetching) return
        val cached = _weather.value
        if (!force && cached != null && com.snapkode.nivolauncher.data.WeatherFormat.isFresh(cached.fetchedAt, System.currentTimeMillis())) return
        weatherFetching = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val fresh = com.snapkode.nivolauncher.data.WeatherSource.fetch(s.weatherLat, s.weatherLon)
            if (fresh != null) {
                _weather.value = fresh
                store.saveWeatherCache(fresh)
            }
            weatherFetching = false
        }
    }

    /** The phone's own Geocoder first (nothing leaves the device if it can answer); the online search only as a fallback. */
    suspend fun searchPlaces(query: String): List<com.snapkode.nivolauncher.data.Place> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val local = com.snapkode.nivolauncher.pagelet.weather.PlaceSearch.onDevice(getApplication(), query)
            local.ifEmpty { com.snapkode.nivolauncher.data.WeatherSource.search(query) }
        }

    /** Sets only the place (used by the Today Pagelet): unlike [chooseWeatherPlace] it doesn't switch the smartspace weather on or fetch anything. */
    fun setWeatherPlace(place: com.snapkode.nivolauncher.data.Place) =
        updateSettings { it.copy(weatherPlace = place.name, weatherLat = place.latitude, weatherLon = place.longitude) }

    /** Forgets the chosen city, so the phone's approximate position (if allowed) is used instead. */
    fun clearWeatherPlace() = updateSettings { it.copy(weatherPlace = "", weatherLat = 0.0, weatherLon = 0.0) }

    fun chooseWeatherPlace(place: com.snapkode.nivolauncher.data.Place) {
        _weather.value = null
        store.saveWeatherCache(null)
        updateSettings { it.copy(weatherEnabled = true, weatherPlace = place.name, weatherLat = place.latitude, weatherLon = place.longitude) }
        refreshWeatherIfStale(force = true)
    }

    // ---- Work profile -------------------------------------------------------------------------

    private val userManager = application.getSystemService(android.os.UserManager::class.java)
    private val ownUser = android.os.Process.myUserHandle()


    fun isWorkApp(app: AppInfo) = app.user != ownUser

    private fun refreshWorkState() {
        val others = runCatching { userManager.userProfiles }.getOrNull().orEmpty().filter { it != ownUser }
        _hasWorkProfile.value = others.isNotEmpty()
        _workPaused.value = others.any { runCatching { userManager.isQuietModeEnabled(it) }.getOrDefault(false) }
    }

    fun setWorkPaused(paused: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
        userManager.userProfiles.filter { it != ownUser }.forEach {
            runCatching { userManager.requestQuietModeEnabled(paused, it) }
        }
        _workPaused.value = paused
    }

    // ---- Widgets ---------------------------------------------------------------------------

    fun requestWidget(info: AppWidgetProviderInfo) {
        _widgetRequests.tryEmit(info)
    }

    /** Places a bound widget on the first page with room; returns false if nothing fits. */
    fun placeWidget(appWidgetId: Int, spanX: Int, spanY: Int): Boolean {
        val s = _settings.value
        val sx = spanX.coerceIn(1, s.columns)
        val sy = spanY.coerceIn(1, s.rows)
        val cell = freeCell(_items.value, widgetItemId(appWidgetId), sx, sy) ?: return false
        commit(_items.value + cell)
        return true
    }

    /** Adds the animated Nivo clock on the first page with room, as wide as the grid and two rows tall (fewer if the grid is short). */
    fun addNivoClock(): Boolean {
        val s = _settings.value
        val spanY = minOf(2, s.rows)
        val cell = freeCell(_items.value, newNivoClockId(), s.columns, spanY) ?: return false
        commit(_items.value + cell)
        return true
    }

    fun discardWidget(appWidgetId: Int) = widgetHost.deleteAppWidgetId(appWidgetId)

    /**
     * Moves or resizes a widget to the given cell rectangle on its page. The rectangle is kept on screen. Other widgets
     * can't be overlapped, but icons and folders in the way are moved aside to the nearest free cells (or another page).
     * If they have nowhere to go, nothing changes.
     */
    private fun widgetLayout(id: String, col: Int, row: Int, spanX: Int, spanY: Int): List<HomeItem>? {
        val s = _settings.value
        val item = _items.value.firstOrNull { it.id == id } ?: return null
        val sx = spanX.coerceIn(1, s.columns)
        val sy = spanY.coerceIn(1, s.rows)
        val c = col.coerceIn(0, s.columns - sx)
        val r = row.coerceIn(0, s.rows - sy)
        val others = _items.value.filter { it.id != id }
        fun inRect(it: HomeItem) = !it.inDock && it.page == item.page &&
            it.col < c + sx && it.col + it.spanX > c && it.row < r + sy && it.row + it.spanY > r
        val inWay = others.filter(::inRect)
        // Another widget in the way is a hard stop; only plain icons and folders get pushed aside.
        if (inWay.any { isAnyWidgetId(it.id) }) return null
        val moved = item.copy(col = c, row = r, spanX = sx, spanY = sy)
        var layout = others.filterNot(::inRect) + moved
        for (displaced in inWay) {
            val spot = nearestFreeCell(layout, item.page, displaced.col, displaced.row, c, r, sx, sy)
                ?: freeCell(layout, displaced.id)
                ?: return null
            layout = layout + spot.copy(id = displaced.id)
        }
        return layout
    }

    fun placeWidgetAt(id: String, col: Int, row: Int, spanX: Int, spanY: Int) {
        _previewItems.value = null
        widgetLayout(id, col, row, spanX, spanY)?.let { commit(it) }
    }

    /** While a resize handle is dragged: where icons and folders would go, shown live. The widget itself is left alone. */
    private val _previewItems = MutableStateFlow<List<HomeItem>?>(null)
    val previewItems: StateFlow<List<HomeItem>?> = _previewItems.asStateFlow()

    fun previewWidgetAt(id: String, col: Int, row: Int, spanX: Int, spanY: Int) {
        val original = _items.value.firstOrNull { it.id == id }
        val layout = widgetLayout(id, col, row, spanX, spanY)
        val shown = if (layout == null || original == null) null
        else layout.map { if (it.id == id) original else it }.takeIf { it != _items.value }
        if (_previewItems.value != shown) _previewItems.value = shown
    }

    fun clearWidgetPreview() { _previewItems.value = null }

    /** Which widget is in resize mode. Kept here so it survives the page being rebuilt while icons are rearranged. */
    val resizingWidgetId = MutableStateFlow<String?>(null)

    /** The free single cell on [page] closest to ([nearCol], [nearRow]) that isn't inside the given rectangle. */
    private fun nearestFreeCell(items: List<HomeItem>, page: Int, nearCol: Int, nearRow: Int, rc: Int, rr: Int, rsx: Int, rsy: Int): HomeItem? {
        val s = _settings.value
        var best: HomeItem? = null
        var bestDistance = Int.MAX_VALUE
        for (row in 0 until s.rows) for (col in 0 until s.columns) {
            val insideRect = col >= rc && col < rc + rsx && row >= rr && row < rr + rsy
            if (insideRect || items.any { it.occupies(page, col, row) }) continue
            val distance = (col - nearCol) * (col - nearCol) + (row - nearRow) * (row - nearRow)
            if (distance < bestDistance) { bestDistance = distance; best = HomeItem("", page, col, row) }
        }
        return best
    }

    /** Every widget provider on this device, including those from the work profile. */
    fun allWidgetProviders(): List<AppWidgetProviderInfo> {
        val result = ArrayList<AppWidgetProviderInfo>(widgetManager.installedProviders)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            userManager.userProfiles.filter { it != ownUser }.forEach {
                result += runCatching { widgetManager.getInstalledProvidersForProfile(it) }.getOrDefault(emptyList())
            }
        }
        return result
    }

    fun isWorkWidget(info: AppWidgetProviderInfo) = info.profile != ownUser

    private fun releaseWidgets(ids: Collection<String>) =
        ids.filter(::isWidgetId).forEach { widgetHost.deleteAppWidgetId(appWidgetIdOf(it)) }

    // ---- Folders ---------------------------------------------------------------------------

    fun renameFolder(id: String, name: String) {
        val folder = _folders.value[id] ?: return
        commit(_items.value, _folders.value + (id to folder.copy(name = name)))
    }

    /** Drags [appId] out of [folderId] and drops it at [target]. */
    fun dropFromFolder(folderId: String, appId: String, target: DropTarget) {
        val folder = _folders.value[folderId] ?: return
        val placed = freeCell(_items.value, appId) ?: return
        commit(_items.value + placed, _folders.value + (folderId to folder.copy(appIds = folder.appIds - appId)))
        drop(appId, target)
    }

    /** Drops an app dragged out of the drawer: placed on the home screen first if it isn't there yet, then moved to [target]. */
    fun dropFromDrawer(appId: String, target: DropTarget) {
        if (_items.value.none { it.id == appId }) {
            val placed = freeCell(_items.value, appId) ?: return
            commit(_items.value + placed)
        }
        drop(appId, target)
    }

    /** Moves [appId] out of its folder onto the first free workspace cell. */
    fun removeFromFolder(folderId: String, appId: String) {
        val folder = _folders.value[folderId] ?: return
        val updated = _folders.value + (folderId to folder.copy(appIds = folder.appIds - appId))
        val placed = freeCell(_items.value, appId)
        commit(if (placed != null) _items.value + placed else _items.value, updated)
    }

    /** Replaces a folder with its apps, the first one taking the folder's spot. */
    fun dissolveFolder(folderId: String) {
        val folder = _folders.value[folderId] ?: return
        val spot = _items.value.firstOrNull { it.id == folderId } ?: return
        var items = _items.value.filterNot { it.id == folderId }
        folder.appIds.forEachIndexed { index, appId ->
            items = items + if (index == 0) spot.copy(id = appId) else (freeCell(items, appId) ?: return@forEachIndexed)
        }
        commit(items, _folders.value - folderId)
    }

    fun drop(id: String, target: DropTarget) {
        val current = _items.value
        val source = current.firstOrNull { it.id == id } ?: return
        val s = _settings.value
        when (target) {
            is DropTarget.Cell -> {
                val dest = HomeItem(id, target.page, target.col.coerceIn(0, s.columns - 1), target.row.coerceIn(0, s.rows - 1))
                placeOrMerge(current, source, dest)
                trimTrailingEmptyPages()
            }
            is DropTarget.Dock -> {
                val dest = HomeItem(id, HomeItem.DOCK, target.slot.coerceIn(0, s.dockSlots * s.dockPages - 1), 0)
                placeOrMerge(current, source, dest)
            }
        }
    }

    /** Dropping an app onto another app/folder groups them; anything else swaps places. */
    private fun placeOrMerge(current: List<HomeItem>, source: HomeItem, dest: HomeItem) {
        val occupant = current.firstOrNull { it.id != source.id && it.occupies(dest.page, dest.col, dest.row) }
        if (occupant != null && isAnyWidgetId(occupant.id)) {
            // A Pagelet owns its whole page: say so, rather than silently ignoring the drop.
            if (isPageletId(occupant.id)) toast("This page is a Pagelet")
            return // can't drop onto a widget
        }
        if (occupant == null || isFolderId(source.id)) {
            commit(placeWithSwap(current, source, dest))
            return
        }
        val folders = _folders.value
        val remaining = current.filterNot { it.id == source.id }
        if (isFolderId(occupant.id)) {
            val folder = folders.getValue(occupant.id)
            commit(remaining, folders + (folder.id to folder.copy(appIds = folder.appIds + source.id)))
        } else {
            val folder = HomeFolder("$FOLDER_PREFIX${java.util.UUID.randomUUID()}", "Folder", listOf(occupant.id, source.id))
            val grouped = remaining.filterNot { it.id == occupant.id } + occupant.copy(id = folder.id)
            commit(grouped, folders + (folder.id to folder))
        }
    }

    private fun placeWithSwap(current: List<HomeItem>, source: HomeItem, dest: HomeItem): List<HomeItem> {
        val occupant = current.firstOrNull { it.id != source.id && it.occupies(dest.page, dest.col, dest.row) }
        return current.map {
            when (it.id) {
                source.id -> dest
                occupant?.id -> it.copy(page = source.page, col = source.col, row = source.row)
                else -> it
            }
        }
    }

    private fun freeCell(items: List<HomeItem>, id: String, spanX: Int = 1, spanY: Int = 1): HomeItem? {
        val s = _settings.value
        val pages = maxOf((items.filter { !it.inDock }.maxOfOrNull { it.page } ?: -1) + 1, _pageCount.value)
        for (page in 0..pages) {
            for (row in 0..s.rows - spanY) for (col in 0..s.columns - spanX) {
                val free = (col until col + spanX).all { c ->
                    (row until row + spanY).all { r -> items.none { it.occupies(page, c, r) } }
                }
                if (free) return HomeItem(id, page, col, row, spanX, spanY)
            }
        }
        return null
    }

    /** Pulls items that fell outside the grid (after a settings change) back onto the workspace. */
    private fun reflow() {
        val s = _settings.value
        // A Pagelet always fills its page, whatever the grid is now.
        val resized = _items.value.map {
            if (isPageletId(it.id) && (it.col != 0 || it.row != 0 || it.spanX != s.columns || it.spanY != s.rows))
                it.copy(col = 0, row = 0, spanX = s.columns, spanY = s.rows) else it
        }
        if (resized != _items.value) commit(resized)
        var items = _items.value
        val stray = items.filter {
            if (it.inDock) it.col >= s.dockSlots * s.dockPages
            else it.col + it.spanX > s.columns || it.row + it.spanY > s.rows
        }
        if (stray.isEmpty()) return
        items = items - stray.toSet()
        for (item in stray) {
            val sx = item.spanX.coerceAtMost(s.columns)
            val sy = item.spanY.coerceAtMost(s.rows)
            items = items + (freeCell(items, item.id, sx, sy) ?: continue)
        }
        commit(items)
    }

    private fun pruneMissing(installed: List<AppInfo>) {
        val ids = installed.mapTo(HashSet()) { it.id }
        val current = _items.value
        val kept = current.filter {
            when {
                isFolderId(it.id) || isNivoWidgetId(it.id) -> true
                isWidgetId(it.id) -> widgetManager.getAppWidgetInfo(appWidgetIdOf(it.id)) != null
                else -> it.id in ids
            }
        }
        // Launcher-drawn widgets (clock, notes, ...) no longer exist: drop any left over from older versions.
        if (current.any { it.id.startsWith("builtin:") }) {
            getApplication<Application>().deleteSharedPreferences("builtin_widgets")
        }
        val folders = _folders.value.mapValues { (_, f) -> f.copy(appIds = f.appIds.filter { it in ids }) }
        if (kept.size != current.size || folders != _folders.value) commit(kept, folders)
    }

    /** Closes gaps left by empty pages, then publishes and saves. */
    private fun commit(items: List<HomeItem>, folders: Map<String, HomeFolder> = _folders.value) {
        var folderMap = folders.filterKeys { id -> items.any { it.id == id } }
        var placed = items.filter { !isFolderId(it.id) || it.id in folderMap }
        // A folder with one app left becomes that app; an empty folder disappears.
        for (folder in folderMap.values.filter { it.appIds.size <= 1 }) {
            val only = folder.appIds.firstOrNull()?.takeIf { app -> placed.none { it.id == app } }
            placed = placed.mapNotNull {
                if (it.id != folder.id) it else only?.let { app -> it.copy(id = app) }
            }
            folderMap = folderMap - folder.id
        }
        _folders.value = folderMap
        store.saveFolders(folderMap.values)
        commitItems(placed)
    }

    private fun commitItems(items: List<HomeItem>) {
        _items.value = items
        store.saveItems(items)
        val needed = (items.filter { !it.inDock }.maxOfOrNull { it.page } ?: -1) + 1
        if (needed > _pageCount.value) setPageCount(needed)
    }

    private fun setPageCount(count: Int) {
        _pageCount.value = count
        store.savePageCount(count)
    }

    // ---- Pagelets ---------------------------------------------------------------------------------------------

    private val pageletRepo = PageletRepository(store)

    /** Each Pagelet's config, by item id. The placement itself is an ordinary workspace item in [items]. */
    val pagelets: StateFlow<Map<String, PageletConfig>> = pageletRepo.configs

    /** What was just removed, kept briefly so an Undo snackbar can bring it back. */
    class PageletRemoval(val item: HomeItem, val config: PageletConfig, val pageDeleted: Boolean)

    private val _pageletUndo = MutableStateFlow<PageletRemoval?>(null)
    val pageletUndo: StateFlow<PageletRemoval?> = _pageletUndo.asStateFlow()

    /** Adds a Pagelet on a page of its own and returns that page's index, or null if it can't be added. */
    fun addPagelet(type: PageletType, config: PageletConfig = PageletConfig.defaultsFor(type)): Int? {
        if (!type.available || _settings.value.lockHome || hasPagelet(type)) return null
        val s = _settings.value
        val id = newPageletId(type)
        // Spanning the whole grid, it can only land on a page that is completely empty (or a brand-new one).
        val cell = freeCell(_items.value, id, s.columns, s.rows) ?: return null
        pageletRepo.put(id, config)
        commit(_items.value + cell)
        return cell.page
    }

    /** Each kind of Pagelet can be on home once: remove it to add it again. */
    fun hasPagelet(type: PageletType): Boolean = _items.value.any { pageletTypeOf(it.id) == type }

    /** The kinds of Pagelet that can still be added. */
    fun addablePagelets(): List<PageletType> = PageletType.entries.filter { it.available && !hasPagelet(it) }

    fun updatePagelet(id: String, transform: (PageletConfig) -> PageletConfig) {
        if (_items.value.none { it.id == id }) return
        pageletRepo.put(id, transform(pageletRepo.configOf(id)))
    }

    /** Removes a Pagelet and the page it occupied (if that leaves the page empty). Undo is offered by [pageletUndo]. */
    fun removePagelet(id: String) {
        val item = _items.value.firstOrNull { it.id == id && isPageletId(it.id) } ?: return
        val config = pageletRepo.configOf(id)
        pageletRepo.remove(id)
        newsJobs.remove(id)?.cancel()
        _newsStates.value = _newsStates.value - id
        commit(_items.value - item)
        val deleted = deletePage(item.page)
        _pageletUndo.value = PageletRemoval(item, config, deleted)
    }

    /** Brings back the last removed Pagelet where it was. Returns its page, or null if there was nothing to undo. */
    fun undoRemovePagelet(): Int? {
        val removal = _pageletUndo.value ?: return null
        _pageletUndo.value = null
        val item = removal.item
        if (removal.pageDeleted) {
            // Reopen the gap the deleted page left.
            commitItems(_items.value.map { if (!it.inDock && it.page >= item.page) it.copy(page = it.page + 1) else it })
            setPageCount(pages.value + 1)
            if (item.page <= _settings.value.defaultPage) setDefaultPage(_settings.value.defaultPage + 1)
        }
        pageletRepo.put(item.id, removal.config)
        commit(_items.value + item)
        return item.page
    }

    fun clearPageletUndo() { _pageletUndo.value = null }

    // ---- Pages (overview) ----------------------------------------------------------------------

    fun addPage() = setPageCount(pages.value + 1)

    /** Deletes an *empty* page and shifts later pages down. Returns false if it still has items. */
    fun deletePage(index: Int): Boolean {
        val count = pages.value
        if (count <= 1 || index !in 0 until count) return false
        if (_items.value.any { !it.inDock && it.page == index }) return false
        commitItems(_items.value.map { if (!it.inDock && it.page > index) it.copy(page = it.page - 1) else it })
        setPageCount(count - 1)
        val home = _settings.value.defaultPage
        if (index < home) setDefaultPage(home - 1) else if (index == home) setDefaultPage(0)
        return true
    }

    /** Swaps a page with its neighbour ([delta] = -1 or +1). */
    fun movePage(index: Int, delta: Int) {
        val other = index + delta
        if (index !in 0 until pages.value || other !in 0 until pages.value) return
        commitItems(_items.value.map {
            when {
                it.inDock -> it
                it.page == index -> it.copy(page = other)
                it.page == other -> it.copy(page = index)
                else -> it
            }
        })
        val home = _settings.value.defaultPage
        if (home == index) setDefaultPage(other) else if (home == other) setDefaultPage(index)
    }

    fun setDefaultPage(index: Int) = updateSettings { it.copy(defaultPage = index.coerceAtLeast(0)) }

    /** Drops empty pages left at the end after items are moved or removed. */
    private fun trimTrailingEmptyPages() {
        var count = pages.value
        val home = _settings.value.defaultPage
        while (count > 1 && count - 1 > home && _items.value.none { !it.inDock && it.page == count - 1 }) count--
        if (count != _pageCount.value) setPageCount(count)
    }

    private fun seedDefaults() {
        val list = repo.apps.value
        fun firstFor(intent: Intent): AppInfo? {
            val pkg = getApplication<Application>().packageManager
                .resolveActivity(intent, 0)?.activityInfo?.packageName ?: return null
            return list.firstOrNull { it.packageName == pkg }
        }
        val dockApps = listOfNotNull(
            firstFor(Intent(Intent.ACTION_DIAL)),
            firstFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            firstFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)),
            firstFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            firstFor(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))),
        ).distinctBy { it.id }
        var items = dockApps.mapIndexed { i, app -> HomeItem(app.id, HomeItem.DOCK, i, 0) }
        // Fill the first page with a few more apps so the screen isn't empty on first launch.
        val s = _settings.value
        list.filter { app -> items.none { it.id == app.id } }
            .take(s.columns * 2)
            .forEach { app -> items = items + (freeCell(items, app.id) ?: return@forEach) }
        commit(items)
    }

    /** The old News page is gone: its leftover settings are dropped, and nothing takes its place until the user adds Headlines themselves. */
    private fun migrateLegacyNews() {
        if (store.loadLegacyNews() != null) store.clearLegacyNews()
        // An earlier build added a Headlines page by itself (migration or first-run seeding); nobody asked for it, so remove it
        // once. From then on a Headlines page is only ever one the user added.
        if (!store.autoHeadlinesCleaned()) {
            store.markAutoHeadlinesCleaned()
            _items.value.filter { pageletTypeOf(it.id) == PageletType.HEADLINES }.forEach { removePagelet(it.id) }
            clearPageletUndo()
        }
        // The dock now stays on every Pagelet unless "Use the whole page" is switched on. Pagelets saved while that was the
        // other way round all carry the old default, so switch them over once; after this, the toggle is the user's own.
        if (!store.pageletsKeepDockApplied()) {
            store.markPageletsKeepDockApplied()
            pageletRepo.configs.value.filter { it.value.hideDock }.keys.forEach { id -> updatePagelet(id) { it.copy(hideDock = false) } }
        }
    }

    /** Resolves an encoded id to its app, if installed. */
    fun appFor(id: String): AppInfo? = AppKey.decode(id)?.let { key -> apps.value.firstOrNull { it.key == key } }
}
