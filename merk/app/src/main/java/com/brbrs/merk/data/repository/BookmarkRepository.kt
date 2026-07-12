package com.brbrs.merk.data.repository

import com.brbrs.merk.auth.AuthManager
import com.brbrs.merk.data.local.BookmarkDao
import com.brbrs.merk.data.local.BookmarkEntity
import com.brbrs.merk.data.local.FolderDao
import com.brbrs.merk.data.local.FolderEntity
import com.brbrs.merk.network.BookmarksApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val folderDao: FolderDao,
    private val apiClient: BookmarksApiClient,
    private val authManager: AuthManager,
) {
    fun observeAll(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()
    fun search(query: String): Flow<List<BookmarkEntity>> = bookmarkDao.search(query)
    fun filterByTag(tag: String): Flow<List<BookmarkEntity>> = bookmarkDao.filterByTag(tag)
    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    suspend fun getAllTags(): List<String> = withContext(Dispatchers.IO) {
        bookmarkDao.getAllTagStrings()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    suspend fun getById(id: Long): BookmarkEntity? = withContext(Dispatchers.IO) {
        bookmarkDao.getById(id)
    }

    suspend fun saveLocal(entity: BookmarkEntity) = withContext(Dispatchers.IO) {
        bookmarkDao.upsert(entity.copy(isDirty = true))
    }

    suspend fun markForDelete(id: Long) = withContext(Dispatchers.IO) {
        val existing = bookmarkDao.getById(id) ?: return@withContext
        bookmarkDao.upsert(existing.copy(isPendingDelete = true))
    }

    suspend fun createFolder(title: String, parentId: Long = -1): Result<FolderEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val creds = authManager.credentials.firstOrNull() ?: error("Not logged in")
                val folder = apiClient.createFolder(creds, title, parentId)
                folderDao.upsert(folder)
                folder
            }
        }

    suspend fun sync(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val creds = authManager.credentials.firstOrNull() ?: return@runCatching

            // 1. Push dirty local changes
            for (dirty in bookmarkDao.getDirty()) {
                if (dirty.id < 0) {
                    val created = apiClient.createBookmark(creds, dirty)
                    bookmarkDao.deleteById(dirty.id)
                    bookmarkDao.upsert(created.copy(isDirty = false))
                } else {
                    apiClient.updateBookmark(creds, dirty)
                    bookmarkDao.upsert(dirty.copy(isDirty = false))
                }
            }

            // 2. Push pending deletes
            for (pending in bookmarkDao.getPendingDelete()) {
                if (pending.id > 0) apiClient.deleteBookmark(creds, pending.id)
                bookmarkDao.deleteById(pending.id)
            }

            // 3. Pull remote — delete anything no longer on the server
            val remote = apiClient.fetchAllBookmarks(creds)
            val remoteIds = remote.map { it.id }.toSet()
            bookmarkDao.deleteNotIn(remoteIds.toList())
            bookmarkDao.upsertAll(remote.map { it.copy(isDirty = false) })

            // 4. Pull folders
            val folders = apiClient.fetchFolders(creds)
            folderDao.upsertAll(folders)
        }
    }

    suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        bookmarkDao.deleteAll()
        folderDao.deleteAll()
    }
}
