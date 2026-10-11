# Test plan

Automated (JVM): `./gradlew :app:testDebugUnitTest` — search scoring, item geometry, ID helpers,
palette contrast (WCAG), surface ordering, app classification.

Manual (per release): default-home flow, launch app, drawer open/close (slow + fast), search, folders (create/drag out),
widgets (add/move/resize/remove, work profile), pages + library, backup/restore, notification badges,
light/dark/AMOLED, reduced motion (system animator scale 0), font scale 1.3, 3-button vs gesture nav, landscape.

Not yet automated: instrumented UI/gesture tests, Macrobenchmark frame metrics, accessibility scanner runs.
