package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import com.ausgetrunken.data.local.dao.WinePhotoDao
import com.ausgetrunken.data.local.entities.WinePhotoEntity
import com.ausgetrunken.data.local.entities.PhotoUploadStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID


/**
 * Simplified wine photo service - remote only, no complex sync
 */
class SimpleWinePhotoService(
    private val context: Context,
    private val unifiedPhotoUploadService: UnifiedPhotoUploadService,
    private val winePhotoDao: WinePhotoDao,
    private val postgrest: Postgrest
) {
    companion object {
        private const val TAG = "SimpleWinePhotoService"
        private const val MAX_PHOTOS_PER_WINE = 3
    }

    /**
     * Get wine photos - returns remote URLs only, with remote-first loading
     */
    fun getWinePhotos(wineId: String): Flow<List<String>> {
        return winePhotoDao.getWinePhotos(wineId).map { entities ->
            entities.mapNotNull { entity ->
                entity.remoteUrl?.takeIf { it.isNotBlank() }
            }.also { urls ->
            }
        }.onStart {
            // Check if we need to sync from Supabase
            CoroutineScope(Dispatchers.IO).launch {
                syncWinePhotosFromSupabase(wineId)
            }
        }
    }

    /**
     * Sync wine photos from Supabase to local database
     */
    private suspend fun syncWinePhotosFromSupabase(wineId: String) {
        try {

            val supabasePhotos = postgrest.from("wine_photos")
                .select() {
                    filter {
                        eq("wine_id", wineId)
                    }
                }
                .decodeList<com.ausgetrunken.domain.service.SupabaseWinePhoto>()


            // Convert and save to local database
            supabasePhotos.forEach { supabasePhoto ->
                if (supabasePhoto.remoteUrl?.isNotBlank() == true) {
                    val localEntity = WinePhotoEntity(
                        id = supabasePhoto.id,
                        wineId = supabasePhoto.wineId,
                        localPath = null,
                        remoteUrl = supabasePhoto.remoteUrl,
                        displayOrder = supabasePhoto.displayOrder,
                        uploadStatus = PhotoUploadStatus.UPLOADED,
                        fileSize = supabasePhoto.fileSize,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    try {
                        winePhotoDao.insertPhoto(localEntity)
                    } catch (e: Exception) {
                        // Photo might already exist, try update
                    }
                }
            }

        } catch (e: Exception) {
        }
    }

    /**
     * Add photo - upload directly to Supabase and save URL
     */
    suspend fun addPhoto(wineId: String, imageUri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {

                // Check current photo count
                val currentCount = winePhotoDao.getPhotoCount(wineId)
                if (currentCount >= MAX_PHOTOS_PER_WINE) {
                    return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINE photos allowed per wine"))
                }

                // Create temporary file for upload
                val tempFile = File.createTempFile("wine_upload_", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    return@withContext Result.failure(Exception("Failed to process image"))
                }


                // Upload directly to Supabase
                val uploadResult = unifiedPhotoUploadService.uploadWinePhoto(tempFile, wineId)

                uploadResult.fold(
                    onSuccess = { remoteUrl ->

                        // Save to database with remote URL
                        val photoId = UUID.randomUUID().toString()
                        val photoEntity = WinePhotoEntity(
                            id = photoId,
                            wineId = wineId,
                            localPath = null, // Remote only
                            remoteUrl = remoteUrl,
                            displayOrder = 0,
                            uploadStatus = PhotoUploadStatus.UPLOADED,
                            fileSize = tempFile.length(),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )

                        // Save to Supabase database
                        try {
                            val supabasePhoto = com.ausgetrunken.domain.service.SupabaseWinePhoto(
                                id = photoId,
                                wineId = wineId,
                                remoteUrl = remoteUrl,
                                localPath = null,
                                displayOrder = 0,
                                uploadStatus = "UPLOADED",
                                fileSize = tempFile.length(),
                                createdAt = Instant.now().toString(),
                                updatedAt = Instant.now().toString()
                            )

                            postgrest.from("wine_photos").insert(supabasePhoto)
                        } catch (e: Exception) {
                            // Continue anyway - local database will work
                        }

                        // Save to local database
                        winePhotoDao.insertPhoto(photoEntity)

                        // Clean up
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }

                        Result.success(remoteUrl)
                    },
                    onFailure = { error ->

                        // Clean up
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }

                        Result.failure(Exception("Upload failed: ${error.message}"))
                    }
                )

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Remove photo
     */
    suspend fun removePhoto(wineId: String, photoUrl: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {

                // Get photo info from local database first
                val photoEntity = winePhotoDao.getPhotoByRemoteUrl(wineId, photoUrl)
                if (photoEntity == null) {
                } else {
                }

                // Remove from local database
                winePhotoDao.deletePhotoByRemoteUrl(wineId, photoUrl)

                // Remove from Supabase database
                try {
                    postgrest.from("wine_photos")
                        .delete {
                            filter {
                                eq("wine_id", wineId)
                                eq("remote_url", photoUrl)
                            }
                        }
                } catch (e: Exception) {
                    // Continue anyway - local removal is more important for UI
                }

                // Remove from Supabase storage
                try {
                    if (photoUrl.startsWith("http")) {
                        unifiedPhotoUploadService.deletePhoto(photoUrl, "wine-photos")
                            .onSuccess {
                            }
                            .onFailure { error ->
                            }
                    }
                } catch (e: Exception) {
                    // Continue anyway - database removal is sufficient
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}