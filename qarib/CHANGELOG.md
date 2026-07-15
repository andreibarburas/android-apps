# Changelog

## [1.3.0] - 2026-07-15

✨ Added:
- Each place now has a dedicated detail screen (replacing the bottom sheet) with full place info, actions, and visit history
- Visit history: log visits to a place with a date, notes, and photos
- Edit and delete individual visits
- Visit history syncs across devices via Nextcloud

## [1.2.0] - 2026-07-13

🐛 Fixed:
- Qarib folder was not editable; the field in Settings > Nextcloud is now tappable and opens an edit dialog
- Qarib folder can now also be set during onboarding, before connecting to Nextcloud

## [1.1.1] - 2026-07-12

✨ New:
- Added link to r/BarburasLab — the community hub for all BARBURAS apps

## [1.1.0] - 2026-07-08

✨ New:
- Optional font pairing in Settings > Display — DM Serif Display for place names and headings, Inter Tight for all UI text

## [1.0.0] - 2026-06-23

### Initial release

- Save places you want to visit, with name, category, photo, and personal note
- Search places via OpenStreetMap / Nominatim (no Google API key required)
- Interactive map view using osmdroid
- List view sorted by country
- Geofence notifications when entering the radius of a saved place
- Configurable notification radius: 100 m, 250 m, 500 m, 1 km, 2 km, 5 km
- Nextcloud WebDAV sync (Login Flow v2)
- Import places from GPX files
- Directions handed off to external maps apps via intent
- Biometric app lock
- Dark and light theme with in-app text size control
- Privacy-first: all location processing is on-device only
