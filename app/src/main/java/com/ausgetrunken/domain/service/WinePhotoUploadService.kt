package com.ausgetrunken.domain.service

import android.content.Context
import com.ausgetrunken.data.local.dao.WinePhotoDao
import com.ausgetrunken.data.local.entities.PhotoUploadStatus
import com.ausgetrunken.domain.model.UploadStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background service for uploading wine photos to cloud storage
 * Uses unified photo upload service for consistent behavior across all photo types
 */
class WinePhotoUploadService(
    private val context: Context,
    private val unifiedPhotoUploadService: UnifiedPhotoUploadService,
    private val uploadStatusStorage: PhotoUploadStatusStorage,
    private val winePhotoDao: WinePhotoDao,
    private val postgrest: Postgrest
) {
    companion object {
        private const val TAG = "WinePhotoUploadService"
        private const val MAX_CONCURRENT_UPLOADS = 2
        private const val RETRY_DELAY_MS = 5000L
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val activeUploads = mutableSetOf<String>()

    /**
     * Queue wine photo for upload
     */
    fun queueWinePhotoForUpload(photoPath: String, wineId: String) {
        uploadScope.launch {
            try {

                // Update status to pending
                uploadStatusStorage.updateUploadStatus(photoPath, UploadStatus.PENDING)

                // Start upload
                uploadWinePhoto(photoPath, wineId)

            } catch (e: Exception) {
                uploadStatusStorage.updateUploadStatus(photoPath, UploadStatus.FAILED)
            }
        }
    }

    /**
     * Upload wine photo with retry logic
     */
    private suspend fun uploadWinePhoto(photoPath: String, wineId: String) {
        if (activeUploads.contains(photoPath)) {
            return
        }

        activeUploads.add(photoPath)

        try {
            uploadStatusStorage.updateUploadStatus(photoPath, UploadStatus.UPLOADING)

            val file = File(photoPath)
            if (!file.exists()) {
                uploadStatusStorage.updateUploadStatus(photoPath, UploadStatus.FAILED)
                return
            }

            var attempt = 0
            var uploadSuccess = false

            while (attempt < MAX_RETRY_ATTEMPTS && !uploadSuccess) {
                attempt++

                try {
                    val result = unifiedPhotoUploadService.uploadWinePhoto(file, wineId)

                    result.onSuccess { remoteUrl ->
                        uploadStatusStorage.updateUploadStatus(
                            photoPath,
                            UploadStatus.COMPLETED,
                            remoteUrl
                        )

                        // Update Supabase database with remote URL
                        try {

                            // Find the photo record by local path and update it
                            // Note: This is a simple approach - could be optimized with a direct update query
                            val updateData = mapOf(
                                "remote_url" to remoteUrl,
                                "upload_status" to "UPLOADED",
                                "updated_at" to Instant.now().toString()
                            )

                            // Update by matching local_path (this will update the correct record)
                            // For now, just log the update - we can implement this later

                        } catch (e: Exception) {
                            // Don't fail the upload - the photo is still uploaded to storage
                        }

                        uploadSuccess = true
                    }.onFailure { error ->
                        if (attempt >= MAX_RETRY_ATTEMPTS) {
                            uploadStatusStorage.updateUploadStatus(photoPath, UploadStatus.FAILED)
                        } else {
                            delay(RETRY_DELAY_MS)
                        }
                    }

                } catch (e: Exception) {
                    if (attempt >= MAX_RETRY_ATTEMPTS) {
                        uploadStatusStorage.updateUploadStatus(photoPath, UploadStatus.FAILED)
                    } else {
                        delay(RETRY_DELAY_MS)
                    }
                }
            }

        } finally {
            activeUploads.remove(photoPath)
        }
    }

    /**
     * Cancel all uploads and stop service
     */
    fun stopUploadService() {
        if (isRunning.compareAndSet(true, false)) {
            uploadScope.cancel()
            activeUploads.clear()
        }
    }
}