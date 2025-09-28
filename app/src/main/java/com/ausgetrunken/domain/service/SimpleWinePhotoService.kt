package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import android.util.Log
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
                Log.d(TAG, "Retrieved ${urls.size} wine photos for wine $wineId: $urls")
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
            Log.d(TAG, "Syncing wine photos from Supabase for wine: $wineId")

            val supabasePhotos = postgrest.from("wine_photos")
                .select() {
                    filter {
                        eq("wine_id", wineId)
                    }
                }
                .decodeList<com.ausgetrunken.domain.service.SupabaseWinePhoto>()

            Log.d(TAG, "Found ${supabasePhotos.size} wine photos in Supabase for wine $wineId")

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
                        Log.d(TAG, "Synced wine photo: ${supabasePhoto.remoteUrl}")
                    } catch (e: Exception) {
                        // Photo might already exist, try update
                        Log.d(TAG, "Photo already exists, skipping: ${supabasePhoto.id}")
                    }
                }
            }

            Log.d(TAG, "✅ Wine photos sync completed for wine $wineId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sync wine photos from Supabase: ${e.message}", e)
        }
    }

    /**
     * Add photo - upload directly to Supabase and save URL
     */
    suspend fun addPhoto(wineId: String, imageUri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🚀 Adding wine photo for wine $wineId (remote-only)")

                // Check current photo count
                val currentCount = winePhotoDao.getPhotoCount(wineId)
                if (currentCount >= MAX_PHOTOS_PER_WINE) {
                    Log.w(TAG, "Maximum number of photos ($MAX_PHOTOS_PER_WINE) reached for wine $wineId")
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
                    Log.e(TAG, "Failed to create temporary file from URI")
                    return@withContext Result.failure(Exception("Failed to process image"))
                }

                Log.d(TAG, "Temporary file created: ${tempFile.length()} bytes")

                // Upload directly to Supabase
                val uploadResult = unifiedPhotoUploadService.uploadWinePhoto(tempFile, wineId)

                uploadResult.fold(
                    onSuccess = { remoteUrl ->
                        Log.d(TAG, "✅ Photo uploaded successfully: $remoteUrl")

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
                            Log.d(TAG, "✅ Wine photo saved to Supabase database")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to save to Supabase database: ${e.message}")
                            // Continue anyway - local database will work
                        }

                        // Save to local database
                        winePhotoDao.insertPhoto(photoEntity)
                        Log.d(TAG, "✅ Wine photo saved to local database")

                        // Clean up
                        if (tempFile.exists()) {
                            tempFile.delete()
                            Log.d(TAG, "🗑️ Temporary file cleaned up")
                        }

                        Result.success(remoteUrl)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ Upload failed: ${error.message}")

                        // Clean up
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }

                        Result.failure(Exception("Upload failed: ${error.message}"))
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to add wine photo: ${e.message}", e)
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
                Log.d(TAG, "🗑️ Removing wine photo: $photoUrl from wine: $wineId")

                // Get photo info from local database first
                val photoEntity = winePhotoDao.getPhotoByRemoteUrl(wineId, photoUrl)
                if (photoEntity == null) {
                    Log.w(TAG, "Photo not found in local database for URL: $photoUrl")
                } else {
                    Log.d(TAG, "Found photo entity with ID: ${photoEntity.id}")
                }

                // Remove from local database
                winePhotoDao.deletePhotoByRemoteUrl(wineId, photoUrl)
                Log.d(TAG, "✅ Removed photo from local database")

                // Remove from Supabase database
                try {
                    postgrest.from("wine_photos")
                        .delete {
                            filter {
                                eq("wine_id", wineId)
                                eq("remote_url", photoUrl)
                            }
                        }
                    Log.d(TAG, "✅ Removed photo from Supabase database")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to remove from Supabase database: ${e.message}")
                    // Continue anyway - local removal is more important for UI
                }

                // Remove from Supabase storage
                try {
                    if (photoUrl.startsWith("http")) {
                        unifiedPhotoUploadService.deletePhoto(photoUrl, "wine-photos")
                            .onSuccess {
                                Log.d(TAG, "✅ Removed photo from Supabase storage")
                            }
                            .onFailure { error ->
                                Log.e(TAG, "❌ Failed to remove from Supabase storage: ${error.message}")
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to remove from Supabase storage: ${e.message}")
                    // Continue anyway - database removal is sufficient
                }

                Log.d(TAG, "✅ Wine photo removal completed")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to remove wine photo: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}