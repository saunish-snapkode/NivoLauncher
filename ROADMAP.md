# Nivo Launcher roadmap

Goal: feature parity with Lawnchair's user-facing functionality, written from scratch
(no Lawnchair / Launcher3 source, resources or assets are copied; only behaviour is matched).

See FEATURE_GAP.md for the full line-by-line comparison with Lawnchair.

## Done
- Home screen: paged grid, dock, drag & drop, swap, edge page flip, remove zone
- App drawer: swipe up, search, hide apps, add to home, per-app shortcuts menu
- Folders (create by dropping, rename, move out, dissolve)
- Settings: grid size, dock slots, icon size, labels, drawer columns, theme, hidden apps

## Phase 1 — gestures, dock, drawer, icon shapes
- [x] Gestures: swipe down (notifications / lock / drawer), double-tap (lock / notifications / drawer)
- [x] Dock: show/hide, background opacity
- [x] App drawer: background opacity
- [x] Icon shapes: original, circle, squircle, rounded square, teardrop

## Phase 2 — icons & looks
- [x] Icon packs (ADW/Nova appfilter.xml), per-app label rename
- [ ] Per-app icon picker (choose any drawable from a pack)
- [x] Themed (monochrome) icons (Android 13+)
- [ ] Material You color style options
- [x] Label lines, label text size
- [ ] Custom fonts
- [x] Wallpaper scroll
- [ ] Wallpaper depth, status bar style, home padding

## Phase 3 — widgets & search
- [x] App widget host: add (bind + configure), resize, remove, widget picker
- [ ] Drag to move widgets, work-profile widgets, resize by handles
- [ ] "At a glance" widget (date, next calendar event)
- [x] Search: contacts, web search, search providers, search bar on home
- [ ] Live web suggestions (type-ahead), search history
- [x] Suggested apps row (on-device launch counts)
- [ ] Predictions on the home screen, time/place-aware predictions

## Phase 4 — drawer & folders depth
- [ ] Drawer sort modes, A–Z fast scroller, row height, work-profile tab + pause
- [ ] Drawer folders, folder icon shape/opacity
- [ ] Multiple dock modes, app-drag to "App info" / "Uninstall" drop targets
- [ ] Notification dots / badges (notification listener)

## Phase 5 — system
- [ ] Backup & restore (export/import layout + settings)
- [ ] Gestures to open an app / app shortcuts; vibration feedback level
- [ ] Rotation lock option, sleep mode, adaptive/auto-add shortcuts
- [ ] Pinned shortcut requests, install-time auto-add
- [ ] Release signing, new launcher icon, tests

## Out of scope (needs system/root access or proprietary services)
Quickstep / Recents integration, QuickSwitch, Google feed overlay, Smartspacer-only features.
