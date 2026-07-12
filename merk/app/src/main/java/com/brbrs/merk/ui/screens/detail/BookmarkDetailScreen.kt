package com.brbrs.merk.ui.screens.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.brbrs.merk.ui.components.RemindMeButton
import com.brbrs.merk.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailScreen(
    url: String,
    title: String,
    tasksEnabled: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val context   = LocalContext.current
    val isDark    = LocalIsDark.current
    var pageTitle by remember { mutableStateOf(title.ifBlank { url }) }
    var progress  by remember { mutableFloatStateOf(0f) }
    var loading   by remember { mutableStateOf(true) }

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavySurface))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    val glowColor = if (isDark) GlowRed else GlowRedLight
    val barBg     = if (isDark) NavyDeep.copy(alpha = 0.96f) else LightSurface

    Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {

        // ── Hero radial glow ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        radius = 500f,
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(barBg),
            ) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pageTitle,
                            style    = MaterialTheme.typography.titleMedium,
                            color    = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Uri.parse(url).host ?: url,
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RemindMeButton(bookmarkTitle = pageTitle, bookmarkUrl = url,
                        tasksEnabled = tasksEnabled, iconOnly = true)
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, "Open in browser",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (loading) {
                    LinearProgressIndicator(
                        progress   = { progress },
                        modifier   = Modifier.fillMaxWidth().height(2.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = barBg,
                    )
                }
            }

            // ── WebView ───────────────────────────────────────────────────────
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory  = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled        = true
                        settings.domStorageEnabled         = true
                        settings.loadsImagesAutomatically  = true
                        settings.useWideViewPort           = true
                        settings.loadWithOverviewMode      = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView, request: WebResourceRequest,
                            ): Boolean {
                                val scheme = request.url.scheme?.lowercase() ?: ""
                                return when (scheme) {
                                    "http", "https" -> false
                                    else -> {
                                        try {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW, request.url)
                                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                            )
                                        } catch (e: ActivityNotFoundException) { }
                                        true
                                    }
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                                loading  = newProgress < 100
                            }
                            override fun onReceivedTitle(view: WebView, t: String?) {
                                if (!t.isNullOrBlank()) pageTitle = t
                            }
                        }
                        loadUrl(url)
                    }
                }
            )
        }
    }
}
