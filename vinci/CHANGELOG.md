# Changelog

All notable changes to Vinci are documented in this file.  
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

---

## [1.6.0]
### Fixed
- Vinci folder was not editable — the field in Settings → Nextcloud is now tappable and opens an edit dialog.
- Vinci folder can now also be set during onboarding, before connecting to Nextcloud.

---

## [1.5.3]
### Added
- Log interactions with multiple contacts at once — group calls and meetings now appear in everyone's history, not just the primary contact.
- Foundation for a Vinci companion app for Nextcloud: contacts and interactions will be viewable and editable from the web.

### Improved
- Sync now pulls in interactions logged on other devices too, not just pushing local changes up.

### Fixed
- An issue where syncing could occasionally create duplicate interactions.

---

## [1.4.0]
### Added
- Optional font pairing in Settings → Display: DM Serif Display for headings, Inter Tight for all UI text — consistent with the rest of the BARBURAS suite.

---

## [1.3.4]
### Added
- Multi-device restore: interactions and starred contacts now restore correctly on a second device (tablet, new phone).
- Restore screen shows progress while importing and a summary when complete.
- New "Sync starred to Nextcloud" button in Settings — pushes starred contacts and follow-ups to Nextcloud, with a spinner and confirmation message.
- Each interaction file now embeds the contact identifier, making restore reliable across devices regardless of how contacts were synced.
- Both plain and bold field formats are now supported when reading older interaction files.

### Fixed
- Deleted interactions are now removed from Nextcloud so they no longer reappear after a restore.
- Starring or unstarring a contact now syncs to Nextcloud immediately.
- Restore and sync actions now show a Snackbar notification when starting and when complete.

---

## [1.1.9]
### Fixed
- Deleting an interaction now correctly resets the contact's last-interaction timestamp in the People section on Home.

## [1.1.8]
### Fixed
- Social Media interactions now correctly save and display the selected platform (was storing a literal backslash due to a string escape bug).

## [1.1.7]
### Added
- "Social Media" interaction type with platform picker (LinkedIn, Instagram, GitHub, etc.) and correct brand icons in interaction lists.

## [1.1.6] / [1.1.5]
### Fixed
- Social Media platform picker now renders correctly — replaced `LazyRow` with `FlowRow` to avoid nested scroll conflict.
- Fixed duplicate `@OptIn` annotation that caused a build error.

## [1.1.4]
### Fixed
- Contacts starting with a digit now appear at the top of the list under a `#` section; `#` added to the alphabet sidebar.

## [1.1.3]
### Fixed
- Social links (LinkedIn, Instagram, GitHub, etc.) now display correctly on the contact detail screen.
- Fixed CardDAV UID resolution to use `SOURCE_ID` (filename) instead of `SYNC1` (internal vCard UID), which differ on some Nextcloud setups.
- Fixed WhatsApp raw contact UID overwriting the DAVx5 UID in multi-account setups.

## [1.1.0]
### Added
- Text size setting (Small / Default / Large / Extra Large) in Settings → Display.
- Tag filter chips on the Contacts tab.
- Bulk star/unstar via long-press selection.
- Per-contact interaction history export as Markdown.
- Privacy policy link in Settings.
- Onboarding screen explaining Vinci and DAVx5 setup.
- GPLv3 license.

### Fixed
- Unknown-number interactions now sync to Nextcloud.
- WhatsApp and Signal icons now display correctly (missing manifest query declarations added).
- Contact detail screen background gradient seam removed.
- Address book URL discovery rewritten to use folder paths instead of `.vcf` file paths.

## [1.0.2]
### Fixed
- Interactions with unsaved/unknown phone numbers were silently not uploading to Nextcloud.
- Fixed folder naming for unknown-number contacts.
- Sync button now retries previously unsynced logs.

## [1.0.1]
### Added
- Initial public release on Google Play.
