package com.brbrs.runa.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages journal entry photos in context.filesDir/photos/.
 * Files named "{entryId}-{index}-{timestamp}.jpg" for cache-busting.
 * Supports multiple photos per entry.
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MAX_DIMENSION = 1600
        private const val JPEG_QUALITY  = 85
    }

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    /**
     * Saves a photo from [sourceUri] for a given [entryId] and [index] (photo slot).
     * Returns the new absolute path, or null on failure.
     * Optionally deletes [previousPhotoPath] after successful write.
     */
    suspend fun savePhoto(
        sourceUri: Uri,
        entryId: String,
        index: Int,
        previousPhotoPath: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeAndOrient(sourceUri) ?: return@withContext null
            val fileName = "$entryId-$index-${System.currentTimeMillis()}.jpg"
            val outFile  = File(photosDir, fileName)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()

            if (!previousPhotoPath.isNullOrBlank()) {
                runCatching { File(previousPhotoPath).delete() }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Post-processes a freshly captured camera photo — downsample + EXIF orient.
     * Call after TakePicture succeeds.
     */
    suspend fun processCameraCapture(capturedFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val uri    = Uri.fromFile(capturedFile)
            val bitmap = decodeAndOrient(uri) ?: return@withContext capturedFile.absolutePath
            FileOutputStream(capturedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            capturedFile.absolutePath
        } catch (e: Exception) {
            capturedFile.absolutePath
        }
    }

    /** Deletes a photo file. Safe to call with blank path. */
    suspend fun deletePhoto(photoPath: String) = withContext(Dispatchers.IO) {
        if (photoPath.isNotBlank()) runCatching { File(photoPath).delete() }
    }

    /** Deletes all photo files in the provided list. */
    suspend fun deletePhotos(photoPaths: List<String>) {
        photoPaths.forEach { deletePhoto(it) }
    }

    /**
     * Creates an empty camera capture target file + content:// URI via FileProvider.
     * Pass the URI to ActivityResultContracts.TakePicture().
     */
    fun createCameraCaptureTarget(entryId: String, index: Int): CameraCapture {
        val fileName = "$entryId-$index-${System.currentTimeMillis()}-capture.jpg"
        val file = File(photosDir, fileName)
        val uri  = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CameraCapture(file, uri)
    }

    data class CameraCapture(val file: File, val uri: Uri)

    private fun decodeAndOrient(uri: Uri): Bitmap? {
        val cr = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        val options    = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap     = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotation = readExifRotation(uri)
        if (rotation == 0) return bitmap

        val matrix  = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun readExifRotation(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) { 0 }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1; var w = width; var h = height
        while (w / 2 >= maxDimension || h / 2 >= maxDimension) { w /= 2; h /= 2; sampleSize *= 2 }
        return sampleSize
    }
}
