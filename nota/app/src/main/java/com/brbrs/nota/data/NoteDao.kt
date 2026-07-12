package com.brbrs.nota.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY favorite DESC, modified DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND category = :category ORDER BY favorite DESC, modified DESC")
    fun getNotesByCategory(category: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY modified DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT category FROM notes WHERE isDeleted = 0 AND category != '' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET isDirty = 1, title = :title, content = :content, category = :category, modified = :modified WHERE id = :id")
    suspend fun updateNoteLocal(id: Long, title: String, content: String, category: String, modified: Long)

    @Query("UPDATE notes SET isLocked = :locked WHERE id = :id")
    suspend fun setNoteLocked(id: Long, locked: Boolean)

    @Query("UPDATE notes SET isDeleted = 1, isDirty = 1 WHERE id = :id")
    suspend fun markDeleted(id: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND id > 0")
    suspend fun getAllNotesSnapshot(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isDirty = 1 AND isDeleted = 0")
    suspend fun getDirtyNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isDirty = 1 AND isDeleted = 1")
    suspend fun getDeletedNotes(): List<NoteEntity>

    @Query("UPDATE notes SET isDirty = 0, etag = :etag WHERE id = :id")
    suspend fun markSynced(id: Long, etag: String)
}
