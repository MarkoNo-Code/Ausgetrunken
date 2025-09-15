package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import android.util.Log
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
 * Wine photo service using file system + DataStore approach, similar to NewWineyardPhotoService
 */
class WinePhotoService(
    private val context: Context,
    private val photoStorage: SimplePhotoStorage,
    private val uploadStatusStorage: PhotoUploadStatusStorage,
    private val uploadService: BackgroundPhotoUploadService
) {
    companion object {
        private const val TAG = "WinePhotoService"
        private const val PHOTOS_DIR = "wine_images"
        private const val MAX_PHOTOS_PER_WINE = 3
    }

    // Use consistent, reliable storage location
    private val photosDirectory: File by lazy {
        val preferredDirectory = context.getExternalFilesDir(PHOTOS_DIR)
        val directory = preferredDirectory ?: File(context.filesDir, PHOTOS_DIR)

        directory.apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Created photos directory: $absolutePath (success: $created)")
            }
            Log.d(TAG, "Using photos directory: $absolutePath")
        }
    }

    /**
     * Get photos with upload status for a wine - returns Flow for reactive UI
     */
    fun getWinePhotosWithStatus(wineId: String): Flow<List<PhotoWithStatus>> {
        Log.d(TAG, "Getting photos with status for wine: $wineId")

        return combine(
            photoStorage.getWinePhotos(wineId),
            uploadStatusStorage.getUploadStatusesFlow(emptyList()) // Will be updated dynamically
        ) { photoPaths, uploadStatuses ->
            Log.d(TAG, "Combining ${photoPaths.size} wine photos with upload statuses")

            // Get current upload statuses for these specific paths
            val currentStatuses = mutableMapOf<String, com.ausgetrunken.domain.model.PhotoUploadInfo>()
            photoPaths.forEach { path ->
                try {
                    val status = uploadStatusStorage.getUploadStatus(path)
                    currentStatuses[path] = status
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get status for $path", e)
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

            Log.d(TAG, "Returning ${photosWithStatus.size} wine photos with status")
            photosWithStatus
        }
    }

    /**
     * Add photo from URI and start background upload
     */
    suspend fun addPhoto(wineId: String, imageUri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Adding photo for wine $wineId from URI: $imageUri")

                // Check current photo count
                val currentPhotos = photoStorage.getWinePhotosSync(wineId)
                if (currentPhotos.size >= MAX_PHOTOS_PER_WINE) {
                    Log.w(TAG, "Maximum number of photos ($MAX_PHOTOS_PER_WINE) reached for wine $wineId")
                    return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINE photos allowed per wine"))
                }

                // Generate unique filename
                val timestamp = System.currentTimeMillis()
                val filename = "wine_${wineId}_${timestamp}_${UUID.randomUUID()}.jpg"
                val localFile = File(photosDirectory, filename)

                // Copy URI content to local file
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    FileOutputStream(localFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (!localFile.exists() || localFile.length() == 0L) {
                    Log.e(TAG, "Failed to copy image file from URI")
                    return@withContext Result.failure(Exception("Failed to copy image file"))
                }

                Log.d(TAG, "Image copied to local file: ${localFile.absolutePath} (${localFile.length()} bytes)")

                // Add to photo storage immediately - this provides instant UI update
                photoStorage.addWinePhoto(wineId, localFile.absolutePath)
                Log.d(TAG, "Photo path added to wine storage")

                // Initialize upload status as pending
                uploadStatusStorage.updateUploadStatus(localFile.absolutePath, UploadStatus.PENDING)

                // Queue for background upload
                uploadService.queuePhotoForUpload(localFile.absolutePath)
                Log.d(TAG, "Wine photo queued for background upload")

                Result.success(localFile.absolutePath)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to add photo for wine $wineId", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Add photo from file path and start background upload
     */
    suspend fun addPhoto(wineId: String, photoPath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Adding photo for wine $wineId from path: $photoPath")

                // Check current photo count
                val currentPhotos = photoStorage.getWinePhotosSync(wineId)
                if (currentPhotos.size >= MAX_PHOTOS_PER_WINE) {
                    Log.w(TAG, "Maximum number of photos ($MAX_PHOTOS_PER_WINE) reached for wine $wineId")
                    return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINE photos allowed per wine"))
                }

                val sourceFile = File(photoPath)
                if (!sourceFile.exists()) {
                    Log.e(TAG, "Source photo file does not exist: $photoPath")
                    return@withContext Result.failure(Exception("Photo file does not exist"))
                }

                // Generate unique filename in our directory
                val timestamp = System.currentTimeMillis()
                val filename = "wine_${wineId}_${timestamp}_${UUID.randomUUID()}.jpg"
                val localFile = File(photosDirectory, filename)

                // Copy file to our managed directory
                sourceFile.copyTo(localFile, overwrite = false)

                Log.d(TAG, "Image copied to local file: ${localFile.absolutePath} (${localFile.length()} bytes)")

                // Add to photo storage immediately - this provides instant UI update
                photoStorage.addWinePhoto(wineId, localFile.absolutePath)
                Log.d(TAG, "Photo path added to wine storage")

                // Initialize upload status as pending
                uploadStatusStorage.updateUploadStatus(localFile.absolutePath, UploadStatus.PENDING)

                // Queue for background upload
                uploadService.queuePhotoForUpload(localFile.absolutePath)
                Log.d(TAG, "Wine photo queued for background upload")

                Result.success(localFile.absolutePath)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to add photo for wine $wineId", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Remove photo
     */
    suspend fun removePhoto(wineId: String, photoPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Removing photo for wine $wineId: $photoPath")

                // Remove from storage
                photoStorage.removeWinePhoto(wineId, photoPath)

                // Remove upload status
                uploadStatusStorage.removeUploadStatus(photoPath)

                Log.d(TAG, "Photo removed successfully")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove photo for wine $wineId", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Clear all photos for a wine
     */
    suspend fun clearAllPhotos(wineId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Clearing all photos for wine $wineId")

                val currentPhotos = photoStorage.getWinePhotosSync(wineId)

                // Remove upload statuses
                currentPhotos.forEach { photoPath ->
                    uploadStatusStorage.removeUploadStatus(photoPath)
                }

                // Clear from storage (this will also handle local file deletion)
                photoStorage.clearWinePhotos(wineId)

                Log.d(TAG, "All photos cleared for wine $wineId")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear photos for wine $wineId", e)
                Result.failure(e)
            }
        }
    }
}