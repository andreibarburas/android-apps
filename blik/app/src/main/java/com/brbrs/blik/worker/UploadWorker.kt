package com.brbrs.blik.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.blik.data.repository.ScreenshotRepository
import com.brbrs.blik.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: ScreenshotRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val onConflict = settings.onConflict.firstOrNull() ?: "ASK"
            val pending = repo.observePending().firstOrNull() ?: emptyList()
            for (entity in pending) {
                repo.uploadScreenshot(entity, onConflict)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
