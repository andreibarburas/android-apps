package com.brbrs.runa.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportResult {
    data class Success(val file: File) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalRepository: JournalRepository,
) {
    /**
     * Exports all journal entries and their photos as a ZIP file.
     *
     * Structure inside the ZIP:
     *   runa-export/
     *     entries.json          ← all entry metadata as a JSON array
     *     photos/
     *       {entryId}-0.jpg
     *       {entryId}-1.jpg
     *       ...
     *
     * Returns the ZIP File on success so the caller can share it.
     */
    suspend fun exportAll(): ExportResult = withContext(Dispatchers.IO) {
        try {
            val entries = journalRepository.getAllForExport()
            val dateStr = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            val zipFile = File(context.cacheDir, "runa-export-$dateStr.zip")
            if (zipFile.exists()) zipFile.delete()

            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->

                // ── entries.json ──────────────────────────────────────────────
                val jsonArray = JSONArray()
                for (entry in entries) {
                    val obj = JSONObject().apply {
                        put("id",              entry.id)
                        put("title",          entry.title)
                        put("body",           entry.body)
                        put("entryDateTimeMs", entry.entryDateTimeMs)
                        put("createdAtMs",    entry.createdAtMs)
                        put("locationName",   entry.locationName ?: "")
                        entry.latitude?.let  { put("latitude",  it) }
                        entry.longitude?.let { put("longitude", it) }
                        put("photoCount",     entry.photoPaths.size)
                    }
                    jsonArray.put(obj)
                }
                val jsonBytes = jsonArray.toString(2).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("runa-export/entries.json"))
                zip.write(jsonBytes)
                zip.closeEntry()

                // ── photos ────────────────────────────────────────────────────
                for (entry in entries) {
                    entry.photoPaths.forEachIndexed { index, path ->
                        val photoFile = File(path)
                        if (!photoFile.exists()) return@forEachIndexed
                        val ext      = photoFile.extension.ifBlank { "jpg" }
                        val zipName  = "runa-export/photos/${entry.id}-$index.$ext"
                        zip.putNextEntry(ZipEntry(zipName))
                        photoFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }

            ExportResult.Success(zipFile)
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Export failed")
        }
    }
}
