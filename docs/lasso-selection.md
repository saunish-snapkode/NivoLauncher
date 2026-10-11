# Lasso selection and spatial editing

Nivo's home screen can select several icons by drawing a loop around them, then move, arrange, group or remove them
together. This note records how it is built and why.

## Starting point

Nivo is a standalone Jetpack Compose launcher. It is **not** based on Launcher3 or Lawnchair, so none of their
classes exist here. The relevant pieces are:

| Concern | Where it lives |
| --- | --- |
| Pages and scrolling | `HorizontalPager` in `HomeScreen.kt` (News, workspace pages, Library) |
| Grid and cell placement | `WorkspacePage`: item at `col * cellW`, `row * cellH`, integer cell sizes |
| Layout model | `HomeItem(id, page, col, row, spanX, spanY)`; `page == -1` is the dock |
| Folders and widgets | ids prefixed `folder:`, `widget:` / `nivo:` |
| Persistence | `LayoutStore.saveItems` (JSON in SharedPreferences), written through `LauncherViewModel.commit()` |
| Single-item drag | `DragCell` + `DragState` (long press, then drag, one item) |
| Tap multi-select | `SelectionState` (drawer only after this change) |

## Architecture

```
lasso/                      pure Kotlin, no Android types, unit tested
  Geometry.kt               Box, LassoPath (reusable buffer), Polygon maths, smoothing
  GridMetrics.kt            pixel <-> cell, using the same integer maths as WorkspacePage
  HitTesting.kt             SelectionPolicy, LassoHitTester, selection algebra
  LassoMachine.kt           the state machine
  LayoutOps.kt              align, distribute, compact, gather, move to page, folder, remove
  GroupDrop.kt              plans where a dragged group would land, and whether that is valid
  LassoMotion.kt            durations and reduced-motion rules

ui/lasso/
  LassoSession.kt           Compose state: machine + selection + geometry registry + undo hooks
  LassoLayer.kt             pointer input, path overlay, rings, group ghosts, drop preview
  LassoToolbar.kt           bottom contextual toolbar (always available, accessible)
  LassoRadial.kt            optional radial action menu
```

`LassoSession` lives in `LauncherViewModel`, so a recreated activity keeps the selection. The view model also owns
layout snapshots for undo and rollback.

### Why the icon rectangles are registered, not recomputed

`DragCell` already measures each icon's real on-screen rectangle. It now reports that rectangle to the session. Hit
testing then uses exactly what the user sees, whatever the page scale, parallax, insets or font size are, instead of
re-deriving positions from settings. The registry is a plain `HashMap`, so reporting costs nothing outside lasso mode.

### Coordinate systems

* Touch points are collected in **workspace-local** pixels: the overlay surface sits exactly on the pager's content
  rectangle.
* Icon rectangles are converted from root to workspace-local once per gesture.
* Hit testing is restricted by the **model** (`HomeItem.page`), never by rectangle position, so a neighbouring page
  that happens to overlap during a transition can never be selected.
* A selection belongs to one page. Starting a stroke or tapping on a different page replaces it.

## State machine

```
IDLE --arm--> ARMED --stroke start--> DRAWING --stroke end--> SELECTED --drag start--> DRAGGING
  ^              ^                       |  cancel / geometry change   |                    |
  |              +------ no hits --------+                             +---- drag end ------+
  +---------------------------- done / cancel / back (from any state) ------------------------+
```

Every transition is a method that returns whether it was allowed. Events that do not apply in the current state are
ignored and leave the state untouched, so the machine is deterministic and any gesture can be interrupted.

`SELECTED` is re-entrant: after an action that keeps the selection meaningful (align, distribute, compact, gather,
move together, move to page) the user is back in `SELECTED` and can act again. Actions that consume the selection
(create folder, remove) end in `IDLE`. **Done** keeps every change, **Cancel** rolls the layout back to the
snapshot taken when the mode was entered, and **Back** behaves like Done.

## Selecting

* A closed-looking stroke selects every icon whose tile is at least **40 %** inside the loop (a 6x6 sample grid per
  tile). An open stroke is closed logically by the chord from its last point to its first.
* A stroke that encloses almost no area (a line or scribble) selects the icons it touches, within a small radius.
* Mode **Add** or **Remove** decides what a stroke does; tapping an icon toggles it.
* Only apps and folders are selectable. Widgets are never captured, and the dock is out of scope.

## Moving, and never overwriting

Dragging a selected icon lifts the whole group as ghosts that keep their relative layout. The model is **not**
touched while dragging; the overlay draws ghosts and a drop preview. On release the planner checks that every
destination cell is inside the grid and not occupied by anything that is not part of the group (including widget
spans). If it is valid the ghosts settle into place and one atomic `commit()` is made. If not, the ghosts glide back
and nothing changes. Dropping onto an unselected icon is invalid: the group never silently merges or overwrites.

## Motion and accessibility

* Timings: highlight about 130 ms, toolbar about 200 ms, lift about 220 ms, placement about 300 ms; all springs come
  from the existing `NivoMotion`, so reduced motion and the system animation scale are honoured.
* Every selected icon is exposed to accessibility services as a checkbox, with "select all on page", "clear" and
  "cancel" as actions. The toolbar is always available; the radial menu is skipped when touch exploration is on.

## Deliberate limits

* Selection is single-page.
* The dock cannot be lassoed or used as a group drop target.
* Widgets are not selectable.
