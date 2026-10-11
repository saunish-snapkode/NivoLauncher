# Navigation model

Implemented: **[Home] → [App Library] → [more pages…]**
* Swipe up → app drawer. Swipe down → system notification shade (configurable gestures).
* Home is the stable default page; "Home button" returns to it. The library page can be switched off.
* Dragging an icon to a screen edge flips pages; dropping on the library page is ignored.

## Left page: Google Discover — NOT possible, by design
Google Discover is shown by the Google app through a private launcher-overlay service that Google only
binds for allow-listed launchers (system / signed integrations such as Pixel Launcher). A third-party launcher
cannot host it, and scraping the feed is not acceptable. Nivo therefore does **not** reserve or fake a Discover
page. A future option is an honest, user-chosen feed (e.g. an RSS reader page) clearly labelled as such.

## Gesture notes
The App Library grid scrolls vertically, so swipe-up-for-drawer and swipe-down gestures do not fire while the
library page is in front (the library itself lists every app).
