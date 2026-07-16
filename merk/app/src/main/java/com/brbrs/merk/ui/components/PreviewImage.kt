package com.brbrs.merk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

private val FALLBACK_COLORS = listOf(
    Color(0xFF1A6B9A), Color(0xFF9A3A1A), Color(0xFF1A9A5A),
    Color(0xFF6B1A9A), Color(0xFF9A8A1A), Color(0xFF1A4A9A),
)

/**
 * Shows the Nextcloud Bookmarks link preview/screenshot for a bookmark
 * (populated server-side by a screenshot provider such as ScreenshotMachine
 * or Screeenly). Falls back to a blurred favicon over a coloured gradient
 * when no preview is available (dead link, provider not configured, etc).
 *
 * @param bookmarkId  The bookmark's remote ID.
 * @param serverUrl   The Nextcloud server base URL.
 * @param authHeader  Basic-auth header value ("Basic …") for the Nextcloud API.
 * @param title       Used to derive a stable fallback colour.
 */
@Composable
fun PreviewImage(
    bookmarkId: Long,
    serverUrl:  String,
    authHeader: String,
    title:      String,
    height:     Dp,
    modifier:   Modifier = Modifier,
) {
    val context        = LocalContext.current
    var useFallback     by remember(bookmarkId, serverUrl) { mutableStateOf(serverUrl.isBlank()) }

    val color = FALLBACK_COLORS[title.hashCode().and(0x7FFFFFFF) % FALLBACK_COLORS.size]

    // GET {serverUrl}/index.php/apps/bookmarks/public/rest/v2/bookmark/{id}/image
    val previewUrl = remember(bookmarkId, serverUrl) {
        "$serverUrl/index.php/apps/bookmarks/public/rest/v2/bookmark/$bookmarkId/image"
    }
    // Favicon reused as the blurred backdrop when no preview exists
    val faviconUrl = remember(bookmarkId, serverUrl) {
        "$serverUrl/index.php/apps/bookmarks/public/rest/v2/bookmark/$bookmarkId/favicon"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(size = 14.dp)),
    ) {
        if (!useFallback) {
            val request = remember(previewUrl, authHeader) {
                ImageRequest.Builder(context)
                    .data(previewUrl)
                    .addHeader("Authorization", authHeader)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model              = request,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
                onState            = { state ->
                    if (state is AsyncImagePainter.State.Error) useFallback = true
                },
            )
        } else {
            // Blurred favicon over a gradient, so the placeholder still feels
            // tied to the bookmark rather than a blank block.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.25f)),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (serverUrl.isNotBlank()) {
                    val faviconRequest = remember(faviconUrl, authHeader) {
                        ImageRequest.Builder(context)
                            .data(faviconUrl)
                            .addHeader("Authorization", authHeader)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model              = faviconRequest,
                        contentDescription = null,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .fillMaxSize()
                            .blur(22.dp),
                    )
                }
            }
        }
    }
}
