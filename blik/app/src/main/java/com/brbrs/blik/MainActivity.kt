/*
 * Blik — a Nextcloud screenshot manager for Android
 * Copyright (C) 2026 andrei BARBURAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See <https://www.gnu.org/licenses/>.
 */

package com.brbrs.blik

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import com.brbrs.blik.data.repository.SettingsRepository
import com.brbrs.blik.network.WebDavClient
import com.brbrs.blik.ui.BlikNavHost
import com.brbrs.blik.ui.theme.BlikTheme
import com.brbrs.blik.ui.theme.TextScale
import com.brbrs.blik.ui.theme.TextScalePreference
import com.brbrs.blik.ui.theme.ThemeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import coil.ImageLoader

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var webDavClient: WebDavClient
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var textScalePreference: TextScalePreference
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by themeRepository.isDark.collectAsStateWithLifecycle(initialValue = true)
            val textScale by textScalePreference.scale.collectAsStateWithLifecycle(
                initialValue = TextScale.DEFAULT
            )
            val useInterTight by settingsRepository.useInterTight.collectAsStateWithLifecycle(
                initialValue = false
            )
            BlikTheme(isDark = isDark, textScaleMultiplier = textScale.multiplier, useCustomFont = useInterTight) {
                BlikNavHost(webDavClient = webDavClient, imageLoader = imageLoader)
            }
        }
    }
}
