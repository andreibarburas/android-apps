package com.brbrs.nota.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.jeziellago.compose.markdowntext.MarkdownText

private val markdownImageRegex = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

fun extractImageUrls(markdown: String): List<String> =
    markdownImageRegex.findAll(markdown).map { it.groupValues[2] }.toList()

/**
 * Renders images found in markdown — used in note list cards and editor strip.
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

/**
 * Splits markdown into alternating text/image segments and renders each inline.
 * This preserves the authorial intent — images appear where placed in the text,
 * not bunched at the bottom.
 */
@Composable
fun InlineMarkdownWithImages(
    markdown: String,
    imageLoader: ImageLoader?,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    // Split into segments: text blocks and image URLs, in document order
    val segments = buildSegments(markdown)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.TextBlock -> {
                    if (segment.text.isNotBlank()) {
                        MarkdownText(
                            markdown = segment.text.trim(),
                            style = textStyle,
                        )
                    }
                }
                is Segment.Image -> {
                    if (imageLoader != null) {
                        AsyncImage(
                            model = segment.url,
                            contentDescription = segment.alt.ifBlank { null },
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
    }
}

private sealed class Segment {
    data class TextBlock(val text: String) : Segment()
    data class Image(val alt: String, val url: String) : Segment()
}

private fun buildSegments(markdown: String): List<Segment> {
    val segments = mutableListOf<Segment>()
    var lastEnd = 0
    for (match in markdownImageRegex.findAll(markdown)) {
        if (match.range.first > lastEnd) {
            segments += Segment.TextBlock(markdown.substring(lastEnd, match.range.first))
        }
        segments += Segment.Image(
            alt = match.groupValues[1],
            url = match.groupValues[2],
        )
        lastEnd = match.range.last + 1
    }
    if (lastEnd < markdown.length) {
        segments += Segment.TextBlock(markdown.substring(lastEnd))
    }
    return segments
}
