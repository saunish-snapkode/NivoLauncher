package com.snapkode.nivolauncher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap

/** Stable identity of a launchable activity for a given (possibly work-profile) user. */
data class AppKey(val component: ComponentName, val userSerial: Long) {
    fun encode(): String = "${component.flattenToString()}#$userSerial"

    companion object {
        fun decode(raw: String): AppKey? {
            val split = raw.lastIndexOf('#')
            if (split < 0) return null
            val component = ComponentName.unflattenFromString(raw.substring(0, split)) ?: return null
            val serial = raw.substring(split + 1).toLongOrNull() ?: return null
            return AppKey(component, serial)
        }
    }
}

data class AppInfo(
    val key: AppKey,
    val label: String,
    val user: UserHandle,
    val icon: ImageBitmap,
    val installTime: Long = 0L,
) {
    val id: String get() = key.encode()
    val packageName: String get() = key.component.packageName
}

/** Where an item lives. [page] == [DOCK] means [col] is the dock slot. */
data class HomeItem(
    val id: String,
    val page: Int,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
) {
    val inDock: Boolean get() = page == DOCK

    fun occupies(page: Int, col: Int, row: Int) =
        this.page == page && col in this.col until this.col + spanX && row in this.row until this.row + spanY

    companion object {
        const val DOCK = -1
    }
}

const val FOLDER_PREFIX = "folder:"

fun isFolderId(id: String) = id.startsWith(FOLDER_PREFIX)

const val WIDGET_PREFIX = "widget:"

fun isWidgetId(id: String) = id.startsWith(WIDGET_PREFIX)

fun widgetItemId(appWidgetId: Int) = "$WIDGET_PREFIX$appWidgetId"

fun appWidgetIdOf(id: String): Int = id.removePrefix(WIDGET_PREFIX).toInt()

/** Widgets drawn by the launcher itself (as opposed to system app widgets). Ids look like "nivo:clock:<random>". */
const val NIVO_WIDGET_PREFIX = "nivo:"

fun isNivoWidgetId(id: String) = id.startsWith(NIVO_WIDGET_PREFIX)

fun newNivoClockId() = "${NIVO_WIDGET_PREFIX}clock:${java.util.UUID.randomUUID().toString().take(8)}"

/** Any widget, system or launcher-drawn: something that spans cells, can be resized, and can't be grouped into a folder. */
fun isAnyWidgetId(id: String) = isWidgetId(id) || isNivoWidgetId(id)

/** A named group of apps. The folder itself is placed on the home screen as a [HomeItem]. */
data class HomeFolder(val id: String, val name: String, val appIds: List<String>)

enum class Tint(val label: String) { Black("Black"), White("White"), Accent("Accent"), Red("Red") }

enum class ColorStyle(val label: String) {
    Dynamic("Follow wallpaper (Material You)"),
    Custom("Custom accent"),
    Nivo("Nivo signature"),
}

enum class MotionStyle(val label: String) {
    Default("Default"), Smooth("Smooth"), Snappy("Snappy"), Calm("Calm"), Reduced("Reduced"),
}

/** How heavy the strokes of the Nivo clock widget are drawn. */
enum class ClockWeight(val label: String) { Light("Light"), Bold("Bold") }

enum class ClockStyle(val label: String) {
    Off("Off"), Hero("Hero"), Minimal("Minimal"), Compact("Compact"), Analog("Analog"),
}

enum class DockStyle(val label: String) { Floating("Floating"), Solid("Solid"), Minimal("Minimal") }

enum class AccentColor(val label: String, val argb: Long) {
    Indigo("Indigo", 0xFF3F51B5),
    Blue("Blue", 0xFF3782F0),
    Teal("Teal", 0xFF00897B),
    Green("Green", 0xFF43A047),
    Orange("Orange", 0xFFF4511E),
    Red("Red", 0xFFE53935),
    Pink("Pink", 0xFFD81B60),
    Purple("Purple", 0xFF8E24AA),
    Coral("Coral", 0xFFD2573A),
}

enum class FontChoice(val label: String) {
    System("System default"),
    Serif("Serif"),
    Mono("Monospace"),
    Cursive("Cursive"),
    Custom("Custom font file"),
}

enum class DrawerMode(val label: String) { Grid("Grid"), List("List"), Categories("Categories") }

enum class DrawerSort(val label: String) {
    AToZ("A to Z"),
    ZToA("Z to A"),
    Recent("Recently installed"),
    MostUsed("Most used"),
}

enum class ThemeMode { System, Light, Dark, Amoled }

enum class GestureAction(val label: String) {
    None("Do nothing"),
    Notifications("Open notifications"),
    LockScreen("Lock screen"),
    AppDrawer("Open app drawer"),
    QuickSettings("Open quick settings"),
    Search("Open search"),
    Assistant("Open assistant"),
    Recents("Open recent apps"),
    OpenApp("Open an app…"),
    Lasso("Select icons (lasso)"),
}

/** What pressing and holding an empty part of the home screen does. */
enum class EmptyLongPress(val label: String) {
    EditMenu("Open the edit menu"),
    Lasso("Start lasso selection"),
}

enum class IconShape(val label: String) {
    Original("System default"),
    Circle("Circle"),
    Squircle("Squircle"),
    RoundedSquare("Rounded square"),
    Teardrop("Teardrop"),
}

enum class SearchProvider(val label: String, val urlTemplate: String) {
    Google("Google", "https://www.google.com/search?q=%s"),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    Brave("Brave", "https://search.brave.com/search?q=%s"),
    Bing("Bing", "https://www.bing.com/search?q=%s"),
    Custom("Custom…", ""),
}

data class LauncherSettings(
    val columns: Int = 4,
    val rows: Int = 4,
    val dockSlots: Int = 5,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val drawerColumns: Int = 4,
    val themeMode: ThemeMode = ThemeMode.System,
    val hiddenApps: Set<String> = emptySet(),
    val swipeDown: GestureAction = GestureAction.Notifications,
    val doubleTap: GestureAction = GestureAction.LockScreen,
    val showDock: Boolean = true,
    val dockOpacity: Float = 0.4f,
    val drawerOpacity: Float = 0.96f,
    val iconShape: IconShape = IconShape.Circle,
    val iconPack: String = "",
    val themedIcons: Boolean = false,
    val labelSize: Float = 12f,
    val labelLines: Int = 1,
    val labelOverrides: Map<String, String> = emptyMap(),
    val webSearch: Boolean = true,
    val searchProvider: SearchProvider = SearchProvider.Google,
    val contactsSearch: Boolean = false,
    val homeSearchBar: Boolean = false,
    val predictions: Boolean = true,
    val wallpaperScroll: Boolean = true,
    val drawerIconScale: Float = 1f,
    val drawerLabelSize: Float = 12f,
    val showDrawerLabels: Boolean = true,
    val homePaddingH: Int = 0,
    val homePaddingV: Int = 0,
    val showPageDots: Boolean = true,
    val lockHome: Boolean = false,
    val defaultPage: Int = 0,
    val hideStatusBar: Boolean = false,
    val darkStatusIcons: Boolean = false,
    val darkHomeText: Boolean = false,
    val drawerSort: DrawerSort = DrawerSort.AToZ,
    val drawerMode: DrawerMode = DrawerMode.Grid,
    val favorites: Set<String> = emptySet(),
    val drawerCellPadding: Int = 10,
    val drawerSideMargin: Int = 8,
    val drawerTopPadding: Int = 0,
    val autoShowKeyboard: Boolean = false,
    val hideDrawerSearch: Boolean = false,
    val drawerScroller: Boolean = true,
    val dockPages: Int = 1,
    val dockRadius: Int = 28,
    val dockTint: Tint = Tint.Black,
    val dockBottomSpace: Int = 0,
    val dockLabels: Boolean = false,
    val dockSearchBar: Boolean = false,
    val folderColumns: Int = 3,
    val folderShape: IconShape = IconShape.RoundedSquare,
    val folderTileOpacity: Float = 0.34f,
    val folderBgOpacity: Float = 1f,
    val folderTint: Tint = Tint.White,
    val showFolderLabels: Boolean = true,
    val roundedWidgets: Boolean = true,
    val smartspace: Boolean = true,
    val smartspaceCalendar: Boolean = false,
    val smartspaceBattery: Boolean = true,
    val notificationDots: Boolean = true,
    val notificationCount: Boolean = false,
    val badgeColor: Tint = Tint.Accent,
    val colorStyle: ColorStyle = ColorStyle.Nivo,
    val accent: AccentColor = AccentColor.Blue,
    val fontChoice: FontChoice = FontChoice.System,
    val customFontPath: String = "",
    val wrapLegacyIcons: Boolean = false,
    val iconOverrides: Map<String, String> = emptyMap(),
    val motionStyle: MotionStyle = MotionStyle.Default,
    val hapticsEnabled: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.Hero,
    val nivoClockWeight: ClockWeight = ClockWeight.Light,
    val clockSeconds: Boolean = false,
    val clockGreeting: Boolean = false,
    val dockStyle: DockStyle = DockStyle.Minimal,
    val readabilityScrim: Boolean = true,
    val libraryPage: Boolean = true,
    val weatherEnabled: Boolean = false,
    val weatherPlace: String = "",
    val weatherLat: Double = 0.0,
    val weatherLon: Double = 0.0,
    val weatherFahrenheit: Boolean = java.util.Locale.getDefault().country == "US",
    val smartspaceMedia: Boolean = true,
    val themeSchedule: ThemeSchedule = ThemeSchedule.Off,
    val scheduleStart: Int = ThemeScheduleDefaults.EVENING_START,
    val scheduleEnd: Int = ThemeScheduleDefaults.MORNING_END,
    val onboarded: Boolean = true,
    val swipeUp: GestureAction = GestureAction.AppDrawer,
    val homePress: GestureAction = GestureAction.None,
    val longPressEmpty: EmptyLongPress = EmptyLongPress.EditMenu,
    /** Show the lasso's actions as a radial menu around the selection instead of a toolbar. */
    val lassoRadialMenu: Boolean = false,
    /** App chosen for each "Open an app" gesture, keyed by gesture name. */
    val gestureApps: Map<String, String> = emptyMap(),
    val fuzzySearch: Boolean = true,
    val hiddenAppsInSearch: Boolean = false,
    val webSuggestions: Boolean = false,
    val customSearchUrl: String = "",
    val settingsSearch: Boolean = true,
)
