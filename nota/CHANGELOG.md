# Changelog

All notable changes to Nóta are documented here.

## [1.3.0] - 2026-07-08

### Added
- Optional font pairing in Settings → Display: DM Serif Display for headings, Inter Tight for all UI text — toggle it on and see how it feels

---

## [1.2.2] - 2026-06-14

### Fixed
- Nextcloud Login Flow v2 poll URL is now rewritten to match the server address the user entered, fixing "failed to connect" errors and login hangs on Tailscale/subdomain setups where the server returns an internal hostname or IP for polling

---

## [1.2.1] - 2026-06-12

### Fixed
- Removed deprecated `windowLayoutInDisplayCutoutMode="shortEdges"` from theme — handled automatically by `enableEdgeToEdge()`
- Updated `activity-compose` to 1.10.1 to remove internal use of deprecated `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`

---

## [1.2.0] - 2026-06-12

### Fixed
- Critical bug: spaces and line breaks could not be typed in the note editor (caused by image-placeholder display transform)
- Markdown toolbar was hidden behind the on-screen keyboard — now anchored above it with `imePadding()`

### Added
- GPL v3 license
- Localization support — strings externalized to `res/values/strings.xml`
- Polish translation (`res/values-pl/strings.xml`) contributed by the community, Weblate-ready structure
- In-app text size setting (Small / Default / Large / Extra Large) in Settings → Display, scales typography across the entire app

---

## [1.1.0] - 2026-06-09 (not published to Play Store)

### Changed
- Visual redesign matching new Vinci design language: deeper dark palette, green-tinted light palette, radial glow accents, pill search bar, badge-style section headers, mode-aware elevated cards for pinned notes

---

## [1.0.2] - 2026-06-09

### Added
- Handwritten logo replaces text in notes list header, app lock screen, and login screen
- GitHub link in Settings → Support section
- Tasks.org integration — add any note as a task with one tap, from the list or inside the note
- Tasks.org toggle in Settings → Integrations section

### Changed
- App accent color updated to forest green to match the new icon
- New app icon
- Donation link updated
- Image markdown (`![alt](url)`) now displays as `📎 filename` in the editor and list preview instead of raw syntax
- Images in single note view now display at full original size (no cropping)
- Images in note list cards remain cropped to thumbnail

### Fixed
- Server-deleted notes now correctly removed from local database on sync
- Notes created on the phone now sync back to Nextcloud
- Light mode text contrast across all screens
- Duplicate note title in card preview (Nextcloud stores title as first content line)

---

## [1.0.1] - 2026-06-03

### Added
- Dark and light mode with instant toggle (sun/moon icon in top bar)
- Theme preference persisted across app restarts
- Category dropdown picker in note editor — select from existing categories or type a new one
- Auto-fill category when creating a note from a filtered category view
- Share text from any app directly into a new note
- Share images from any app — image uploaded to Nextcloud, embedded as markdown
- Authenticated image rendering — images hosted on Nextcloud display correctly in notes
- Image thumbnails in note list cards
- Support section in Settings with donation link and website link
- Tasks.org package visibility declaration for Android 11+

### Changed
- Date format updated to `YYYY/MM/DD | HH:MM`
- Back button now always navigates correctly from the note editor
- Opera shared text no longer shows `+` between words

### Fixed
- Gson/Retrofit replaced with plain OkHttp + org.json to fix R8 obfuscation crashes (`ClassCastException`)
- DNS caching added to OkHttpClient to fix Nextcloud Login Flow polling failures
- `okhttp3.Dns` interface implemented with `object :` syntax instead of SAM lambda
- `provideGson()` removed from Hilt module after Gson dependency was dropped

---

## [1.0.0] - 2026-05-31

### Initial release

- Nextcloud Login Flow v2 authentication
- Offline-first note sync with Room database
- Markdown editor with preview toggle
- Per-note biometric lock
- App-wide biometric lock
- Category filtering
- Note search
- Pinned (favorite) notes section
- Pull-to-sync
- Dark mode
