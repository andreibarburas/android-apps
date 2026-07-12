package com.brbrs.qarib.data.local

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
 * Manages user-attached place photos on local storage, under
 * context.filesDir/photos/. Files are named "{placeId}-{timestamp}.jpg"
 * so updating a place's photo always produces a new filename — this
 * busts Coil's URI-based cache, which otherwise keeps showing the old
 * image after a photo is replaced.
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MAX_DIMENSION = 1600
        private const val JPEG_QUALITY = 85
    }

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    /**
     * Copies and downsamples the image at [sourceUri] (from a gallery
     * picker or camera capture) into local storage for [placeId],
     * correcting orientation from EXIF data. Returns the new file's
     * absolute path, or null on failure. Deletes [previousPhotoPath] if
     * provided, once the new file is written successfully.
     */
    suspend fun savePhoto(sourceUri: Uri, placeId: String, previousPhotoPath: String? = null): String? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeAndOrient(sourceUri) ?: return@withContext null
                val fileName = "$placeId-${System.currentTimeMillis()}.jpg"
                val outFile = File(photosDir, fileName)
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
     * Post-processes a freshly captured camera photo at [capturedFile]
     * (downsamples + corrects EXIF orientation), replacing it in place.
     * Call this after [CameraCapture] succeeds, before using the file as
     * a place's photo.
     */
    suspend fun processCameraCapture(capturedFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.fromFile(capturedFile)
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

    /** Deletes the photo file for [photoPath], if any. Safe to call with an empty path. */
    suspend fun deletePhoto(photoPath: String) = withContext(Dispatchers.IO) {
        if (photoPath.isNotBlank()) {
            runCatching { File(photoPath).delete() }
        }
    }

    /**
     * Writes [bytes] downloaded from Nextcloud as the photo for [placeId].
     * Used during sync when a remote place has a photo this device
     * doesn't have locally yet.
     */
    suspend fun savePhotoBytes(bytes: ByteArray, placeId: String): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "$placeId-${System.currentTimeMillis()}.jpg"
            val outFile = File(photosDir, fileName)
            outFile.writeBytes(bytes)
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Reads the photo file at [photoPath] into a byte array for upload, or null if missing. */
    suspend fun readPhotoBytes(photoPath: String): ByteArray? = withContext(Dispatchers.IO) {
        if (photoPath.isBlank()) return@withContext null
        val file = File(photoPath)
        if (!file.exists()) return@withContext null
        runCatching { file.readBytes() }.getOrNull()
    }

    /**
     * Creates an empty file under context.filesDir/photos/ and returns it
     * along with a content:// URI for it via FileProvider, suitable for
     * ActivityResultContracts.TakePicture(). The camera writes the
     * captured image directly to [CameraCapture.file]; use that path as
     * the new place photo once capture succeeds.
     */
    fun createCameraCaptureTarget(placeId: String): CameraCapture {
        val fileName = "$placeId-${System.currentTimeMillis()}-capture.jpg"
        val file = File(photosDir, fileName)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CameraCapture(file, uri)
    }

    data class CameraCapture(val file: File, val uri: Uri)

    private fun decodeAndOrient(uri: Uri): Bitmap? {
        val contentResolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotationDegrees = readExifRotation(uri)
        if (rotationDegrees == 0) return bitmap

        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun readExifRotation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }
}
