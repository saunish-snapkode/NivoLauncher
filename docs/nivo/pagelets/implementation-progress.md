# Pagelets — implementation progress

Date: 2026-10-11. Verified on an Android 17-class emulator (software GPU). **Not** verified on a physical device.

## Done

### Phase 0 — audit
`00-audit.md`. Main finding: the brief assumes Lawnchair/Launcher3; Nivo is a standalone Compose launcher with no
`Workspace`/`CellLayout`/DB. The approach was adapted to Nivo's own `nivo:` first-party widget mechanism (see the audit).

### Phase 1 — framework
* `pagelet/`: `PageletType`, `PageletConfig` (forgiving JSON), `PageletUiState` + `PageletDataProvider` (Loading /
  NeedsPermission / Empty / Ready / Error-with-cache), `PageletVisibility` (+ `pageCloseness`, `dockHideFraction`),
  `PageletRepository`, `PageletTokens`, `UsageAccess`.
* A Pagelet is a `HomeItem` `nivo:pagelet:<TYPE>:<uuid8>` spanning the whole grid on a page of its own, so the existing
  paging, removal, reorder, page overview, backup and drop logic apply. Config is one `pagelet.<id>` key in the layout's own
  SharedPreferences file, so **backup/restore carries it with no extra code**.
* Add: edit sheet › *Add a Pagelet*, and Settings › Home › *Pagelets*. Gallery sheet per the brief (template carousel,
  section chips, "Use the whole page" switch, live Usage-access card with Allow → Settings and an "allowed" state).
* Remove: long-press the page › *Remove*, or Settings. The page is deleted with it; **Undo** snackbar restores it, its
  config and its position.
* Drops onto a Pagelet page are refused with "This page is a Pagelet"; new icons are never auto-placed on it; it keeps its
  whole page when pages are reordered and when the grid size changes.
* Dock hiding is proportional to scroll (not a hard toggle) and is per-Pagelet (`hideDock`). Status-bar icons switch to
  light over the sky.
* Visibility gating: layers are composed only within 1.05 pages of the current page; ambient motion and data collection run
  only while the Pagelet is fully settled, the app is resumed. Battery saver / low-RAM halve rain and drop twinkle and drift.

### Phase 2 — Sky UI with fake data
* Background: one vertical gradient, weather light, stars, blurred cloud washes (API 31+), rain streaks, fog bands, distant
  storm flash, two-layer horizon bloom that follows the sun/moon, hairline, 256×256 film grain (made once), vignette.
* Foreground: header, 118 sp clock (12/24 h follows the system), temperature row, 24-hour arc with sunrise/sunset ticks,
  screen-time segments that glow along it, sun/moon marker, NOW/NOON labels, ground stats (screen time, goal bar, data-driven
  insight line, unlocks / up since / longest session, top apps).
* Entrance (rise, arc draw-in, sun travel, staggered segments, count-up, goal bar) plays once; scenes cross-fade over 1.4 s;
  reduced motion draws the final state and shortens fades.
* Debug-only scene switcher in the Pagelet options sheet (time of day × weather).
* One real finding fed back into the palette: under the spec's Cloudy and Fog palettes white text measured ~3:1; both now
  carry a 30% black legibility scrim behind the header/clock band (the test asserts ≥ 4.5:1 for every phase × weather).

### Phase 3 — real weather (direct from the phone, no proxy, by decision)
* `pagelet/weather/`: `MetNorwayClient` (Locationforecast 2.0 compact), `MetParser`, `FeelsLike`, `SolarTimes` (NOAA, offline),
  `SkyWeatherRepository` (cache + refresh rules), `LiveSkyProvider`, `WeatherLocationResolver`, `PlaceSearch` (Android Geocoder).
* MET terms followed without a server: identifying User-Agent (`NivoLauncher/<ver> (+contact)`, contact from `met.contact`
  in git-ignored `local.properties`, baked into `BuildConfig.MET_CONTACT`); lat/lon rounded to 2 decimals; HTTPS + gzip;
  `If-Modified-Since` = the exact previous `Last-Modified`; `Expires` honoured (+0–2 min random jitter) so nothing is
  requested before it; 403 backs off an hour, 429 honours `Retry-After`, 5xx 15 min; a different place is the only thing
  that bypasses `Expires`; requests happen only while the Sky is on screen (and on return to the launcher), never in the
  background; no WorkManager.
* Location: the city the user picked (Android Geocoder first, online search only as fallback), or approximate device
  position (`ACCESS_COARSE_LOCATION`, last-known only, asked on tap with the reason on the card). No place → the header says
  "Set a location" and the page keeps the right time-of-day sky; no cache and no network → "Weather unavailable".
* Derived on the phone: sunrise/sunset, feels-like, today's high/low in the user's time zone, 6-hour outlook (parsed, not shown yet).
* Attribution: "Weather data: MET Norway" on the page (only when the data is real) and Settings › Data sources (licence link).
* Verified live from the emulator: 200 + `Expires` + `Last-Modified`, real response parsed, repeat call → 304.

### Phase 4 — real Wellbeing, Calendar, Steps, Activity log
* `pagelet/wellbeing/`: `WellbeingCalculator` (pure: foreground/background/screen events → screen time with overlaps counted once,
  per-hour minutes, per-app time, unlocks, "up since", longest session), `UsageStatsReader`, `WellbeingSource`, `ActivityLog`
  (timeline, grouping by hour, RFC 4180 CSV). Launchers, System UI and this app are excluded, as Digital Wellbeing does.
* **Settings fix:** `PACKAGE_USAGE_STATS` was never declared, so the app was missing from the Usage access list. Declared; the
  Allow button now opens Settings at this app's own switch (Android 10+).
* `pagelet/calendar/`: today's remaining events across all calendars; "IN 40 MIN · Design review +2" under the weather; permission asked in place.
* `pagelet/steps/`: Health Connect read-only steps, guarded for API < 26; rationale activity + manifest `queries` added; the card
  handles allowed / not allowed / not installed (opens the Play page) / unsupported.
* Activity log screen: day switcher (a week back, as far as Android keeps events), summary, All/Unlocks/App-use filters, hourly
  timeline with app icons, CSV export through the system file picker. No Room database: it is computed from Android's own log on
  demand, so nothing is stored or duplicated.
* Goals are adjustable (screen time ±15 min, steps ±500).

### Layout (the overlap you reported)
* The ground (screen time, stats, steps, sentence, top apps, footer) is a **priority column**: it shows as many rows as fit, most
  important first, instead of overlapping the footer. The arc's radius and the clock's size adapt to the measured weather block, so
  larger display/text scales no longer collide. Tested at 7 screen sizes/text scales (`SkyLayoutTest`).

## Tests (all green)
* JVM (`app/src/test`): phase boundaries, MET `symbol_code` mapping (snow showers no longer map to rain), arc geometry,
  segment proration, palette rules (no saturated background colour), text contrast for all 25 phase × weather scenes,
  insight text, config round trip, id parsing, visibility and dock-fade maths.
* Emulator (`app/src/androidTest`): `PageletTest` (14) — add, persistence across a fresh store, backup/restore round trip,
  remove/undo, drop rejection, no auto-placement, page move, grid change, two Pagelets — plus the existing real-app
  two-finger tests.

## Measured (emulator, software renderer, warmed up)
* Idle on the Sky with ambient motion: 361 frames in 6 s, 0 janky, 16–20 ms.
* Swiping between the Sky and an ordinary page: 1.6% janky, p99 19 ms — no worse than swiping between two ordinary pages
  in the same session (3.7%, p99 32 ms). Cold-start numbers (just after install) are 3–7% and should be ignored.
* A bug found by the real-app tests: passing the animation clock into the arc as a plain parameter recomposed the whole
  foreground every frame. It is now read while drawing.

## Not done
* **Phase 3 leftovers.** No WorkManager (refresh is on-screen only, by design); the 6-hour outlook is parsed but not drawn;
  the existing smartspace weather line still uses Open-Meteo. **Set `met.contact` in `local.properties`**: without it the
  User-Agent has no contact, which MET's terms require.
* **Phases 5–8 (Agenda, Now Playing, Move, Headlines).** Registered in `PageletType` and shown as "Coming soon" in the
  gallery; not built. Focus stays out of scope as specified.
* **Screenshot tests.** No Paparazzi/Roborazzi (new Gradle plugins + golden images are a repo-level decision). Scene logic is
  JVM-tested; the 10 scenes were captured and inspected on the emulator via the debug switcher.
* **Roboto Flex** is not bundled; the clock uses the platform sans with variable weight (true variable on Android 12+).
* **Heat index extremes:** at very hot and humid readings the NWS formula can show a large "Feels" figure (e.g. 59° at 37° / high humidity). It follows the brief's rule; consider capping it.
* **TalkBack pass, font scale 1.3×/2.0×, Macrobenchmark/JankStats, a physical high-refresh device** — not done.
* The Sky draws behind the dock but the content is laid out assuming the dock is hidden; with "Use the whole page" off the
  ground stats can sit under the dock.

## Headlines Pagelet (News converted)

The separate News page and its setting are gone. News is now the **Headlines** Pagelet (`nivo:pagelet:HEADLINES:<id>`): added, removed (with Undo) and configured like any Pagelet, and it draws itself inside the pager (`PageletType.isLayered == false`), hiding the dock when "Use the whole page" is on.

- Per-Pagelet config (`PageletConfig.news`, `newsTopic`) is stored in the Pagelet's JSON and so travels in backups; fetched feeds are shared between Headlines Pagelets via the view model's feed cache.
- Existing installs: the old News page and its settings are dropped on first launch (`migrateLegacyNews()`); no Pagelet replaces it. Users add Headlines from the gallery if they want it.
- New installs: no Pagelet is added by default; Headlines (like Today) appears only when the user adds it from the gallery.

## Today Pagelet: illustrated horizon and weather glyph

- `SkyLandscape.kt`: layered vector hills, a city skyline and pines drawn at the horizon in their own canvas. Shapes come from fixed seeds (`LandscapeGen`) and are built once per page size (`LandscapeGeometry`); only colours and a few lights change per frame.
- Time of day sets the colours (`LandscapeColors`): hazy far hills to near-black front hill, sun- or moon-lit ridge edges, and how many city windows are lit (none by day, most at night). Weather darkens (rain, storm), hazes (fog) or greys (cloud) the layers; lightning lights the silhouettes; a few birds fly by day.
- `SkyGlyph.kt`: the weather as a small vector illustration (sun, moon, partly cloudy, cloud, rain, storm, snow, fog) beside the temperature, chosen from MET Norway's `symbol_code` and whether it is night.
- The old horizon hairline is gone: the landscape is the horizon.

## Agenda, Now Playing and Move

All three draw themselves inside the pager on the surface colour, like Headlines (`PageletPage` dispatches by kind; only Today uses the layers around the pager). Each kind can be on home once.

- **Agenda**: today or tomorrow from the phone's calendars (`CalendarToday.eventsOnDay`), grouped by hour with a "now" line (`AgendaModel`, pure and tested). Option: keep finished events, dimmed.
- **Now Playing**: the active media session through the existing notification listener, now with cover art, album, duration and position (`NowPlaying`, `MediaProgress`); a scrubber, previous/play/next, and a calm ask for notification access.
- **Move**: steps (ring against the goal, seven-day bars), last night's sleep against a goal, and heart rate (latest, average, lowest, highest) from Health Connect (`MoveSource`; `READ_SLEEP` and `READ_HEART_RATE` were added to the manifest). Each section is switchable; `java.time` is only touched on Android 8+.
- `PageletPagesShotTest` renders the three pages with realistic data and saves pictures (run with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`, then pull `/sdcard/Android/data/<app>/files/shots`).
