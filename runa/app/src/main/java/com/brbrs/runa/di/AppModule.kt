package com.brbrs.runa.di

import android.content.Context
import androidx.room.Room
import com.brbrs.runa.data.local.RunaDatabase
import com.brbrs.runa.data.local.dao.JournalEntryDao
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
    fun provideRunaDatabase(@ApplicationContext context: Context): RunaDatabase =
        Room.databaseBuilder(context, RunaDatabase::class.java, RunaDatabase.DATABASE_NAME)
            .addMigrations(RunaDatabase.MIGRATION_1_2)
            .build()

    @Provides @Singleton
    fun provideJournalEntryDao(db: RunaDatabase): JournalEntryDao = db.journalEntryDao()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dnsCache  = ConcurrentHashMap<String, List<InetAddress>>()
        val cachingDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                dnsCache.getOrPut(hostname) { Dns.SYSTEM.lookup(hostname) }
        }
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .dns(cachingDns)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Runa/1.0.0 (Android)")
                        .build()
                )
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }
}
