# Nivo Launcher

A home-screen launcher for Android, written from scratch in Kotlin + Jetpack Compose.

## Features
Home screen with paged grid, dock (multi-page), folders, widgets (move / resize / work-profile), "At a glance" header,
app drawer (search, fuzzy matching, sort modes, A–Z scroller, drawer folders, work tab), contact / settings / web search,
notification badges, icon packs, icon shapes, themed icons, per-app icons and names, accent colours, fonts,
gestures (swipe, double-tap, home press), backup & restore.
See `FEATURE_GAP.md` for the full status against Lawnchair and `ROADMAP.md` for the plan.

## Build
```
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:testDebugUnitTest # unit tests
```
Release signing: create `keystore.properties` in the project root (it is git-ignored):
```
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```
then `./gradlew :app:assembleRelease`.

## Download / releases
Signed APKs are attached to each [GitHub Release](../../releases). CI builds and tests every pull request, and pushing a
`v*` tag (e.g. `v1.0.0`) publishes a new release automatically. See [.github/RELEASING.md](.github/RELEASING.md) for
how to cut a release and set up the signing secrets.

## Permissions
`QUERY_ALL_PACKAGES` (list every app — core to a launcher), `READ_CONTACTS` / `READ_CALENDAR` (optional search and
"At a glance"), `INTERNET` (optional web suggestions), `EXPAND_STATUS_BAR` (notification gesture),
`REQUEST_DELETE_PACKAGES` (uninstall from long-press), plus optional notification-listener and accessibility services.

## Origin and licensing
Lawnchair was used only as a reference for *which features a launcher offers*. No Lawnchair or Launcher3 source code,
resources, strings or assets were copied; everything here is original and uses public Android APIs.

## Licence
Nivo Launcher is free software, released under the **GNU General Public Licence, version 3** (or, at your option, any
later version). The full text is in [LICENSE](LICENSE). You may use, study, share and change it; if you distribute it or
a modified version, you must do so under the same licence and make the source available.

The program is distributed in the hope that it will be useful, but **without any warranty**; without even the implied
warranty of merchantability or fitness for a particular purpose.

Weather data shown by the Today Pagelet comes from MET Norway (licensed CC BY 4.0) and is credited in the app.
