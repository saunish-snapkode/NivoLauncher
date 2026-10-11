# Nivo vs Lawnchair — feature gap

Audit date: 2026-10-10. Compared against Lawnchair's preference list and gesture handlers
(behaviour only; no code or assets reused). ✅ done · 🟡 partial · ⬜ not started · 🚫 out of scope

## Home screen
| Feature | Status |
|---|---|
| Grid columns / rows | ✅ |
| Icon size, label size, label lines, show labels | ✅ separate home and drawer values (folders reuse home) |
| Drag & drop, swap, page flip, remove zone | ✅ |
| Page indicator | 🟡 on/off only; height / style missing |
| Wallpaper scroll with pages | ✅ |
| Wallpaper depth effect / blur | ⬜ |
| Home padding (horizontal / vertical) | ✅ (top / widget padding ⬜) |
| Lock home screen, default home page | ✅ (remember page position ⬜) |
| Infinite (looping) page scrolling | ⬜ deferred: conflicts with pages added mid-drag |
| Show / hide status bar, dark status-bar icons | ✅ (status-bar clock ⬜) |
| Workspace text colour | 🟡 white or dark only |
| Add / remove / reorder pages, edit mode | ⬜ |
| Pinned shortcuts (from other apps), auto-add shortcuts on install | ⬜ |
| Screen rotation setting | ⬜ |
| Deck layout (drawer on home) | ⬜ |
| Long-press popup: app shortcuts, info, uninstall | ✅ |
| Popup extras: lock / edit home screen, system settings entry, popup order | ⬜ |
| Drop targets: App info / Uninstall / Pause | ⬜ |
| Notification dots and counts (+ colour) | ✅ built; listener connects, badge drawing untested on a real notification (long-press notification previews ⬜) |

## Dock
| Feature | Status |
|---|---|
| Show / hide, slot count, opacity | ✅ |
| Multiple dock pages, bottom spacing | ✅ (extra dock rows ⬜) |
| Background corner radius, colour | ✅ (insets ⬜) |
| Dock search bar | ✅ (style / corner / provider options ⬜) |
| Labels in dock | ✅ |

## Widgets
| Feature | Status |
|---|---|
| Add (bind + configure), resize via dialog, remove, picker | ✅ |
| Drag to move (same page), resize handles | ✅ (moving across pages ⬜) |
| Work-profile widgets | ✅ built, untested (no work profile on test devices) |
| Rounded widget corners | ✅ (allow overlap, force resize, unlimited size ⬜) |
| "At a glance" (date, next calendar event, battery) | ✅ (now playing, torch, time options ⬜) |

## Folders
| Feature | Status |
|---|---|
| Create, rename, open, drag out, dissolve | ✅ |
| Folder columns | ✅ (rows / scrolling ⬜) |
| Folder shape, colour, tile / open-folder opacity | ✅ |
| Labels inside folders | ✅ on/off (separate size ⬜) |
| Drawer folders (group apps in the drawer) | ✅ |

## App drawer
| Feature | Status |
|---|---|
| Swipe up, search, hide apps, add to home | ✅ |
| Columns, background opacity | ✅ |
| Suggested apps row | ✅ (launch counts; Lawnchair also offers usage-stats prediction) |
| Work-profile tab, pause work apps | ✅ built, untested (no work profile on test devices); tab colours ⬜ |
| Fast scroller (A–Z) with toggle | ✅ |
| Sort modes (A–Z, Z–A, recent, most used), row spacing, side margins, top padding | ✅ |
| Auto-show keyboard, hide search bar | ✅ (haptics ⬜) |
| Two-line labels, show component names | 🟡 two-line labels done |
| Background colour / lightness, colored backgrounds | ⬜ |

## Search
| Feature | Status |
|---|---|
| App search, contacts, web search with provider choice | ✅ |
| Fuzzy search | ✅ (algorithm choice ⬜) |
| Hidden apps in search toggle | ✅ |
| Live web suggestions (opt-in), custom provider URL | ✅ built, suggestions untested (needs network) |
| Settings entries | ✅ (files, recent results, result-count limits ⬜) |
| Search bar on home | ✅ |

## Icons & theming
| Feature | Status |
|---|---|
| Icon packs, icon shape (5 shapes) | ✅ |
| Themed (monochrome) icons | ✅ Android 13+ |
| Custom icon / folder shapes (freeform editor) | ⬜ preset shapes only |
| Per-app icon picker and label edit | ✅ (picture or icon-pack icon; gallery path untested) |
| Wrap old-style icons in the shape (auto-adaptive) | ✅ (shadow, transparent background ⬜) |
| Themed icon packs | ⬜ |
| Colour style: wallpaper-based or custom accent (8 colours) | ✅ (more Material You variants, stroke colour ⬜) |
| Light / dark / system | ✅ |
| Font selection: system, serif, mono, cursive, custom font file | ✅ custom file untested |
| Reload icons on demand | ✅ |

## Gestures
| Feature | Status |
|---|---|
| Swipe down, double tap (notifications / lock / drawer) | ✅ |
| Swipe up, home-button press (when already home) | ✅ built, untested (long-press customisation ⬜) |
| Open app, assistant, quick settings, search, recents | ✅ built, untested on device |
| Sleep / lock variants (sleep mode) | 🟡 accessibility lock only |
| Predictive back for drawer | ✅ |

## System & misc
| Feature | Status |
|---|---|
| Backup / restore (layout, folders, settings, custom icons) | ✅ built, untested on device |
| Default-home shortcut, wallpaper picker | ✅ |
| Foldable / unfolded layouts, tablet taskbar | ⬜ |
| Closing-app overlay animation | ⬜ |
| Release signing (keystore.properties), launcher icon, unit tests | ✅ (crash/debug menu ⬜; release APK builds but wasn't run on a device) |

## 🚫 Out of scope (needs system / root access or proprietary services)
Quickstep & Recents integration, QuickSwitch, Google feed overlay, Smartspacer-only integration,
in-app auto-updater for nightly builds.

## Suggested order
1. ~~**Home polish batch**~~ ✅ done (2026-10-10)
2. ~~**Drawer batch**~~ ✅ done (2026-10-10)
3. ~~**Dock / folders batch**~~ ✅ done (2026-10-10)
4. ~~**Widgets batch**~~ ✅ done (2026-10-10)
5. ~~**Notifications batch**~~ ✅ done (2026-10-10)
6. ~~**Theming batch**~~ ✅ done (2026-10-10)
7. ~~**Search & gestures batch**~~ ✅ done (2026-10-10)
8. ~~**Backup / restore, release polish**~~ ✅ done (2026-10-10)
