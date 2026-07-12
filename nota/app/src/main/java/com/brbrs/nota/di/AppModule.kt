package com.brbrs.nota.di

import android.content.Context
import androidx.room.Room
import com.brbrs.nota.data.NoteDao
import com.brbrs.nota.data.NotaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NotaDatabase =
        Room.databaseBuilder(context, NotaDatabase::class.java, "nota.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideNoteDao(db: NotaDatabase): NoteDao = db.noteDao()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Cache DNS resolutions so they survive the browser round-trip during
        // Nextcloud Login Flow v2 (Android's system DNS cache expires too fast)
        val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()
        val cachingDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return dnsCache.getOrPut(hostname) {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(cachingDns)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Nota/1.3.1 (Android)")
                        .build()
                )
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }
}
