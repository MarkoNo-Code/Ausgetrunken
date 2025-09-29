package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import com.ausgetrunken.domain.model.PhotoWithStatus
import com.ausgetrunken.domain.model.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * New simplified photo service using file system + DataStore approach
 * This replaces the complex Room database approach with a reliable, simple solution
 */
class NewWineryPhotoService(
    private val context: Context,
    private val photoStorage: SimplePhotoStorage,
    private val uploadStatusStorage: PhotoUploadStatusStorage,
    private val uploadService: BackgroundPhotoUploadService
) {
    companion object {
        private const val TAG = "NewWineryPhotoService"
        private const val PHOTOS_DIR = "winery_images"
        private const val MAX_PHOTOS_PER_WINERY = 3
    }
    
    // Use consistent, reliable storage location
    private val photosDirectory: File by lazy {
        val preferredDirectory = context.getExternalFilesDir(PHOTOS_DIR)
        val directory = preferredDirectory ?: File(context.filesDir, PHOTOS_DIR)
        
        directory.apply {
            if (!exists()) {
                val created = mkdirs()
            }
        }
    }
    
    /**
     * Get photos with upload status for a winery - returns Flow for reactive UI
     */
    fun getWineryPhotosWithStatus(wineryId: String): Flow<List<PhotoWithStatus>> {
        
        return combine(
            photoStorage.getPhotoPaths(wineryId),
            uploadStatusStorage.getUploadStatusesFlow(emptyList()) // Will be updated dynamically
        ) { photoPaths, uploadStatuses ->
            
            // Get current upload statuses for these specific paths
            val currentStatuses = mutableMapOf<String, com.ausgetrunken.domain.model.PhotoUploadInfo>()
            photoPaths.forEach { path ->
                try {
                    val status = uploadStatusStorage.getUploadStatus(path)
                    currentStatuses[path] = status
                } catch (e: Exception) {
                    currentStatuses[path] = com.ausgetrunken.domain.model.PhotoUploadInfo(
                        localPath = path,
                        status = UploadStatus.PENDING
                    )
                }
            }
            
            // Create PhotoWithStatus objects
            val photosWithStatus = photoPaths.map { path ->
                PhotoWithStatus(
                    localPath = path,
                    uploadInfo = currentStatuses[path] ?: com.ausgetrunken.domain.model.PhotoUploadInfo(
                        localPath = path,
                        status = UploadStatus.PENDING
                    )
                )
            }
            
            photosWithStatus
        }
    }
    
    /**
     * Get simple photo paths for immediate display (no status info)
     */
    fun getWineryPhotos(wineryId: String): Flow<List<String>> {
        return photoStorage.getPhotoPaths(wineryId)
    }
    
    /**
     * Add a photo from URI - immediate local storage + background upload
     */
    suspend fun addPhoto(wineryId: String, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            
            // Check photo limit
            val currentCount = photoStorage.getPhotoCount(wineryId)
            if (currentCount >= MAX_PHOTOS_PER_WINERY) {
                return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINERY photos allowed per winery"))
            }
            
            // Copy to local storage immediately
            val localFile = copyUriToLocalStorage(imageUri, wineryId)
            if (localFile == null) {
                return@withContext Result.failure(Exception("Failed to save photo locally"))
            }
            
            
            // Add to photo storage immediately - this provides instant UI update
            photoStorage.addPhotoPath(wineryId, localFile.absolutePath)
            
            // Initialize upload status as pending
            uploadStatusStorage.updateUploadStatus(localFile.absolutePath, UploadStatus.PENDING)
            
            // Queue for background upload
            uploadService.queuePhotoForUpload(localFile.absolutePath)
            
            Result.success(localFile.absolutePath)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add a photo from file path
     */
    suspend fun addPhoto(wineryId: String, filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $filePath"))
            }
            
            
            // Check photo limit
            val currentCount = photoStorage.getPhotoCount(wineryId)
            if (currentCount >= MAX_PHOTOS_PER_WINERY) {
                return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINERY photos allowed per winery"))
            }
            
            // Copy to our photos directory if needed
            val finalFile = if (sourceFile.parent == photosDirectory.absolutePath) {
                sourceFile
            } else {
                copyFileToLocalStorage(sourceFile, wineryId) 
                    ?: return@withContext Result.failure(Exception("Failed to copy photo locally"))
            }
            
            // Add to photo storage immediately
            photoStorage.addPhotoPath(wineryId, finalFile.absolutePath)
            
            // Initialize upload status and queue for upload
            uploadStatusStorage.updateUploadStatus(finalFile.absolutePath, UploadStatus.PENDING)
            uploadService.queuePhotoForUpload(finalFile.absolutePath)
            
            Result.success(finalFile.absolutePath)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove a photo from a winery
     */
    suspend fun removePhoto(wineryId: String, photoPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            
            // Remove from photo storage
            photoStorage.removePhotoPath(wineryId, photoPath)
            
            // Remove upload status
            uploadStatusStorage.removeUploadStatus(photoPath)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clear all photos for a winery
     */
    suspend fun clearAllPhotos(wineryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            
            // Get current paths to clean up upload statuses
            val currentPaths = photoStorage.getPhotoPathsSync(wineryId)
            
            // Clear from storage (this also deletes physical files)
            photoStorage.clearAllPhotos(wineryId)
            
            // Clean up upload statuses
            currentPaths.forEach { path ->
                uploadStatusStorage.removeUploadStatus(path)
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Retry failed uploads for a winery
     */
    fun retryFailedUploads() {
        uploadService.retryFailedUploads()
    }
    
    /**
     * Get upload statistics
     */
    suspend fun getUploadStats(): UploadStats {
        return uploadService.getUploadStats()
    }
    
    /**
     * Copy URI to local storage
     */
    private suspend fun copyUriToLocalStorage(uri: Uri, wineryId: String): File? {
        return try {
            val fileName = "winery_${wineryId}_${UUID.randomUUID()}.jpg"
            val destFile = File(photosDirectory, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (destFile.exists() && destFile.length() > 0) {
                destFile
            } else {
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Copy file to local storage
     */
    private fun copyFileToLocalStorage(sourceFile: File, wineryId: String): File? {
        return try {
            val fileName = "winery_${wineryId}_${UUID.randomUUID()}.jpg"
            val destFile = File(photosDirectory, fileName)
            
            sourceFile.copyTo(destFile, overwrite = true)
            
            if (destFile.exists() && destFile.length() > 0) {
                destFile
            } else {
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Start the background upload service
     */
    fun startUploadService() {
        uploadService.startUploadService()
    }
    
    /**
     * Stop the background upload service
     */
    fun stopUploadService() {
        uploadService.stopUploadService()
    }
    
    /**
     * Debug logging
     */
    suspend fun logServiceState(wineryId: String) {
        photoStorage.logStorageState()
        uploadStatusStorage.logUploadStatuses()
        val stats = getUploadStats()
    }
}