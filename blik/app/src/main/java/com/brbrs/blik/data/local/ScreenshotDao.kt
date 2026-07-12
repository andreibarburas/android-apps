package com.brbrs.blik.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    @Query("SELECT * FROM screenshots ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE uploadStatus = 'PENDING' OR uploadStatus = 'ERROR' ORDER BY capturedAt DESC")
    fun observePending(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE uploadStatus = 'UPLOADED' ORDER BY capturedAt DESC")
    fun observeUploaded(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE note != '' OR tags != '' ORDER BY capturedAt DESC")
    fun observeWithNotes(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE isStarred = 1 ORDER BY capturedAt DESC")
    fun observeStarred(): Flow<List<ScreenshotEntity>>

    @Query("""
        SELECT * FROM screenshots
        WHERE fileName LIKE '%' || :q || '%'
           OR note LIKE '%' || :q || '%'
           OR tags LIKE '%' || :q || '%'
           OR aiDescription LIKE '%' || :q || '%'
           OR category LIKE '%' || :q || '%'
        ORDER BY capturedAt DESC
    """)
    fun search(q: String): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE category = :cat ORDER BY capturedAt DESC")
    fun filterByCategory(cat: String): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE localPath = :path")
    suspend fun getByPath(path: String): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE fileHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE uploadStatus = 'PENDING' OR uploadStatus = 'ERROR'")
    suspend fun getPendingUpload(): List<ScreenshotEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(screenshot: ScreenshotEntity)

    @Update
    suspend fun update(screenshot: ScreenshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(screenshot: ScreenshotEntity)

    @Query("DELETE FROM screenshots WHERE localPath = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT DISTINCT category FROM screenshots WHERE category != '' ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>
}
