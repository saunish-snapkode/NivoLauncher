# App Library

* `data/AppLibrary.kt` – offline classifier. Order: Android's own app category for games, then keyword rules
  on package + label (token-aware: ≥5 letters match anywhere, 4 letters match word starts, ≤3 must be a whole
  word, so "ola" does not match "nivolauncher"), then Android category fallbacks, then "Utilities".
* User edits (`LibraryConfig`): rename, hide, reorder, create/delete custom categories, move apps.
  Saved in the `library` preference and included in backups. Automatic grouping never overrides a manual move.
* UI (`ui/AppLibrary.kt`): two-column cards with four tappable preview icons; tap a card to expand it from its
  position (geometry interpolation); long-press a card for Rename / Move / Hide / Delete; long-press an app
  to move it to another category or a new one.
* Tests: `DesignAndLibraryTest` covers classification (incl. a regression for the "snap"/"ola" substring bug).

Known limits: expansion covers the page area, not the full screen; category colours/icons and card sizes
are not yet customisable; "manual-only" organisation mode is not implemented.
