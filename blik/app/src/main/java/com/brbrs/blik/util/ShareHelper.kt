package com.brbrs.blik.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareHelper {

    /**
     * Copy the image at [sourceUri] to the app's cache and share it via ACTION_SEND.
     * Uses FileProvider so any app can read it without special permissions.
     */
    fun shareImage(context: Context, sourceUri: Uri, fileName: String) {
        val file = copyToCache(context, sourceUri, fileName) ?: return
        launchShareIntent(context, fileToContentUri(context, file))
    }

    /**
     * Save a cropped [bitmap] to cache and share it.
     */
    fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String) {
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(shareDir, "crop_$fileName")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        launchShareIntent(context, fileToContentUri(context, file))
    }

    /**
     * Copy the image at [sourceUri] to cache and return the cached [File], or null on failure.
     */
    fun copyToCache(context: Context, sourceUri: Uri, fileName: String): File? {
        return try {
            val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val dest = File(shareDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest
        } catch (e: Exception) {
            null
        }
    }

    private fun fileToContentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun launchShareIntent(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share screenshot"))
    }
}
