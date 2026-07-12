# org.json and OkHttp - keep for runtime reflection safety
-keep class org.json.** { *; }
-dontwarn org.json.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
