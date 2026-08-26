package com.brbrs.nota

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brbrs.nota.ui.NotaNavGraph
import com.brbrs.nota.ui.theme.NotaTheme
import com.brbrs.nota.ui.theme.TextScalePreference
import com.brbrs.nota.ui.theme.ThemeRepository
import com.brbrs.nota.ui.theme.FontPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var textScalePreference: TextScalePreference
    @Inject lateinit var fontPreference: FontPreference

    private val _sharedText     = mutableStateOf<String?>(null)
    private val _sharedImageUri = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only consume the share intent on fresh launch (not config change)
        if (savedInstanceState == null) {
            consumeShareIntent(intent)
        }

        setContent {
            val isDark by themeRepository.isDark.collectAsStateWithLifecycle(initialValue = true)
            val textScale by textScalePreference.scale.collectAsStateWithLifecycle(
                initialValue = com.brbrs.nota.ui.theme.TextScale.DEFAULT
            )
            val customFont by fontPreference.customFontEnabled.collectAsStateWithLifecycle(initialValue = false)

            NotaTheme(isDark = isDark, textScaleMultiplier = textScale.multiplier, customFont = customFont) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotaNavGraph(
                        sharedText     = _sharedText.value,
                        sharedImageUri = _sharedImageUri.value,
                        onShareConsumed = {
                            _sharedText.value     = null
                            _sharedImageUri.value = null
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun consumeShareIntent(intent: Intent?) {
        _sharedText.value     = resolveSharedText(intent)
        _sharedImageUri.value = resolveSharedImage(intent)?.toString()
    }

    private fun resolveSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val raw = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
    }

    private fun resolveSharedImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val mimeType = intent.type ?: return null
        if (!mimeType.startsWith("image/")) return null
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
