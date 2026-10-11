# Pagelets — Phase 0 audit

Date: 2026-10-11

## 1. The spec's premise does not match this repo

The brief assumes **Lawnchair / Launcher3** (`Workspace`, `CellLayout`, `LauncherModel`, `LauncherProvider`, a favorites SQLite
table, `PagedView`, `NotificationListener` from Launcher3). **Nivo Launcher is not based on any of that.** It is a standalone
Kotlin / Jetpack Compose launcher. Everything in the brief that names a Launcher3 class therefore needs an equivalent in
Nivo's own architecture. The mapping below is what the implementation follows.

| Brief assumes | What Nivo actually has |
|---|---|
| Lawnchair / Launcher3 base | None. `com.snapkode.nivolauncher`, single `app` module |
| `minSdk` / `targetSdk` | `minSdk 24`, `targetSdk 37`, `compileSdk 37`; AGP 9.4.1, Kotlin 2.2.10, Compose BOM 2025.10.00 |
| Compose "already used by settings" | Compose is used for **everything** (home, drawer, settings, lasso). There are no Views except `AndroidView` hosting `AppWidgetHostView` |
| `Workspace` (PagedView) + `CellLayout` | `HorizontalPager` in `ui/HomeScreen.kt`. Pager order is always: News feed (optional) → workspace pages → App Library (optional). A workspace page is `WorkspacePage` (a `BoxWithConstraints` that places items at `col*cellW, row*cellH`) |
| Screen IDs, `LauncherModel` | `LauncherViewModel` owns `items: StateFlow<List<HomeItem>>`, `folders`, `pages` (derived from items and an explicit stored `pageCount`) |
| Favorites DB (`LauncherProvider` / `ModelDbController`), DB version, migrations | **No database.** `LayoutStore` keeps layout and settings as JSON in one `SharedPreferences` file (`nivo_launcher`) |
| Item types (`ITEM_TYPE_*`) | `HomeItem(id, page, col, row, spanX, spanY)`; the **type is encoded in the id prefix**: plain `component#user` = app, `folder:` = folder, `widget:<appWidgetId>` = system widget, `nivo:<kind>:<uuid>` = a widget drawn by the launcher itself (today only `nivo:clock:`) |
| Custom first-party view on the workspace (smartspace/QSB) | `nivo:` items already exist and are rendered in `WidgetCell` (`ui/WidgetUi.kt`) by `NivoClockWidget`. They are laid out, moved, resized, removed, restored and counted as pages exactly like system widgets (`isAnyWidgetId`) |
| Hotseat / QSB fade on page scroll | The dock is `DockBar` inside a `Column(Modifier.collapseBy(libraryFraction))`. `libraryFraction` is a pure function of `pagerState.currentPage + currentPageOffsetFraction`, so the dock **already shrinks and fades proportionally to scroll** for the Library and News pages. There is no QSB: search is the drawer's search field |
| Page-scroll callbacks | `pagerState` (`currentPage`, `currentPageOffsetFraction`, `isScrollInProgress`) read via `snapshotFlow` |
| Weather / smartspace providers to reuse | `data/Weather.kt` (`WeatherSource`) — **Open-Meteo**, current temperature + WMO code only; and `ui/SmartspaceUi.kt`. See "Risks" |
| Launcher3 `NotificationListener` | `NivoNotificationListener` (manifest service; `NotificationListener.kt`) exists for notification dots. Reusable for media sessions in a later phase |
| Backup/restore format | `LayoutStore.exportJson` serialises **every key** of the `nivo_launcher` SharedPreferences plus custom icon files into one JSON document (`"app":"nivo-launcher"`). `importJson` clears and rewrites all keys. Anything stored in that same preference file is therefore backed up and restored automatically |
| Existing tests | JVM unit tests (`app/src/test`, 125 passing), emulator tests (`app/src/androidTest`, drive real two-finger gestures through the launcher). No Paparazzi/Roborazzi yet |

## 2. Integration approach (adapted)

The brief's recommended approach is, in spirit, exactly what Nivo's existing `nivo:` widget mechanism does, so that is what
is used. **No second engine, no new database, no change to the workspace model.**

* A Pagelet is a **`HomeItem` whose id is `nivo:pagelet:<TYPE>:<uuid8>`**, with `col = 0, row = 0, spanX = columns, spanY = rows`
  on a page of its own. Because it is a `nivo:` id it is already treated as a widget by drop logic, lasso, page overview,
  removal, restore and trailing-page trimming. A page that is entirely covered by a widget accepts no auto-placement
  (`freeCell`) and no icon drops (the cells are occupied by a widget, which is a hard stop for `widgetLayout`).
* **Config storage:** one `SharedPreferences` key per Pagelet, `pagelet.<id>` = JSON, **in the same preference file as the
  layout**. This replaces the brief's "DB migration": there is no DB, and sharing the file gives backup/restore for free.
  Unknown or corrupt JSON degrades to defaults (tested).
* **Rendering:** the pager item for a Pagelet page renders `PageletHost` (Compose, no `ComposeView` interop needed).
  Because a Pagelet fills the whole page *including the area behind the status bar and the dock*, a full-screen
  `PageletBackdrop` is drawn behind the pager, translated with the scroll position, so the sky can bleed under the bars.
* **Dock hiding:** the existing `collapseBy` mechanism is generalised: the fraction is `max(library, news, pagelet)`.
  `pagelet` is the scroll-proportional closeness to any Pagelet page whose config has `hideDock = true`. This is
  proportional by construction (not a hard toggle), and never touches the system gesture area.
* **Visibility gating:** a `LocalPageletActive` composition local is true only when the Pagelet's page is the current (or
  being-scrolled-to) page, the Activity is resumed, and the screen is on. Animations and data collection are keyed on it.
* **Fallback** (a separate page engine) is **not** needed and not used.

## 3. Per-phase scope decisions

* **Phase 1 (framework)** and **Phase 2 (Sky UI + fake provider + debug scene switcher)** are implemented in this change.
* **Phase 3 (real weather)**: the app-side `WeatherSource` interface and MET Norway client + the proxy are specified in the
  brief; they depend on hosting and a contact address that only the owner can supply (MET Norway requires an identifying
  User-Agent with real contact details and forbids per-install direct calls). Not started; see "Open decisions".
* **Phases 4–8** (wellbeing, agenda, now-playing, move, headlines): not started. The framework registers all six types;
  the gallery shows the five unbuilt ones as "Coming soon" rather than adding blank pages.

## 4. Risks and constraints found

1. **Existing weather source is Open-Meteo's hosted free tier**, which the brief says is non-commercial only. It currently
   feeds the clock/smartspace line. It is **not** touched here; Phase 3 introduces the `WeatherSource` interface so both can
   be moved to MET Norway behind the proxy. Flagged for a licensing decision.
2. **No screenshot-test library** is present. Roborazzi/Paparazzi would need new Gradle plugins; the scene definitions are
   pure data, so scene correctness (palette rules, mapping tables) is covered by JVM tests, and visuals are checked on the
   emulator with the debug scene switcher.
3. **Roboto Flex variable font** is not bundled. Variable-font axes (`wght`, `wdth`, `opsz`) via `FontVariation` need API 26+
   and a variable font file. Adding the font file is a licensing/asset decision (it is OFL). Until then the Sky clock uses
   the platform sans with `wght` variation where the system font supports it (Android's default Roboto is variable on
   API 31+), and falls back to a light static weight below that.
4. `minSdk 24`: `RenderEffect` blur needs API 31; below that the radial-gradient washes are used alone, as the brief says.
5. Multi-touch page turning, drag/drop and lasso must keep working with a full-grid widget on a page; covered by the
   existing emulator tests plus new Pagelet tests.

## 5. Open decisions for the owner

* Hosting + contact details for the MET Norway proxy (Phase 3).
* Whether to bundle Roboto Flex (OFL) for exact typography.
* Whether to add Roborazzi/Paparazzi (adds Gradle plugins and golden images to the repo).
