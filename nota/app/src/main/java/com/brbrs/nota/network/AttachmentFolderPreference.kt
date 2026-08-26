package com.brbrs.nota.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.attachmentDataStore by preferencesDataStore(name = "nota_attachments")

@Singleton
class AttachmentFolderPreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val DEFAULT_FOLDER = "Nota Attachments"
    }

    private val FOLDER_KEY = stringPreferencesKey("attachment_folder")

    val folderName: Flow<String> = context.attachmentDataStore.data
        .map { prefs -> prefs[FOLDER_KEY]?.takeIf { it.isNotBlank() } ?: DEFAULT_FOLDER }

    suspend fun setFolderName(name: String) {
        context.attachmentDataStore.edit {
            it[FOLDER_KEY] = name.trim().ifBlank { DEFAULT_FOLDER }
        }
    }
}
