package com.brbrs.merk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val parentFolderId: Long,
    val isDirty: Boolean = false,
    val isPendingDelete: Boolean = false,
)
