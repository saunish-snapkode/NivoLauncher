# Accessibility

## What was fixed in the 2026-10-11 pass
| Problem found | Fix |
|---|---|
| Home-screen icons and folders used raw pointer input, so a screen reader saw **no clickable items** on Home | `DragCell` now exposes `Role.Button`, a content description, an *Open* action and a *More options* (long-press) action |
| Opening the drawer needed a swipe | Added an "Open all apps" semantic button at the bottom of Home |
| Edit-mode tap-away layer was unnamed | Labelled "Close editing" |
| App name read twice (icon + label) | Icon description is dropped when a visible label exists |
| Settings toggles read as a button plus a separate switch | `ToggleRow` uses `toggleable(role = Switch)` |
| Sliders and option rows had no name/value | Slider rows announce title + value; option rows announce current choice and use the dropdown role |
| Page dots were unlabelled | "Page N of M" |
| Page overview buttons were icon-only and 36dp | Named ("Move page 2 earlier", "Make page 1 your home page", "Delete page 2"…) and 48dp |
| Widget resize handles were 28dp and unnamed; frame buttons unnamed | 48dp touch areas, named handles, "Done resizing" / "Remove widget" |
| Hero clock and icon labels broke at large system font sizes | Clock numerals capped at ~115% growth; labels allow two lines and a wider cell above 130%, and grow at most ~150% so words never split |

## Checked
* `docs/nivo/a11y-audit.py` dumps the live UI tree from an emulator and lists clickable nodes under 48dp or without a label.
  Home, drawer, library, edit sheet, page overview and settings dashboard pass (the only hits are cards clipped by the viewport).
* System font scale 2.0 on Home, drawer and Settings: no clipping or crashes.
* Contrast: palette text ratios are unit-tested (≥ 7:1 primary, ≥ 4.5:1 secondary).
* Reduced motion: preset + automatic when the system animator scale is 0.

## Not verified
* **No real TalkBack session was run** (TalkBack is not installed on the test emulator). Semantics were verified through the
  accessibility tree, not by listening to speech. Focus order inside the pager, folder popup and drag flows still needs a device pass.
* Drag-and-drop has no non-drag equivalent yet (moving an icon to another cell). Remove/uninstall/info are reachable via the options menu.
* Switch Access, keyboard navigation and RTL layouts were not tested.
* Wallpaper-dependent label contrast cannot be guaranteed; the readability shading and "dark text" option mitigate it.
