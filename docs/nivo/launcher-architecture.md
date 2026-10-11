# Launcher architecture (Phase 0 audit)

**Important correction to the brief:** this repository is *not* based on Lawnchair or Launcher3. Nivo is a
from-scratch Kotlin + Jetpack Compose launcher written clean-room (Lawnchair was used only as a feature
reference; no source, resources or assets were copied). There is therefore no Launcher3 workspace engine,
Lawnfeed integration, Quickstep layer or upstream version to preserve. Everything below describes the real code.

## Stack
Kotlin, Jetpack Compose (Material 3 + extended icons), AGP 9.4, compileSdk 37, minSdk 24, no third-party runtime libs.
UI is 100% Compose inside one `MainActivity` (singleTask HOME activity).

## Modules / packages (`com.snapkode.nivolauncher`)
| Area | Files |
|---|---|
| Data | `data/AppRepository` (LauncherApps loader, icon rendering), `LayoutStore` (SharedPreferences JSON), `Models`, `IconRenderer`, `IconPack`, `AppLibrary` (classifier + groups), `SearchTools`, `ContactsSearch`, `CalendarSource`, `NivoPaletteSpec` |
| State | `LauncherViewModel` — single source of truth: items, folders, drawer folders, library config, settings, launch counts, widgets host |
| Home | `ui/HomeScreen` (pager, drag & drop, dock, widgets), `NivoClock`, `SmartspaceUi`, `FolderUi`, `WidgetUi` |
| Drawer | `ui/AppDrawer`, `DrawerState` |
| Library | `ui/AppLibrary` |
| Settings | `ui/Settings`, `SettingsComponents`, `PreviewScenes` |
| Design system | `ui/Theme`, `ui/design/NivoTokens`, `ui/design/NivoMotion` |
| Services | `LockScreenService` (accessibility, lock/recents), `NivoNotificationListener` (badges) |

## Key behaviours
* Home layout = list of `HomeItem(id, page, col, row, spanX, spanY)`; dock items use `page = -1`.
* Pager pages: Home (workspace page 0), App Library (optional, index 1), then further workspace pages.
* Persistence: one `SharedPreferences` file; backup/restore exports it as JSON plus custom icon images.
* Icons are rendered once to bitmaps (shape, pack, themed, overrides) and cached in `AppRepository`.

## Not present (and why)
* Launcher3 `Workspace`, `Hotseat`, Quickstep/Recents, Lawnfeed → never part of this codebase.
* Google Discover: Google exposes the left-page feed only to privileged/signed launchers through a private
  overlay API. A third-party launcher cannot host it. See `navigation.md`.
