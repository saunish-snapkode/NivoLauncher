# Implementation progress

Date: 2026-10-11. Verified on an Android 17-class emulator (software GPU) and, for earlier work, a OnePlus CPH2487.

## Done in this milestone
* Nivo design system: palettes (light / dark / AMOLED), accent presets, type scale, radius/space/elevation tokens.
* Colour style "Nivo signature" is now the default; Material You and custom accent remain options.
* Nivo Motion presets + reduced-motion handling + haptics toggle; settings page "Motion & haptics" with tap-to-replay preview.
* Signature clock (Hero / Minimal / Compact / Analog, optional seconds & greeting) with battery-friendly ticking.
* Dock styles: Floating (soft shadow), Solid, Minimal. Readability shading behind clock/dock.
* App Library page right of Home with editable categories (see `app-library.md`).
* Settings: dashboard + live previews (earlier), plus Library and Motion categories, clock/dock-style options.
* Edit mode: long-press empty space opens a bottom sheet (Wallpaper, Widgets, Pages, Style, Settings) and the home screen scales back.
* Page overview: miniature previews of every page; tap to open, set home page, reorder, delete empty pages, add pages. Empty pages now persist (explicit `pageCount`); trailing empty pages are trimmed after drops/removals. Pinch-in on Home opens the overview.
* Drawer modes: Grid (default), Compact List, Category View (uses the App Library classification, respects the Personal/Work tab); Favourites row pinned from the long-press menu. Live preview in settings follows the chosen mode.
* Folders: the open folder grows from the tapped tile and shrinks back into it; its icons pop in one after another (per motion preset); the tile hides while open.
* Onboarding: five skippable steps (welcome, look, home with live preview, gestures, default-home request via the Android role prompt). Shown on fresh installs only; replay from Settings → Backup & system.
* Accessibility pass: Home icons were invisible to screen readers (raw pointer input) — fixed with real button semantics; see `accessibility.md`.
* Performance pass: repeatable probe (`perf.py`), measured before/after, drawer jank cut roughly in half on the emulator; see `performance.md` (device numbers still missing).
* Tests: 14 unit tests pass (`./gradlew :app:testDebugUnitTest`). Release build compiles.

## Verified visually (screenshots)
Home with hero clock, App Library, expanded category, settings dashboard, Folders/Home pages, drawer.
**Not** visually verified: pinch-in gesture (needs a real two-finger touch), delete/reorder in the overview, Analog/Minimal/Compact clocks, Solid/Minimal dock, AMOLED, Motion/Library settings pages,
animation smoothness on a real high-refresh display.

## Known issues
* Library expansion leaves a strip of wallpaper above it.
* Library page consumes vertical swipes (no drawer/notification gesture there).
* Context menus still use stock Material dropdowns (not yet restyled).
* Previews for Library/Motion are simple cards, not full surface renders.

## Not started (continuation plan, in order)
1. Page overview polish: drag-to-reorder (currently arrow buttons), animated card scale from the live workspace, per-page wallpaper preview.
2. Drawer: restyle search results (grouping, app-icon alignment) and add per-section collapse in Category view.
3. Folder polish: shared-element icon travel (icons currently fade/scale in rather than flying from the tile), folder reorder/sort, per-folder appearance.
4. Onboarding: verify the real `RoleManager` prompt on a device where Nivo is not yet the default (the emulator already had it set).
5. Theme: scheduled switching, wallpaper-derived accent for the Nivo scheme, cross-fade on change.
6. Weather card (needs a data source decision), media card (notification listener), wellbeing card.
7. Real TalkBack session on a device; non-drag way to move icons; tablet / foldable layout pass.
8. Run `perf.py` on a real phone; add a Macrobenchmark + Baseline Profile module; instrumented gesture tests.
9. Replace remaining literal dp values with tokens; restyle dialogs and menus (NivoDialog, NivoBottomSheet).
