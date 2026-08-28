# Barburas Android Apps

A suite of beautiful, privacy-focused Android apps built for self-hosted infrastructure. All apps connect to your own server; no data collection, no ads, no third-party services.

## Notice
I am starting to create individual Gits for each app. As a result, the app directories in this Git will slowly but surely be deleted.

## Apps

| App | Description | Package | Status |
|-----|-------------|---------|--------|
| [Blik](blik/) | Screenshot manager with Nextcloud upload | 'com.brbrs.blik' | ✅ [Live on Google Play](https://play.google.com/store/apps/details?id=com.brbrs.blik) |
| [Merk](merk/) | Nextcloud Bookmarks client | 'com.brbrs.merk' | ✅ [Live on Google Play](https://play.google.com/store/apps/details?id=com.brbrs.merk) |
| [Nóta](nota/) | Nextcloud Notes client | 'com.brbrs.nota' | ✅ [Live on Google Play](https://play.google.com/store/apps/details?id=com.brbrs.nota) |
| [Qarib](https://github.com/andreibarburas/qarib-android/) | A "want to visit" places app | 'com.brbrs.qarib' | ✅ [Live on Google Play](https://play.google.com/store/apps/details?id=com.brbrs.qarib) |
| [Runa](runa/) | A personal journal | 'com.brbrs.runa' | ✅ [Live on Google Play](https://play.google.com/store/apps/details?id=com.brbrs.runa) |
| [Vinci](https://github.com/andreibarburas/vinci-android) | Nextcloud Personal CRM | 'com.brbrs.vinci' | ✅ [Live on Google Play](https://play.google.com/store/apps/details?id=com.brbrs.vinci) |

## Philosophy

These apps are built for people who:
- Run their own Nextcloud instance or other self-hosted services
- Care about where their data lives
- Want beautiful, native Android apps that don't compromise on privacy

Every app in this suite:
- Communicates only with your own server
- Collects no analytics or telemetry
- Contains no ads
- Works offline first

## Tech Stack

All apps share the same foundation:

- **Kotlin** + **Jetpack Compose** + **Material Design 3**
- **Hilt** for dependency injection
- **Room** for offline-first local storage
- **DataStore** for preferences
- **OkHttp** + **org.json** for networking (no Retrofit/Gson)
- **Coil** for image loading
- **Nextcloud Login Flow v2** for authentication

## Support

If these apps save you time, a small tip means a lot:

- **Donate:** [bunq.me/barburasdonations](https://bunq.me/barburasdonations)
- **Website:** [barburas.com](https://barburas.com)
- **Issues & feature requests:** [open an issue](https://github.com/andreibarburas/android-apps/issues)

## License

All apps in this suite are open source. See the 'LICENSE' file in each app folder for details.

---

*These apps are not affiliated with or endorsed by Nextcloud GmbH or any other third-party service.*
