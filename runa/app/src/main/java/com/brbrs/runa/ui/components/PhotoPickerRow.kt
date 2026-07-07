package com.brbrs.runa.ui.components

import android.net.Uri
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.brbrs.runa.data.local.PhotoStorage
import java.io.File

/**
 * Horizontal photo strip for Write/Edit screens.
 * Shows existing photos as thumbnails + an "Add" button at the end.
 * Max 5 photos per entry.
 */
@Composable
fun PhotoPickerRow(
    photoPaths: List<String>,
    onPhotoPicked: (Uri, Int) -> Unit,
    onCreateCameraCapture: (Int) -> PhotoStorage.CameraCapture,
    onCameraCaptureComplete: (PhotoStorage.CameraCapture, Int) -> Unit,
    onRemovePhoto: (Int) -> Unit,
) {
    val context        = LocalContext.current
    var pendingCapture by remember { mutableStateOf<Pair<PhotoStorage.CameraCapture, Int>?>(null) }
    var pickingIndex   by remember { mutableStateOf(0) }
    var pendingCameraIndex by remember { mutableStateOf<Int?>(null) }

    // Camera permission launcher — required before TakePicture on API 28+
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val index = pendingCameraIndex ?: return@rememberLauncherForActivityResult
            pendingCameraIndex = null
            // Permission granted — caller should retry camera launch
            // We signal this by setting pendingCameraIndex back to trigger recomposition
        }
    }

    fun launchCamera(index: Int, capture: PhotoStorage.CameraCapture, launcher: androidx.activity.result.ActivityResultLauncher<Uri>) {
        val hasCameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            pendingCapture = Pair(capture, index)
            launcher.launch(capture.uri)
        } else {
            pendingCameraIndex = index
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPhotoPicked(uri, pickingIndex)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val pending = pendingCapture
        if (success && pending != null) onCameraCaptureComplete(pending.first, pending.second)
        pendingCapture = null
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(photoPaths) { index, path ->
            Box(modifier = Modifier.size(80.dp)) {
                SubcomposeAsyncImage(
                    model             = File(path),
                    contentDescription = null,
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                // Remove button
                IconButton(
                    onClick  = { onRemovePhoto(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Add button — only shown if under 5 photos
        if (photoPaths.size < 5) {
            item {
                var showPicker by remember { mutableStateOf(false) }
                val nextIndex = photoPaths.size

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add photo",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }

                if (showPicker) {
                    AlertDialog(
                        onDismissRequest = { showPicker = false },
                        title = { Text("Add photo") },
                        text  = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        showPicker  = false
                                        pickingIndex = nextIndex
                                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Choose from gallery")
                                }
                                TextButton(
                                    onClick = {
                                        showPicker = false
                                        val capture = onCreateCameraCapture(nextIndex)
                                        launchCamera(nextIndex, capture, cameraLauncher)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Take a photo")
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showPicker = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}
