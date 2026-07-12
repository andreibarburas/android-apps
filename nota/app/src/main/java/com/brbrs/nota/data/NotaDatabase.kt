package com.brbrs.nota.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NotaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
