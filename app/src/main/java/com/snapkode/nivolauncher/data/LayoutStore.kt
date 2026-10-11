package com.snapkode.nivolauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the home layout and user settings in SharedPreferences. */
class LayoutStore(context: Context) {
    private val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600

    private val prefs = context.getSharedPreferences("nivo_launcher", Context.MODE_PRIVATE)

    /** Returns null on first run (nothing saved yet). */
    fun loadItems(): List<HomeItem>? {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) {
                val o = array.getJSONObject(it)
                HomeItem(
                    o.getString("id"), o.getInt("page"), o.getInt("col"), o.getInt("row"),
                    o.optInt("sx", 1), o.optInt("sy", 1),
                )
            }
        }.getOrNull()
    }

    fun saveItems(items: List<HomeItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject().put("id", it.id).put("page", it.page).put("col", it.col).put("row", it.row)
                    .put("sx", it.spanX).put("sy", it.spanY)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun loadFolders(key: String = KEY_FOLDERS): Map<String, HomeFolder> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it) }.associate { o ->
                val apps = o.getJSONArray("apps")
                val id = o.getString("id")
                id to HomeFolder(id, o.getString("name"), List(apps.length()) { apps.getString(it) })
            }
        }.getOrDefault(emptyMap())
    }

    fun saveFolders(folders: Collection<HomeFolder>, key: String = KEY_FOLDERS) {
        val array = JSONArray()
        folders.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.appIds)))
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun loadLaunchCounts(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString("launchCounts", "{}").orEmpty())
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun saveLaunchCounts(counts: Map<String, Int>) {
        prefs.edit().putString("launchCounts", JSONObject(counts as Map<*, *>).toString()).apply()
    }

    /** One key per Pagelet in this same file, so backup and restore carry them automatically. */
    fun loadPageletConfigs(): Map<String, com.snapkode.nivolauncher.pagelet.PageletConfig> {
        val out = HashMap<String, com.snapkode.nivolauncher.pagelet.PageletConfig>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(PAGELET_KEY) || value !is String) return@forEach
            val id = key.removePrefix(PAGELET_KEY)
            out[id] = com.snapkode.nivolauncher.pagelet.PageletConfig.fromJson(value, com.snapkode.nivolauncher.pagelet.pageletTypeOf(id))
        }
        return out
    }

    fun savePageletConfig(id: String, config: com.snapkode.nivolauncher.pagelet.PageletConfig?) {
        prefs.edit().apply { if (config == null) remove(PAGELET_KEY + id) else putString(PAGELET_KEY + id, config.toJson()) }.apply()
    }

    /**
     * The old separate News page's settings, if this phone still has them (from before News became a Pagelet): whether the page
     * was on, and the config to carry over. Null once [clearLegacyNews] has run, or on a phone that never had the page.
     */
    fun loadLegacyNews(): LegacyNews? {
        if (!prefs.contains("newsPage")) return null
        val d = com.snapkode.nivolauncher.data.news.NewsPrefs()
        val custom = runCatching {
            val a = JSONArray(prefs.getString("newsCustom", "[]"))
            List(a.length()) { val o = a.getJSONObject(it); com.snapkode.nivolauncher.data.news.NewsCustomFeed(o.getString("name"), o.getString("url")) }
        }.getOrDefault(emptyList())
        val news = com.snapkode.nivolauncher.data.news.NewsPrefs(
            // A phone that never saved a choice keeps reading the BBC, as it always did.
            providers = prefs.getStringSet("newsProviders", null)?.toSet() ?: d.providers,
            custom = custom,
            style = com.snapkode.nivolauncher.data.news.NewsStyle.entries.getOrElse(prefs.getInt("newsStyle", d.style.ordinal)) { d.style },
            images = prefs.getBoolean("newsImages", d.images),
            perSource = prefs.getInt("newsPerSource", d.perSource).coerceIn(1, 50),
            order = com.snapkode.nivolauncher.data.news.NewsOrder.entries.getOrElse(prefs.getInt("newsOrder", d.order.ordinal)) { d.order },
        )
        val topic = NewsTopic.entries.getOrElse(prefs.getInt("newsTopic", 0)) { NewsTopic.Top }
        return LegacyNews(prefs.getBoolean("newsPage", true), news, topic)
    }

    /** True once the one-time removal of auto-created Headlines pages has run (or was never needed). */
    fun autoHeadlinesCleaned(): Boolean = prefs.getBoolean("autoHeadlinesCleaned", false)
    fun markAutoHeadlinesCleaned() = prefs.edit().putBoolean("autoHeadlinesCleaned", true).apply()

    /** True once Pagelets saved before "keep the dock" became the default have been switched over (or never needed it). */
    fun pageletsKeepDockApplied(): Boolean = prefs.getBoolean("pageletsKeepDock", false)
    fun markPageletsKeepDockApplied() = prefs.edit().putBoolean("pageletsKeepDock", true).apply()

    fun clearLegacyNews() {
        prefs.edit().remove("newsPage").remove("newsTopic").remove("newsProviders").remove("newsCustom")
            .remove("newsStyle").remove("newsImages").remove("newsPerSource").remove("newsOrder").apply()
    }

    class LegacyNews(val enabled: Boolean, val news: com.snapkode.nivolauncher.data.news.NewsPrefs, val topic: NewsTopic)

    fun loadPageCount(): Int = prefs.getInt("pageCount", 0)

    fun savePageCount(count: Int) = prefs.edit().putInt("pageCount", count).apply()

    fun loadWeatherCache(): Weather? = runCatching {
        val o = JSONObject(prefs.getString("weatherCache", null) ?: return null)
        Weather(o.getDouble("c"), o.getInt("code"), o.getLong("t"))
    }.getOrNull()

    fun saveWeatherCache(w: Weather?) {
        prefs.edit().putString("weatherCache", w?.let { JSONObject().put("c", it.celsius).put("code", it.code).put("t", it.fetchedAt).toString() }).apply()
    }

    fun loadLibrary(): LibraryConfig = runCatching {
        val o = JSONObject(prefs.getString("library", null) ?: return LibraryConfig())
        fun map(key: String) = o.optJSONObject(key)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap()
        fun list(key: String) = o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        LibraryConfig(map("renamed"), map("overrides"), list("hidden").toSet(), list("order"), map("custom"))
    }.getOrDefault(LibraryConfig())

    fun saveLibrary(c: LibraryConfig) {
        val o = JSONObject()
            .put("renamed", JSONObject(c.renamed as Map<*, *>))
            .put("overrides", JSONObject(c.overrides as Map<*, *>))
            .put("hidden", JSONArray(c.hidden.toList()))
            .put("order", JSONArray(c.order))
            .put("custom", JSONObject(c.custom as Map<*, *>))
        prefs.edit().putString("library", o.toString()).apply()
    }

    fun loadSettings(): LauncherSettings {
        // Roomier defaults on tablets; anything the user has changed is read from storage as before.
        val d = if (isTablet) LauncherSettings(columns = 6, rows = 5, dockSlots = 6, drawerColumns = 7, folderColumns = 4) else LauncherSettings()
        return LauncherSettings(
            columns = prefs.getInt("columns", d.columns),
            rows = prefs.getInt("rows", d.rows),
            dockSlots = prefs.getInt("dockSlots", d.dockSlots),
            iconScale = prefs.getFloat("iconScale", d.iconScale),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            drawerColumns = prefs.getInt("drawerColumns", d.drawerColumns),
            themeMode = ThemeMode.entries.getOrElse(prefs.getInt("themeMode", 0)) { ThemeMode.System },
            hiddenApps = prefs.getStringSet("hiddenApps", emptySet()).orEmpty().toSet(),
            swipeDown = GestureAction.entries.getOrElse(prefs.getInt("swipeDown", d.swipeDown.ordinal)) { d.swipeDown },
            doubleTap = GestureAction.entries.getOrElse(prefs.getInt("doubleTap", d.doubleTap.ordinal)) { d.doubleTap },
            showDock = prefs.getBoolean("showDock", d.showDock),
            dockOpacity = prefs.getFloat("dockOpacity", d.dockOpacity),
            drawerOpacity = prefs.getFloat("drawerOpacity", d.drawerOpacity),
            iconShape = IconShape.entries.getOrElse(prefs.getInt("iconShape", d.iconShape.ordinal)) { d.iconShape },
            iconPack = prefs.getString("iconPack", d.iconPack).orEmpty(),
            themedIcons = prefs.getBoolean("themedIcons", d.themedIcons),
            labelSize = prefs.getFloat("labelSize", d.labelSize),
            labelLines = prefs.getInt("labelLines", d.labelLines),
            webSearch = prefs.getBoolean("webSearch", d.webSearch),
            searchProvider = SearchProvider.entries.getOrElse(prefs.getInt("searchProvider", 0)) { d.searchProvider },
            contactsSearch = prefs.getBoolean("contactsSearch", d.contactsSearch),
            homeSearchBar = prefs.getBoolean("homeSearchBar", d.homeSearchBar),
            predictions = prefs.getBoolean("predictions", d.predictions),
            wallpaperScroll = prefs.getBoolean("wallpaperScroll", d.wallpaperScroll),
            drawerIconScale = prefs.getFloat("drawerIconScale", d.drawerIconScale),
            drawerLabelSize = prefs.getFloat("drawerLabelSize", d.drawerLabelSize),
            showDrawerLabels = prefs.getBoolean("showDrawerLabels", d.showDrawerLabels),
            homePaddingH = prefs.getInt("homePaddingH", d.homePaddingH),
            homePaddingV = prefs.getInt("homePaddingV", d.homePaddingV),
            showPageDots = prefs.getBoolean("showPageDots", d.showPageDots),
            lockHome = prefs.getBoolean("lockHome", d.lockHome),
            defaultPage = prefs.getInt("defaultPage", d.defaultPage),
            hideStatusBar = prefs.getBoolean("hideStatusBar", d.hideStatusBar),
            darkStatusIcons = prefs.getBoolean("darkStatusIcons", d.darkStatusIcons),
            darkHomeText = prefs.getBoolean("darkHomeText", d.darkHomeText),
            drawerMode = DrawerMode.entries.getOrElse(prefs.getInt("drawerMode", 0)) { d.drawerMode },
            favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet(),
            drawerSort = DrawerSort.entries.getOrElse(prefs.getInt("drawerSort", 0)) { d.drawerSort },
            drawerCellPadding = prefs.getInt("drawerCellPadding", d.drawerCellPadding),
            drawerSideMargin = prefs.getInt("drawerSideMargin", d.drawerSideMargin),
            drawerTopPadding = prefs.getInt("drawerTopPadding", d.drawerTopPadding),
            autoShowKeyboard = prefs.getBoolean("autoShowKeyboard", d.autoShowKeyboard),
            hideDrawerSearch = prefs.getBoolean("hideDrawerSearch", d.hideDrawerSearch),
            drawerScroller = prefs.getBoolean("drawerScroller", d.drawerScroller),
            dockPages = prefs.getInt("dockPages", d.dockPages),
            dockRadius = prefs.getInt("dockRadius", d.dockRadius),
            dockTint = Tint.entries.getOrElse(prefs.getInt("dockTint", d.dockTint.ordinal)) { d.dockTint },
            dockBottomSpace = prefs.getInt("dockBottomSpace", d.dockBottomSpace),
            dockLabels = prefs.getBoolean("dockLabels", d.dockLabels),
            dockSearchBar = prefs.getBoolean("dockSearchBar", d.dockSearchBar),
            folderColumns = prefs.getInt("folderColumns", d.folderColumns),
            folderShape = IconShape.entries.getOrElse(prefs.getInt("folderShape", d.folderShape.ordinal)) { d.folderShape },
            folderTileOpacity = prefs.getFloat("folderTileOpacity", d.folderTileOpacity),
            folderBgOpacity = prefs.getFloat("folderBgOpacity", d.folderBgOpacity),
            folderTint = Tint.entries.getOrElse(prefs.getInt("folderTint", d.folderTint.ordinal)) { d.folderTint },
            showFolderLabels = prefs.getBoolean("showFolderLabels", d.showFolderLabels),
            roundedWidgets = prefs.getBoolean("roundedWidgets", d.roundedWidgets),
            smartspace = prefs.getBoolean("smartspace", d.smartspace),
            smartspaceCalendar = prefs.getBoolean("smartspaceCalendar", d.smartspaceCalendar),
            smartspaceBattery = prefs.getBoolean("smartspaceBattery", d.smartspaceBattery),
            notificationDots = prefs.getBoolean("notificationDots", d.notificationDots),
            notificationCount = prefs.getBoolean("notificationCount", d.notificationCount),
            badgeColor = Tint.entries.getOrElse(prefs.getInt("badgeColor", d.badgeColor.ordinal)) { d.badgeColor },
            weatherEnabled = prefs.getBoolean("weatherEnabled", d.weatherEnabled),
            weatherPlace = prefs.getString("weatherPlace", "").orEmpty(),
            weatherLat = prefs.getString("weatherLat", null)?.toDoubleOrNull() ?: 0.0,
            weatherLon = prefs.getString("weatherLon", null)?.toDoubleOrNull() ?: 0.0,
            weatherFahrenheit = prefs.getBoolean("weatherFahrenheit", d.weatherFahrenheit),
            smartspaceMedia = prefs.getBoolean("smartspaceMedia", d.smartspaceMedia),
            themeSchedule = ThemeSchedule.entries.getOrElse(prefs.getInt("themeSchedule", 0)) { d.themeSchedule },
            scheduleStart = prefs.getInt("scheduleStart", d.scheduleStart),
            scheduleEnd = prefs.getInt("scheduleEnd", d.scheduleEnd),
            // Existing installs already have a layout and have no need for the introduction.
            onboarded = prefs.getBoolean("onboarded", prefs.contains("items")),
            swipeUp = GestureAction.entries.getOrElse(prefs.getInt("swipeUp", d.swipeUp.ordinal)) { d.swipeUp },
            homePress = GestureAction.entries.getOrElse(prefs.getInt("homePress", d.homePress.ordinal)) { d.homePress },
            longPressEmpty = EmptyLongPress.entries.getOrElse(prefs.getInt("longPressEmpty", d.longPressEmpty.ordinal)) { d.longPressEmpty },
            lassoRadialMenu = prefs.getBoolean("lassoRadialMenu", d.lassoRadialMenu),
            gestureApps = runCatching {
                val o = JSONObject(prefs.getString("gestureApps", "{}").orEmpty())
                o.keys().asSequence().associateWith { o.getString(it) }
            }.getOrDefault(emptyMap()),
            fuzzySearch = prefs.getBoolean("fuzzySearch", d.fuzzySearch),
            hiddenAppsInSearch = prefs.getBoolean("hiddenAppsInSearch", d.hiddenAppsInSearch),
            webSuggestions = prefs.getBoolean("webSuggestions", d.webSuggestions),
            customSearchUrl = prefs.getString("customSearchUrl", "").orEmpty(),
            settingsSearch = prefs.getBoolean("settingsSearch", d.settingsSearch),
            colorStyle = ColorStyle.entries.getOrElse(prefs.getInt("colorStyle", d.colorStyle.ordinal)) { d.colorStyle },
            motionStyle = MotionStyle.entries.getOrElse(prefs.getInt("motionStyle", 0)) { d.motionStyle },
            hapticsEnabled = prefs.getBoolean("hapticsEnabled", d.hapticsEnabled),
            clockStyle = ClockStyle.entries.getOrElse(prefs.getInt("clockStyle", d.clockStyle.ordinal)) { d.clockStyle },
            nivoClockWeight = ClockWeight.entries.getOrElse(prefs.getInt("nivoClockWeight", d.nivoClockWeight.ordinal)) { d.nivoClockWeight },
            clockSeconds = prefs.getBoolean("clockSeconds", d.clockSeconds),
            clockGreeting = prefs.getBoolean("clockGreeting", d.clockGreeting),
            dockStyle = DockStyle.entries.getOrElse(prefs.getInt("dockStyle", d.dockStyle.ordinal)) { d.dockStyle },
            readabilityScrim = prefs.getBoolean("readabilityScrim", d.readabilityScrim),
            libraryPage = prefs.getBoolean("libraryPage", d.libraryPage),
            accent = AccentColor.entries.getOrElse(prefs.getInt("accent", d.accent.ordinal)) { d.accent },
            fontChoice = FontChoice.entries.getOrElse(prefs.getInt("fontChoice", 0)) { d.fontChoice },
            customFontPath = prefs.getString("customFontPath", "").orEmpty(),
            wrapLegacyIcons = prefs.getBoolean("wrapLegacyIcons", d.wrapLegacyIcons),
            iconOverrides = runCatching {
                val o = JSONObject(prefs.getString("iconOverrides", "{}").orEmpty())
                o.keys().asSequence().associateWith { o.getString(it) }
            }.getOrDefault(emptyMap()),
            labelOverrides = runCatching {
                val o = JSONObject(prefs.getString("labelOverrides", "{}").orEmpty())
                o.keys().asSequence().associateWith { o.getString(it) }
            }.getOrDefault(emptyMap()),
        )
    }

    fun saveSettings(s: LauncherSettings) {
        prefs.edit()
            .putInt("columns", s.columns)
            .putInt("rows", s.rows)
            .putInt("dockSlots", s.dockSlots)
            .putFloat("iconScale", s.iconScale)
            .putBoolean("showLabels", s.showLabels)
            .putInt("drawerColumns", s.drawerColumns)
            .putInt("themeMode", s.themeMode.ordinal)
            .putStringSet("hiddenApps", s.hiddenApps)
            .putInt("swipeDown", s.swipeDown.ordinal)
            .putInt("doubleTap", s.doubleTap.ordinal)
            .putBoolean("showDock", s.showDock)
            .putFloat("dockOpacity", s.dockOpacity)
            .putFloat("drawerOpacity", s.drawerOpacity)
            .putInt("iconShape", s.iconShape.ordinal)
            .putBoolean("webSearch", s.webSearch)
            .putInt("searchProvider", s.searchProvider.ordinal)
            .putBoolean("contactsSearch", s.contactsSearch)
            .putBoolean("homeSearchBar", s.homeSearchBar)
            .putBoolean("predictions", s.predictions)
            .putBoolean("wallpaperScroll", s.wallpaperScroll)
            .putFloat("drawerIconScale", s.drawerIconScale)
            .putFloat("drawerLabelSize", s.drawerLabelSize)
            .putBoolean("showDrawerLabels", s.showDrawerLabels)
            .putInt("homePaddingH", s.homePaddingH)
            .putInt("homePaddingV", s.homePaddingV)
            .putBoolean("showPageDots", s.showPageDots)
            .putBoolean("lockHome", s.lockHome)
            .putInt("defaultPage", s.defaultPage)
            .putBoolean("hideStatusBar", s.hideStatusBar)
            .putBoolean("darkStatusIcons", s.darkStatusIcons)
            .putBoolean("darkHomeText", s.darkHomeText)
            .putInt("drawerMode", s.drawerMode.ordinal)
            .putStringSet("favorites", s.favorites)
            .putInt("drawerSort", s.drawerSort.ordinal)
            .putInt("drawerCellPadding", s.drawerCellPadding)
            .putInt("drawerSideMargin", s.drawerSideMargin)
            .putInt("drawerTopPadding", s.drawerTopPadding)
            .putBoolean("autoShowKeyboard", s.autoShowKeyboard)
            .putBoolean("hideDrawerSearch", s.hideDrawerSearch)
            .putBoolean("drawerScroller", s.drawerScroller)
            .putInt("dockPages", s.dockPages)
            .putInt("dockRadius", s.dockRadius)
            .putInt("dockTint", s.dockTint.ordinal)
            .putInt("dockBottomSpace", s.dockBottomSpace)
            .putBoolean("dockLabels", s.dockLabels)
            .putBoolean("dockSearchBar", s.dockSearchBar)
            .putInt("folderColumns", s.folderColumns)
            .putInt("folderShape", s.folderShape.ordinal)
            .putFloat("folderTileOpacity", s.folderTileOpacity)
            .putFloat("folderBgOpacity", s.folderBgOpacity)
            .putInt("folderTint", s.folderTint.ordinal)
            .putBoolean("showFolderLabels", s.showFolderLabels)
            .putBoolean("roundedWidgets", s.roundedWidgets)
            .putBoolean("smartspace", s.smartspace)
            .putBoolean("smartspaceCalendar", s.smartspaceCalendar)
            .putBoolean("smartspaceBattery", s.smartspaceBattery)
            .putBoolean("notificationDots", s.notificationDots)
            .putBoolean("notificationCount", s.notificationCount)
            .putInt("badgeColor", s.badgeColor.ordinal)
            .putBoolean("weatherEnabled", s.weatherEnabled)
            .putString("weatherPlace", s.weatherPlace)
            .putString("weatherLat", s.weatherLat.toString())
            .putString("weatherLon", s.weatherLon.toString())
            .putBoolean("weatherFahrenheit", s.weatherFahrenheit)
            .putBoolean("smartspaceMedia", s.smartspaceMedia)
            .putInt("themeSchedule", s.themeSchedule.ordinal)
            .putInt("scheduleStart", s.scheduleStart)
            .putInt("scheduleEnd", s.scheduleEnd)
            .putBoolean("onboarded", s.onboarded)
            .putInt("swipeUp", s.swipeUp.ordinal)
            .putInt("homePress", s.homePress.ordinal)
            .putInt("longPressEmpty", s.longPressEmpty.ordinal)
            .putBoolean("lassoRadialMenu", s.lassoRadialMenu)
            .putString("gestureApps", JSONObject(s.gestureApps as Map<*, *>).toString())
            .putBoolean("fuzzySearch", s.fuzzySearch)
            .putBoolean("hiddenAppsInSearch", s.hiddenAppsInSearch)
            .putBoolean("webSuggestions", s.webSuggestions)
            .putString("customSearchUrl", s.customSearchUrl)
            .putBoolean("settingsSearch", s.settingsSearch)
            .putInt("motionStyle", s.motionStyle.ordinal)
            .putBoolean("hapticsEnabled", s.hapticsEnabled)
            .putInt("clockStyle", s.clockStyle.ordinal)
            .putInt("nivoClockWeight", s.nivoClockWeight.ordinal)
            .putBoolean("clockSeconds", s.clockSeconds)
            .putBoolean("clockGreeting", s.clockGreeting)
            .putInt("dockStyle", s.dockStyle.ordinal)
            .putBoolean("readabilityScrim", s.readabilityScrim)
            .putBoolean("libraryPage", s.libraryPage)
            .putInt("colorStyle", s.colorStyle.ordinal)
            .putInt("accent", s.accent.ordinal)
            .putInt("fontChoice", s.fontChoice.ordinal)
            .putString("customFontPath", s.customFontPath)
            .putBoolean("wrapLegacyIcons", s.wrapLegacyIcons)
            .putString("iconOverrides", JSONObject(s.iconOverrides as Map<*, *>).toString())
            .putString("iconPack", s.iconPack)
            .putBoolean("themedIcons", s.themedIcons)
            .putFloat("labelSize", s.labelSize)
            .putInt("labelLines", s.labelLines)
            .putString("labelOverrides", JSONObject(s.labelOverrides as Map<*, *>).toString())
            .apply()
    }

    /** Everything the launcher saved (layout, folders, settings, counts) plus custom icon images, as JSON. */
    fun exportJson(filesDir: java.io.File): String {
        val values = JSONObject()
        prefs.all.forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> return@forEach
            }
            values.put(key, entry)
        }
        val icons = JSONObject()
        java.io.File(filesDir, "icons").listFiles()?.forEach {
            icons.put(it.name, android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP))
        }
        return JSONObject().put("app", "nivo-launcher").put("version", 1).put("prefs", values).put("icons", icons).toString(2)
    }

    /** Replaces all saved data with a backup made by [exportJson]. Returns false if the file isn't one. */
    fun importJson(json: String, filesDir: java.io.File): Boolean = runCatching {
        val root = JSONObject(json)
        require(root.optString("app") == "nivo-launcher")
        val values = root.getJSONObject("prefs")
        val editor = prefs.edit().clear()
        values.keys().forEach { key ->
            val entry = values.getJSONObject(key)
            when (entry.getString("t")) {
                "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                "i" -> editor.putInt(key, entry.getInt("v"))
                "l" -> editor.putLong(key, entry.getLong("v"))
                "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                "s" -> editor.putString(key, entry.getString("v"))
                "ss" -> editor.putStringSet(key, entry.getJSONArray("v").let { a -> List(a.length()) { a.getString(it) }.toSet() })
            }
        }
        val dir = java.io.File(filesDir, "icons").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        root.optJSONObject("icons")?.let { icons ->
            icons.keys().forEach { name ->
                if ('/' !in name) java.io.File(dir, name).writeBytes(android.util.Base64.decode(icons.getString(name), android.util.Base64.NO_WRAP))
            }
        }
        editor.commit()
    }.getOrDefault(false)

    private companion object {
        const val PAGELET_KEY = "pagelet."
        const val KEY_ITEMS = "items"
        const val KEY_FOLDERS = "folders"
    }
}
