package com.brbrs.blik.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.brbrs.blik.data.local.BlikDatabase
import com.brbrs.blik.data.local.ScreenshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.net.ssl.*

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthDataStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SettingsDataStore

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @AuthDataStore
    fun provideAuthDataStore(@ApplicationContext ctx: Context) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { ctx.preferencesDataStoreFile("auth") }
        )

    @Provides @Singleton @SettingsDataStore
    fun provideSettingsDataStore(@ApplicationContext ctx: Context) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { ctx.preferencesDataStoreFile("settings") }
        )

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val trustManager = buildTrustManager()
        val sslContext   = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, session ->
                try { session.peerCertificates.isNotEmpty() } catch (e: Exception) { false }
            }
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Blik/1.0 (Android) Nextcloud-WebDAV")
                        .build()
                )
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    private fun buildTrustManager(): X509TrustManager {
        val systemKs = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(systemKs)
        val systemTm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        return object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = systemTm.acceptedIssuers
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                systemTm.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try { systemTm.checkServerTrusted(chain, authType) } catch (e: Exception) { }
            }
        }
    }

    @Provides @Singleton
    fun provideImageLoader(
        @ApplicationContext ctx: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(ctx)
        .okHttpClient(okHttpClient)
        .memoryCache {
            MemoryCache.Builder(ctx).maxSizePercent(0.25).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(ctx.cacheDir.resolve("image_cache"))
                .maxSizePercent(0.02)
                .build()
        }
        .build()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): BlikDatabase =
        Room.databaseBuilder(ctx, BlikDatabase::class.java, "blik.db")
            .addMigrations(BlikDatabase.MIGRATION_1_2, BlikDatabase.MIGRATION_2_3, BlikDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideScreenshotDao(db: BlikDatabase): ScreenshotDao = db.screenshotDao()
}
