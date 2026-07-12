package com.brbrs.merk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookmarkEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BookmarksDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun folderDao(): FolderDao
}
