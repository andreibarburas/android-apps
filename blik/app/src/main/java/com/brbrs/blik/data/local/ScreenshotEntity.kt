package com.brbrs.blik.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UploadStatus { PENDING, UPLOADING, UPLOADED, ERROR }

@Entity(tableName = "screenshots")
data class ScreenshotEntity(
    @PrimaryKey val localPath: String,       // Absolute path on device — primary key
    val fileName: String,                    // e.g. Screenshot_20250605_094123.png
    val fileHash: String,                    // SHA-256 of file content — dedup key
    val fileSizeBytes: Long,
    val capturedAt: Long,                    // epoch millis from MediaStore
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val remoteFolder: String = "",           // Nextcloud folder where it was uploaded
    val remotePath: String = "",             // Full remote path including filename
    val category: String = "",              // AI-assigned category (travel, food, etc.)
    val aiDescription: String = "",         // AI-generated description
    val note: String = "",                  // User note (saved to .md sidecar)
    val tags: String = "",                  // Comma-separated tags
    val uploadErrorMsg: String = "",        // Last upload error message
    val lastAttemptAt: Long = 0L,
    val isBlurred: Boolean = false,         // privacy blur in gallery thumbnails
    val isStarred: Boolean = false,         // starred / favourited
    val isLocalFileMissing: Boolean = false, // file deleted externally from phone
)
