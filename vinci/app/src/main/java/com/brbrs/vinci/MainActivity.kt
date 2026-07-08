package com.brbrs.vinci

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brbrs.vinci.ui.VinciNavGraph
import com.brbrs.vinci.ui.theme.VinciTheme
import com.brbrs.vinci.ui.theme.ThemeRepository
import com.brbrs.vinci.ui.theme.DisplayPreferencesRepository
import com.brbrs.vinci.ui.theme.textSizeMultiplier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var displayPrefs: DisplayPreferencesRepository

    // Extras from CallReceiver notification tap
    var pendingContactId: Long? = null
    var pendingPhone: String    = ""
    var pendingIsOutgoing: Boolean = true
    var pendingDuration: Int    = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallIntent(intent)
        enableEdgeToEdge()
        setContent {
            val isDark by themeRepository.isDark.collectAsStateWithLifecycle(initialValue = true)
            val textSize by displayPrefs.preferences.collectAsStateWithLifecycle(initialValue = com.brbrs.vinci.ui.theme.DisplayPreferences())
            VinciTheme(isDark = isDark, textScale = textSizeMultiplier(textSize.textSize), useCustomFont = textSize.useCustomFont) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VinciNavGraph(
                        pendingContactId = pendingContactId,
                        pendingPhone     = pendingPhone,
                        onPendingConsumed = {
                            pendingContactId = null
                            pendingPhone = ""
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent?.getStringExtra("action") == "log_call") {
            pendingContactId = intent.getLongExtra("contact_id", -1L).takeIf { it >= 0 }
            pendingPhone     = intent.getStringExtra("phone") ?: ""
            pendingIsOutgoing= intent.getBooleanExtra("is_outgoing", true)
            pendingDuration  = intent.getIntExtra("duration", 0)
        }
    }
}
