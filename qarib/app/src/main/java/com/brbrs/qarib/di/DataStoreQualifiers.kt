package com.brbrs.qarib.di

import javax.inject.Qualifier

/**
 * Qualifier for the DataStore<Preferences> backing app settings
 * (sync bookkeeping such as last sync time).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore
