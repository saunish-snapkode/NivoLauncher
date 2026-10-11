# Pagelets — permissions and data

Everything stays on the device. No analytics, no accounts. A permission is asked for only when the user switches on the
section that needs it, with a plain-language reason first.

| Pagelet / section | Permission | Used for | Asked when | Status |
|---|---|---|---|---|
| Today · Wellbeing | **Usage access** (`PACKAGE_USAGE_STATS`, a special app-op). It must be *declared in the manifest* or the app is not listed under Settings › Special app access › Usage access (that was a real bug) | Screen time, unlocks, per-app time, computed on the phone from `UsageStatsManager` | The user taps **Allow** on the page or in the options sheet; it opens Settings straight at this app's switch | Built (`pagelet/wellbeing/`) |
| Today · Weather | `INTERNET` (already declared); `ACCESS_COARSE_LOCATION` only if the user taps **Use my location** | Forecast for a place. Only the place, rounded to ~1 km, is sent to api.met.no (with an app User-Agent); never the precise position | When the user taps *Use my location* on the Weather location card | Built. A chosen city needs no permission |
| Today · Calendar | `READ_CALENDAR` (runtime) | Next events today, from every calendar on the phone, read-only | Tap **Allow calendar** on the page or the card; after a refusal the button opens the app's Settings page | Built (`pagelet/calendar/`) |
| Today · Steps | `android.permission.health.READ_STEPS` via Health Connect (read-only). Needs the rationale activity declared in the manifest, which is | Today's step total | Tap **Allow steps**; Health Connect shows its own dialog. On Android 13 and older without Health Connect the button opens its Play page | Built (`pagelet/steps/`) |
| Move (sleep, heart rate) | Health Connect | | | Not built |
| Now Playing | Notification access (existing `NivoNotificationListener`) | Media sessions | n/a | Not built |
| Headlines | `INTERNET` | The user's own feeds | n/a | Not built |

## Network destinations

* `api.met.no` — forecasts (HTTPS, direct from the phone, identified by User-Agent).
* The phone's Android Geocoder for city search; if it has none, `geocoding-api.open-meteo.com` as a fallback (existing).

## What the sample data is

Weather, wellbeing, calendar and steps are all real now. Nothing is made up: a section that isn't allowed says so in words and
offers the button. The fake provider survives only for debug scenes and layout tests. The MET credit appears only when real
weather is shown.
