package com.brbrs.nota.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage

private val markdownImageRegex = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

fun extractImageUrls(markdown: String): List<String> =
    markdownImageRegex.findAll(markdown).map { it.groupValues[2] }.toList()

/**
 * Renders images found in markdown using an authenticated imageLoader.
 * Used both in the note list cards and in the editor preview.
 */
@Composable
fun NoteImageStrip(
    markdown: String,
    imageLoader: ImageLoader?,
    modifier: Modifier = Modifier,
    maxImages: Int = 3,
    cropImages: Boolean = true,
) {
    if (imageLoader == null) return
    val urls = extractImageUrls(markdown).take(maxImages)
    if (urls.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        urls.forEach { url ->
            if (cropImages) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}
