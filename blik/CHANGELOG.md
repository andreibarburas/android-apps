# Changelog

All notable changes to Blik are documented here.

---

## [1.1.1] — 2026-07-09

### Fixed
- Crop editor no longer conflicts with system gestures; the image canvas is now inset from all screen edges, keeping the corner handles well within the safe gesture zone

---

## [1.1.0] — 2026-07-08

### Added
- Optional font pairing in Settings → Display: DM Serif Display for headings, Inter Tight for all UI text — toggle it on to bring Blik in line with the rest of the BARBURAS suite

---

## [1.0.8] — 2026-06-20

### Fixed
- Deleting screenshots from the device now works correctly on Android 10 and above

---

## [1.0.3] — 2026-06-12

### Added
- GPL v3 license — `LICENSE` file added, README updated, license headers added to key source files
- In-app text size setting (Settings → Appearance → Text size): Small / Default / Large / Extra Large, scales the entire typography scale across the app

---

## [1.0.2] — 2026-06-09

### Added
- Login screen: Nextcloud required notice below the connect button, including a link to find a hosting partner at nextcloud.com/sign-up/

---

## [1.0.1] — 2026-06-09

### Added
- GitHub link in Settings → Support section
- Handwritten Blik logo on Login, App Lock, and Gallery screens
- Authenticated WebDAV thumbnail loading in the Nextcloud tab for remote-only files (new device scenario)
- Fullscreen image viewer with pinch-to-zoom, tappable from the detail screen

### Changed
- Primary accent color updated to golden orange (`#FF8F00` dark / `#F57C00` light)
- App icon updated
- All user-facing strings switched to American English
- Support section order: Buy me a coffee → More by andrei BARBURAS → View on GitHub
- Donation link updated to `bunq.me/barburasdonations`

### Fixed
- Blank thumbnails when local file has been deleted externally — now shows "On Nextcloud" overlay or removes record entirely depending on upload status
- Duplicate `isDark` declaration in LoginScreen causing build failure
- Stray import inside file body in BlikNavHost causing KSP failure
- `AsyncImage` error slot compile error — replaced with `SubcomposeAsyncImage`

---

## [1.0.0] — 2026-06-05

### Initial release

**Library**
- Screenshot gallery with 2-column grid
- Upload status badges: Pending, Uploading, Uploaded, Error
- Filter chips: All, Pending, Uploaded, Notes, ⭐ Starred, and dynamic AI category chips
- Search across filename, note, tags, AI description, and category
- Long-press multi-select with upload, delete, blur, and star batch actions
- Per-thumbnail star and blur toggle buttons
- Auto-scan local folder on every app launch
- File reconciliation: detects externally deleted files on scan

**Upload**
- Nextcloud Login Flow v2 authentication
- SAF-based local folder picker
- WebDAV folder browser for Nextcloud destination
- Single, bulk, and auto-upload (WorkManager) with Wi-Fi only and charging only constraints
- Conflict handling: Ask / Skip / Overwrite
- Upload success/failure snackbar feedback

**AI**
- Claude and OpenAI vision API support (user's own keys)
- Returns description, category, and tags in a single API call
- Auto-categorize and auto-describe toggles
- On-demand AI analysis per screenshot

**Detail screen**
- Full image preview with "Tap to expand" hint
- Fullscreen viewer with pinch-to-zoom and fade chrome
- Action buttons: Star, Upload/Uploaded, Note, Tags, Task, AI
- Smart delete dialog: phone only / Nextcloud only / both
- AI description card with model badge
- Note, tags, and file metadata sections

**Nextcloud tab**
- Pull-to-refresh PROPFIND of configured remote folder and category subfolders
- Local and remote-only thumbnail display
- 📱 / ☁️ availability badges
- Info bottom sheet per file with delete option
- Multi-select with bulk delete from Nextcloud

**Settings**
- Nextcloud account management with sign-out
- Local and Nextcloud folder pickers
- Auto-upload controls
- Claude and OpenAI API key fields with show/hide toggle
- Default AI model selector
- Biometric app lock
- Tasks.org integration toggle
- Support: Buy me a coffee, More by andrei BARBURAS, View on GitHub

**Theme**
- Dark and light mode with toggle in gallery top bar
- Dark glass aesthetic with golden orange accent
- Handwritten Blik logo throughout
