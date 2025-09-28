package com.ausgetrunken.domain.service

import android.content.Context
import android.util.Log
import com.ausgetrunken.domain.model.UploadStatus
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background service for uploading photos to cloud storage
 * Runs on a separate thread and provides upload status updates
 */
class BackgroundPhotoUploadService(
    private val context: Context,
    private val imageUploadService: ImageUploadService,
    private val uploadStatusStorage: PhotoUploadStatusStorage
) {
    companion object {
        private const val TAG = "BackgroundPhotoUpload"
        private const val MAX_CONCURRENT_UPLOADS = 2
        private const val RETRY_DELAY_MS = 5000L
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val activeUploads = mutableSetOf<String>()
    
    /**
     * Start the background upload service
     */
    fun startUploadService() {
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "🚀 Starting background photo upload service")
            uploadScope.launch {
                runUploadLoop()
            }
        } else {
            Log.d(TAG, "Upload service already running")
        }
    }
    
    /**
     * Stop the background upload service
     */
    fun stopUploadService() {
        if (isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "🛑 Stopping background photo upload service")
            uploadScope.cancel()
        }
    }
    
    /**
     * Queue a photo for upload immediately
     */
    fun queuePhotoForUpload(localPath: String) {
        Log.d(TAG, "📤 Queueing photo for upload: $localPath")
        
        uploadScope.launch {
            try {
                // Mark as pending if not already uploading
                val currentStatus = uploadStatusStorage.getUploadStatus(localPath)
                if (currentStatus.status != UploadStatus.UPLOADING && currentStatus.status != UploadStatus.COMPLETED) {
                    uploadStatusStorage.updateUploadStatus(localPath, UploadStatus.PENDING)
                }
                
                // Trigger upload loop
                processUploadQueue()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue photo for upload", e)
            }
        }
    }
    
    /**
     * Force retry failed uploads
     */
    fun retryFailedUploads() {
        Log.d(TAG, "🔄 Retrying failed uploads")
        
        uploadScope.launch {
            try {
                val pendingUploads = uploadStatusStorage.getPendingUploads()
                val failedUploads = pendingUploads.filter { it.status == UploadStatus.FAILED }
                
                failedUploads.forEach { uploadInfo ->
                    uploadStatusStorage.updateUploadStatus(
                        uploadInfo.localPath, 
                        UploadStatus.PENDING,
                        errorMessage = null
                    )
                }
                
                Log.d(TAG, "Marked ${failedUploads.size} failed uploads for retry")
                processUploadQueue()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to retry failed uploads", e)
            }
        }
    }
    
    /**
     * Main upload loop that processes the queue
     */
    private suspend fun runUploadLoop() {
        Log.d(TAG, "📋 Starting upload loop")
        
        while (isRunning.get()) {
            try {
                processUploadQueue()
                delay(10000) // Check queue every 10 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error in upload loop", e)
                delay(5000) // Wait before retrying
            }
        }
        
        Log.d(TAG, "Upload loop stopped")
    }
    
    /**
     * Process the upload queue with concurrency limits
     */
    private suspend fun processUploadQueue() {
        try {
            val pendingUploads = uploadStatusStorage.getPendingUploads()
            val eligibleUploads = pendingUploads.filter { uploadInfo ->
                uploadInfo.localPath !in activeUploads && 
                File(uploadInfo.localPath).exists() &&
                (uploadInfo.status == UploadStatus.PENDING || 
                 (uploadInfo.status == UploadStatus.FAILED && shouldRetry(uploadInfo)))
            }
            
            if (eligibleUploads.isEmpty()) {
                return
            }
            
            Log.d(TAG, "Processing ${eligibleUploads.size} eligible uploads (${activeUploads.size} active)")
            
            // Start uploads up to the concurrency limit
            val availableSlots = MAX_CONCURRENT_UPLOADS - activeUploads.size
            val uploadsToStart = eligibleUploads.take(availableSlots)
            
            uploadsToStart.forEach { uploadInfo ->
                uploadScope.launch {
                    uploadPhoto(uploadInfo.localPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing upload queue", e)
        }
    }
    
    /**
     * Upload a single photo
     */
    private suspend fun uploadPhoto(localPath: String) {
        if (!activeUploads.add(localPath)) {
            Log.w(TAG, "Upload already in progress for: $localPath")
            return
        }
        
        try {
            Log.d(TAG, "📤 Starting upload: $localPath")
            
            val file = File(localPath)
            if (!file.exists()) {
                Log.w(TAG, "File no longer exists: $localPath")
                uploadStatusStorage.updateUploadStatus(
                    localPath, 
                    UploadStatus.FAILED,
                    errorMessage = "File not found"
                )
                return
            }
            
            // Update status to uploading
            uploadStatusStorage.updateUploadStatus(localPath, UploadStatus.UPLOADING, progress = 0f)
            
            // Perform the actual upload
            val result = imageUploadService.uploadWineryImage("", file)
            
            result.fold(
                onSuccess = { cloudUrl ->
                    Log.d(TAG, "✅ Upload successful: $localPath -> $cloudUrl")
                    uploadStatusStorage.updateUploadStatus(
                        localPath,
                        UploadStatus.COMPLETED,
                        cloudUrl = cloudUrl,
                        progress = 1f
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Upload failed: $localPath", error)
                    uploadStatusStorage.updateUploadStatus(
                        localPath,
                        UploadStatus.FAILED,
                        errorMessage = error.message ?: "Upload failed"
                    )
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload exception: $localPath", e)
            uploadStatusStorage.updateUploadStatus(
                localPath,
                UploadStatus.FAILED,
                errorMessage = e.message ?: "Upload exception"
            )
        } finally {
            activeUploads.remove(localPath)
        }
    }
    
    /**
     * Determine if a failed upload should be retried
     */
    private fun shouldRetry(uploadInfo: com.ausgetrunken.domain.model.PhotoUploadInfo): Boolean {
        val timeSinceLastAttempt = System.currentTimeMillis() - uploadInfo.lastAttempt
        return timeSinceLastAttempt > RETRY_DELAY_MS
    }
    
    /**
     * Get current upload statistics
     */
    suspend fun getUploadStats(): UploadStats {
        return try {
            val allUploads = uploadStatusStorage.getPendingUploads()
            UploadStats(
                pending = allUploads.count { it.status == UploadStatus.PENDING },
                uploading = activeUploads.size,
                failed = allUploads.count { it.status == UploadStatus.FAILED },
                completed = allUploads.count { it.status == UploadStatus.COMPLETED }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get upload stats", e)
            UploadStats(0, 0, 0, 0)
        }
    }
}

/**
 * Upload statistics data class
 */
data class UploadStats(
    val pending: Int,
    val uploading: Int,
    val failed: Int,
    val completed: Int
) {
    val total: Int get() = pending + uploading + failed + completed
    val hasWork: Boolean get() = pending > 0 || uploading > 0
}