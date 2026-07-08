package com.brbrs.merk

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import com.brbrs.merk.ui.BookmarksNavHost
import com.brbrs.merk.ui.theme.BookmarksTheme
import com.brbrs.merk.ui.theme.FontPreference
import com.brbrs.merk.ui.theme.TextScalePreference
import com.brbrs.merk.ui.theme.ThemeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var textScalePreference: TextScalePreference
    @Inject lateinit var fontPreference: FontPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUrl   = extractSharedUrl(intent)
        val sharedTitle = extractSharedTitle(intent)

        setContent {
            val isDark         by themeRepository.isDark.collectAsStateWithLifecycle(initialValue = true)
            val textScale      by textScalePreference.textScale.collectAsStateWithLifecycle(
                initialValue = com.brbrs.merk.ui.theme.TextScale.DEFAULT
            )
            val useCustomFont  by fontPreference.useCustomFont.collectAsStateWithLifecycle(initialValue = false)
            BookmarksTheme(isDark = isDark, textScale = textScale.multiplier, useCustomFont = useCustomFont) {
                BookmarksNavHost(
                    sharedUrl   = sharedUrl,
                    sharedTitle = sharedTitle,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return text.split("\\s+".toRegex()).firstOrNull {
            it.startsWith("http://") || it.startsWith("https://")
        } ?: text.trim().takeIf { it.startsWith("http") }
    }

    private fun extractSharedTitle(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_SUBJECT)
    }
}
