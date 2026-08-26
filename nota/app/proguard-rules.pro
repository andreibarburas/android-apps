# ── OkHttp + Okio ─────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── org.json ──────────────────────────────────────────────────────────────────
# Used directly for Nextcloud API parsing — keep to avoid R8 ClassCastException
-keep class org.json.** { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
