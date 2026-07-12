# Qarib

**A "want to visit" places app with Nextcloud sync and geofence notifications.**

Qarib (قريب — Arabic for "nearby") is a free, open-source Android app for saving places you want to visit and getting notified when you're close to one. No cloud account required to use it locally; optionally sync your places to Nextcloud via WebDAV.

Part of the [BARBURAS](https://barburas.com) open-source Android suite.

---

## Screenshots

<!-- screenshots -->

---

## Features

- **Save places** with name, category, photo, and a personal note
- **Search** via OpenStreetMap / Nominatim — no Google API key required
- **Map view** using osmdroid (OpenStreetMap tiles)
- **List view** sorted by country, with fast-scroll index
- **Geofence notifications** — get notified when you enter the radius of a saved place
- **Configurable radius** — 100 m, 250 m, 500 m, 1 km, 2 km, 5 km
- **Nextcloud sync** — places stored as JSON in your Nextcloud via WebDAV (Login Flow v2)
- **GPX import** — import saved places from a GPX file
- **Directions** — handed off to whichever maps app you prefer
- **Biometric app lock**
- **Dark and light theme**, in-app text size control
- **Privacy-first** — location processing is entirely on-device

---

## A note on place names and territories

Qarib does not decide how places, countries, or territories are named. It displays addresses and details as returned by the data source it queries (Nominatim / OpenStreetMap). Any naming of disputed or contested territories reflects that source, not a position taken by the app or its developer.

---

## Tech stack

- Kotlin + Jetpack Compose
- Material3
- Room (offline-first, local source of truth)
- Hilt
- OkHttp
- osmdroid (OpenStreetMap)
- Google GeofencingClient
- Nextcloud WebDAV + Login Flow v2

---

## Build

1. Clone the repo
2. Open in Android Studio
3. Build and run on a device or emulator running Android 9+

No API keys required.

---

## A note on AI

Parts of this app were developed with AI assistance. I always disclose this upfront.

---

## Links

- [Play Store](https://play.google.com/store/apps/dev?id=6842866278906089090)
- [Privacy Policy](https://barburas.com/privacy-policy/)
- [Donate](https://bunq.me/barburasdonations)
- [More apps by BARBURAS](https://barburas.com)

---

## License

[GNU General Public License v3.0](LICENSE)

Copyright © 2024 BARBURAS
