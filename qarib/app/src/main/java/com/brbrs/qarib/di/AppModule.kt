package com.brbrs.qarib.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import androidx.room.Room
import com.brbrs.qarib.data.local.QaribDatabase
import com.brbrs.qarib.data.local.dao.PlaceDao
import com.brbrs.qarib.data.remote.NextcloudWebDavClient
import com.brbrs.qarib.data.remote.NominatimClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// Settings DataStore delegate — instantiated once here via the
// @SettingsDataStore qualifier. The auth and display-preferences
// DataStores are each instantiated once in their own repository files
// (AuthRepository.kt -> "qarib_auth", DisplayPreferencesRepository.kt
// -> "qarib_display") per the one-delegate-per-file rule.
private val Context.settingsDataStoreDelegate: DataStore<Preferences> by preferencesDataStore(name = "qarib_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @SettingsDataStore
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStoreDelegate

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Cache DNS lookups per hostname. Some network setups (Tailscale
        // MagicDNS, certain VPN/DNS configurations) resolve a hostname
        // successfully on the first request but intermittently fail on
        // subsequent lookups within the same session (e.g. during Login
        // Flow v2 polling). Caching the first successful result avoids
        // those follow-up failures.
        val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()
        val cachingDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return dnsCache.getOrPut(hostname) { Dns.SYSTEM.lookup(hostname) }
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .dns(cachingDns)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Qarib/1.2.0 (Android)")
                        .build()
                )
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideQaribDatabase(@ApplicationContext context: Context): QaribDatabase =
        Room.databaseBuilder(context, QaribDatabase::class.java, QaribDatabase.DATABASE_NAME)
            .addMigrations(QaribDatabase.MIGRATION_1_2, QaribDatabase.MIGRATION_2_3, QaribDatabase.MIGRATION_3_4, QaribDatabase.MIGRATION_4_5)
            .build()

    @Provides
    fun providePlaceDao(database: QaribDatabase): PlaceDao = database.placeDao()

    @Provides
    @Singleton
    fun provideNominatimClient(client: OkHttpClient): NominatimClient = NominatimClient(client)

    @Provides
    @Singleton
    fun provideNextcloudWebDavClient(client: OkHttpClient): NextcloudWebDavClient = NextcloudWebDavClient(client)
}
