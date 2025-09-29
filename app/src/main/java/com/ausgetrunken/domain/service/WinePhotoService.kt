package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import com.ausgetrunken.data.local.dao.WinePhotoDao
import com.ausgetrunken.data.local.entities.WinePhotoEntity
import com.ausgetrunken.data.local.entities.PhotoUploadStatus
import com.ausgetrunken.domain.model.PhotoWithStatus
import com.ausgetrunken.domain.model.UploadStatus
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID

@Serializable
data class SupabaseWinePhoto(
    val id: String,
    @SerialName("wine_id") val wineId: String,
    @SerialName("remote_url") val remoteUrl: String? = null,
    @SerialName("local_path") val localPath: String? = null,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("upload_status") val uploadStatus: String = "LOCAL_ONLY",
    @SerialName("file_size") val fileSize: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

/**
 * Wine photo service with database sync, similar to WineryPhotoService
 */
class WinePhotoService(
    private val context: Context,
    private val photoStorage: SimplePhotoStorage,
    private val uploadStatusStorage: PhotoUploadStatusStorage,
    private val winePhotoUploadService: WinePhotoUploadService,
    private val winePhotoDao: WinePhotoDao,
    private val postgrest: Postgrest
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
            }
        }
    }

    /**
     * Get photos with upload status for a wine - returns Flow for reactive UI
     */
    fun getWinePhotosWithStatus(wineId: String): Flow<List<PhotoWithStatus>> {

        return combine(
            photoStorage.getWinePhotos(wineId),
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
     * Add photo from URI and start background upload
     */
    suspend fun addPhoto(wineId: String, imageUri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {

                // Check current photo count
                val currentPhotos = photoStorage.getWinePhotosSync(wineId)
                if (currentPhotos.size >= MAX_PHOTOS_PER_WINE) {
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
                    return@withContext Result.failure(Exception("Failed to copy image file"))
                }


                // Add to photo storage immediately - this provides instant UI update
                photoStorage.addWinePhoto(wineId, localFile.absolutePath)

                // Initialize upload status as pending
                uploadStatusStorage.updateUploadStatus(localFile.absolutePath, UploadStatus.PENDING)

                // Save to database for persistence and sync
                val photoEntity = WinePhotoEntity(
                    id = UUID.randomUUID().toString(),
                    wineId = wineId,
                    localPath = localFile.absolutePath,
                    remoteUrl = null, // Will be updated after upload
                    displayOrder = 0,
                    uploadStatus = PhotoUploadStatus.LOCAL_ONLY,
                    fileSize = localFile.length(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // SYNC TO SUPABASE DATABASE TABLE
                try {
                    val supabasePhoto = SupabaseWinePhoto(
                        id = photoEntity.id,
                        wineId = photoEntity.wineId,
                        remoteUrl = photoEntity.remoteUrl,
                        localPath = photoEntity.localPath,
                        displayOrder = photoEntity.displayOrder,
                        uploadStatus = photoEntity.uploadStatus.toString(),
                        fileSize = photoEntity.fileSize,
                        createdAt = Instant.now().toString(),
                        updatedAt = Instant.now().toString()
                    )

                    postgrest.from("wine_photos").insert(supabasePhoto)
                } catch (e: Exception) {
                    // Don't fail the entire operation - local database is still updated
                }

                // Save to local database
                winePhotoDao.insertPhoto(photoEntity)

                // Queue for background upload
                winePhotoUploadService.queueWinePhotoForUpload(localFile.absolutePath, wineId)

                Result.success(localFile.absolutePath)

            } catch (e: Exception) {
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

                // Check current photo count
                val currentPhotos = photoStorage.getWinePhotosSync(wineId)
                if (currentPhotos.size >= MAX_PHOTOS_PER_WINE) {
                    return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINE photos allowed per wine"))
                }

                val sourceFile = File(photoPath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(Exception("Photo file does not exist"))
                }

                // Generate unique filename in our directory
                val timestamp = System.currentTimeMillis()
                val filename = "wine_${wineId}_${timestamp}_${UUID.randomUUID()}.jpg"
                val localFile = File(photosDirectory, filename)

                // Copy file to our managed directory
                sourceFile.copyTo(localFile, overwrite = false)


                // Add to photo storage immediately - this provides instant UI update
                photoStorage.addWinePhoto(wineId, localFile.absolutePath)

                // Initialize upload status as pending
                uploadStatusStorage.updateUploadStatus(localFile.absolutePath, UploadStatus.PENDING)

                // Save to database for persistence and sync
                val photoEntity = WinePhotoEntity(
                    id = UUID.randomUUID().toString(),
                    wineId = wineId,
                    localPath = localFile.absolutePath,
                    remoteUrl = null, // Will be updated after upload
                    displayOrder = 0,
                    uploadStatus = PhotoUploadStatus.LOCAL_ONLY,
                    fileSize = localFile.length(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // SYNC TO SUPABASE DATABASE TABLE
                try {
                    val supabasePhoto = SupabaseWinePhoto(
                        id = photoEntity.id,
                        wineId = photoEntity.wineId,
                        remoteUrl = photoEntity.remoteUrl,
                        localPath = photoEntity.localPath,
                        displayOrder = photoEntity.displayOrder,
                        uploadStatus = photoEntity.uploadStatus.toString(),
                        fileSize = photoEntity.fileSize,
                        createdAt = Instant.now().toString(),
                        updatedAt = Instant.now().toString()
                    )

                    postgrest.from("wine_photos").insert(supabasePhoto)
                } catch (e: Exception) {
                    // Don't fail the entire operation - local database is still updated
                }

                // Save to local database
                winePhotoDao.insertPhoto(photoEntity)

                // Queue for background upload
                winePhotoUploadService.queueWinePhotoForUpload(localFile.absolutePath, wineId)

                Result.success(localFile.absolutePath)

            } catch (e: Exception) {
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

                // Remove from storage
                photoStorage.removeWinePhoto(wineId, photoPath)

                // Remove upload status
                uploadStatusStorage.removeUploadStatus(photoPath)

                Result.success(Unit)

            } catch (e: Exception) {
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

                val currentPhotos = photoStorage.getWinePhotosSync(wineId)

                // Remove upload statuses
                currentPhotos.forEach { photoPath ->
                    uploadStatusStorage.removeUploadStatus(photoPath)
                }

                // Clear from storage (this will also handle local file deletion)
                photoStorage.clearWinePhotos(wineId)

                Result.success(Unit)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}