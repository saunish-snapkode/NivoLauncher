# Performance

Probe: `docs/nivo/perf.py <adb-serial> [label]` (cold start from the system's "Displayed" log, frame stats from
`dumpsys gfxinfo` while exercising drawer / drawer scroll / page swipes, PSS, and idle CPU).

**Caveat:** the numbers below come from an Android emulator with a *software* GPU, so absolute frame times and jank
rates are pessimistic and not representative of a phone. Use them only for before/after comparison, and re-run the
script on a real device (the phone was locked during this pass, so no on-device numbers exist yet).

## Results (emulator, debug build)
| | Baseline | After |
|---|---|---|
| Cold start (system-reported) | ~315 ms | ~300 ms |
| Janky frames, mixed gestures | 8.6 % | 7.4–8.3 % (noise level) |
| Drawer open/close ×3 (warm) | 20.8 % | 16.7 % |
| Drawer open + scroll | 24.4 % | 10.0 % |
| Home ⇄ Library swipes | 2.3 % | 2.3 % |
| Worst-frame p99 | 113–117 ms | 117–129 ms (first drawer composition; unchanged) |
| PSS after exercise | 61–63 MB | 59 MB |
| Idle CPU / frames at rest | 0 % / 0 frames | 0 % / 0 frames |

## Changes made because of the measurements
* Settings no longer rebuild/re-sort the app list, suggestions or library on every change: those flows now depend only on
  the fields they use (`labelOverrides`, `hiddenApps`).
* Settings are written to disk 300 ms after the last change (and when the launcher stops) instead of on every slider tick.
* Dragging an icon no longer recomposes the whole home screen on each pointer move (remove-strip highlight is derived state).
* Drawer: home-lookups hoisted out of per-cell code; `DrawerState.isOpen` is derived so root screens stop recomposing every frame;
  rounded clip dropped once the sheet is full-screen.
* **Tried and reverted:** `CompositingStrategy.ModulateAlpha` on the home layers. It added ~30 MB PSS and no measurable frame gain.

## Remaining
* The first drawer composition still hitches (~120 ms on the emulator). Candidates: pre-composing the drawer off-screen,
  fewer simultaneous full-screen translucent layers, a lighter first frame.
* No Macrobenchmark / Baseline Profile module yet; startup and scroll should be measured with it on a device.
* No battery measurement (needs a device and a long run). Design choices that help: the clock ticks per minute only while
  the activity is started; nothing animates at rest; no network calls unless the user turns on web suggestions.
