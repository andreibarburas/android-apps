package com.brbrs.blik.ui.screens.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.brbrs.blik.ui.theme.CyanPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val HANDLE_RADIUS = 20f   // touch target for corner handles
private const val MIN_CROP_PX  = 60f   // minimum crop rectangle size

@Composable
fun CropScreen(
    localPath: String,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val context = LocalContext.current

    // Load the original bitmap on a background thread
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(localPath) {
        originalBitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(Uri.parse(localPath))
                    ?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { null }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val bmp = originalBitmap
        if (bmp == null) {
            CircularProgressIndicator(
                color = CyanPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            CropEditor(
                bitmap   = bmp,
                onDismiss = onDismiss,
                onConfirm = { cropRect, displayRect, canvasSize ->
                    // Map crop rect from display coords back to bitmap coords
                    val scaleX = bmp.width  / displayRect.width
                    val scaleY = bmp.height / displayRect.height
                    val bx = ((cropRect.left   - displayRect.left) * scaleX).toInt().coerceIn(0, bmp.width)
                    val by = ((cropRect.top    - displayRect.top)  * scaleY).toInt().coerceIn(0, bmp.height)
                    val bw = (cropRect.width  * scaleX).toInt().coerceIn(1, bmp.width  - bx)
                    val bh = (cropRect.height * scaleY).toInt().coerceIn(1, bmp.height - by)
                    val cropped = Bitmap.createBitmap(bmp, bx, by, bw, bh)
                    onCropped(cropped)
                },
            )
        }
    }
}

@Composable
private fun CropEditor(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: (cropRect: Rect, displayRect: Rect, canvasSize: IntSize) -> Unit,
) {
    // Canvas size determined after layout
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // The rect within the canvas where the image is drawn (letterboxed)
    val imageDisplayRect by remember(canvasSize, bitmap) {
        derivedStateOf {
            if (canvasSize == IntSize.Zero) return@derivedStateOf Rect.Zero
            val cw = canvasSize.width.toFloat()
            val ch = canvasSize.height.toFloat()
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()
            val scale = min(cw / iw, ch / ih)
            val dw = iw * scale
            val dh = ih * scale
            val dx = (cw - dw) / 2f
            val dy = (ch - dh) / 2f
            Rect(dx, dy, dx + dw, dy + dh)
        }
    }

    // Crop rect in canvas coordinates — starts as full image
    var cropRect by remember(imageDisplayRect) {
        mutableStateOf(imageDisplayRect)
    }

    // Which handle is being dragged: TL, TR, BL, BR, or MOVE
    var dragHandle by remember { mutableStateOf<DragHandle?>(null) }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val primary = CyanPrimary
    val overlay  = Color.Black.copy(alpha = 0.55f)

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Canvas: image + crop overlay (inset to 90% to avoid system gesture zones) ──
        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .align(Alignment.Center)
        ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(imageDisplayRect) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            dragHandle = hitTest(pos, cropRect)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            cropRect = moveCropRect(cropRect, dragHandle, drag, imageDisplayRect)
                        },
                        onDragEnd    = { dragHandle = null },
                        onDragCancel = { dragHandle = null },
                    )
                }
        ) {
            // Draw the image
            if (imageDisplayRect != Rect.Zero) {
                drawImage(
                    image      = imageBitmap,
                    dstOffset  = androidx.compose.ui.unit.IntOffset(
                        imageDisplayRect.left.toInt(), imageDisplayRect.top.toInt()
                    ),
                    dstSize    = androidx.compose.ui.unit.IntSize(
                        imageDisplayRect.width.toInt(), imageDisplayRect.height.toInt()
                    ),
                )
            }

            // Darken everything outside crop
            if (cropRect != Rect.Zero) {
                // Top
                drawRect(overlay, topLeft = Offset(0f, 0f),
                    size = Size(size.width, cropRect.top))
                // Bottom
                drawRect(overlay, topLeft = Offset(0f, cropRect.bottom),
                    size = Size(size.width, size.height - cropRect.bottom))
                // Left
                drawRect(overlay, topLeft = Offset(0f, cropRect.top),
                    size = Size(cropRect.left, cropRect.height))
                // Right
                drawRect(overlay, topLeft = Offset(cropRect.right, cropRect.top),
                    size = Size(size.width - cropRect.right, cropRect.height))

                // Crop border
                drawRect(
                    color     = primary,
                    topLeft   = Offset(cropRect.left, cropRect.top),
                    size      = Size(cropRect.width, cropRect.height),
                    style     = Stroke(width = 2.dp.toPx()),
                )

                // Rule-of-thirds grid lines
                val thirdW = cropRect.width  / 3f
                val thirdH = cropRect.height / 3f
                val gridColor = primary.copy(alpha = 0.35f)
                for (i in 1..2) {
                    drawLine(gridColor,
                        Offset(cropRect.left + thirdW * i, cropRect.top),
                        Offset(cropRect.left + thirdW * i, cropRect.bottom),
                        strokeWidth = 1.dp.toPx())
                    drawLine(gridColor,
                        Offset(cropRect.left,  cropRect.top + thirdH * i),
                        Offset(cropRect.right, cropRect.top + thirdH * i),
                        strokeWidth = 1.dp.toPx())
                }

                // Corner + edge-midpoint handles (8 total)
                val hr = HANDLE_RADIUS * 0.55f
                val mx = (cropRect.left + cropRect.right)  / 2f
                val my = (cropRect.top  + cropRect.bottom) / 2f
                listOf(
                    Offset(cropRect.left,  cropRect.top),    // TL
                    Offset(cropRect.right, cropRect.top),    // TR
                    Offset(cropRect.left,  cropRect.bottom), // BL
                    Offset(cropRect.right, cropRect.bottom), // BR
                    Offset(mx,             cropRect.top),    // TM
                    Offset(mx,             cropRect.bottom), // BM
                    Offset(cropRect.left,  my),              // ML
                    Offset(cropRect.right, my),              // MR
                ).forEach { c ->
                    drawCircle(primary,     radius = hr,          center = c)
                    drawCircle(Color.White, radius = hr * 0.45f,  center = c)
                }
            }
        }
        } // end inset Box

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back",
                    tint = Color.White)
            }
            Text("Crop", style = MaterialTheme.typography.titleLarge,
                color = Color.White, modifier = Modifier.weight(1f))
            // Reset button
            IconButton(onClick = { cropRect = imageDisplayRect }) {
                Icon(Icons.Outlined.CropFree, "Reset crop", tint = Color.White)
            }
        }

        // ── Confirm button ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    if (cropRect != Rect.Zero && imageDisplayRect != Rect.Zero) {
                        onConfirm(cropRect, imageDisplayRect, canvasSize)
                    }
                },
                containerColor = primary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Check, null, tint = Color.Black)
                    Text("Crop & Share", color = Color.Black,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private enum class DragHandle { TL, TR, BL, BR, TM, BM, ML, MR, MOVE }

/** Corner and edge-midpoint handles take priority; anything inside the rect is a MOVE drag. */
private fun hitTest(pos: Offset, crop: Rect): DragHandle? {
    val r  = HANDLE_RADIUS * 2.5f
    val mx = (crop.left + crop.right)  / 2f
    val my = (crop.top  + crop.bottom) / 2f
    return when {
        (pos - Offset(crop.left,  crop.top   )).getDistance() < r -> DragHandle.TL
        (pos - Offset(crop.right, crop.top   )).getDistance() < r -> DragHandle.TR
        (pos - Offset(crop.left,  crop.bottom)).getDistance() < r -> DragHandle.BL
        (pos - Offset(crop.right, crop.bottom)).getDistance() < r -> DragHandle.BR
        (pos - Offset(mx,         crop.top   )).getDistance() < r -> DragHandle.TM
        (pos - Offset(mx,         crop.bottom)).getDistance() < r -> DragHandle.BM
        (pos - Offset(crop.left,  my         )).getDistance() < r -> DragHandle.ML
        (pos - Offset(crop.right, my         )).getDistance() < r -> DragHandle.MR
        crop.contains(pos)                                         -> DragHandle.MOVE
        else                                                       -> null
    }
}

private fun moveCropRect(
    current: Rect,
    handle: DragHandle?,
    drag: Offset,
    bounds: Rect,
): Rect {
    if (handle == null) return current

    var l = current.left
    var t = current.top
    var r = current.right
    var b = current.bottom
    val dx = drag.x
    val dy = drag.y

    when (handle) {
        DragHandle.TL   -> { l += dx; t += dy }
        DragHandle.TR   -> { r += dx; t += dy }
        DragHandle.BL   -> { l += dx; b += dy }
        DragHandle.BR   -> { r += dx; b += dy }
        DragHandle.TM   -> { t += dy }
        DragHandle.BM   -> { b += dy }
        DragHandle.ML   -> { l += dx }
        DragHandle.MR   -> { r += dx }
        DragHandle.MOVE -> { l += dx; t += dy; r += dx; b += dy }
    }

    // Clamp to image bounds and enforce minimum size
    l = l.coerceIn(bounds.left, r - MIN_CROP_PX)
    t = t.coerceIn(bounds.top,  b - MIN_CROP_PX)
    r = r.coerceIn(l + MIN_CROP_PX, bounds.right)
    b = b.coerceIn(t + MIN_CROP_PX, bounds.bottom)

    // For MOVE: keep the rect fully inside bounds without resizing it
    if (handle == DragHandle.MOVE) {
        val w = r - l
        val h = b - t
        l = l.coerceIn(bounds.left, bounds.right  - w)
        t = t.coerceIn(bounds.top,  bounds.bottom - h)
        r = l + w
        b = t + h
    }

    return Rect(l, t, r, b)
}
