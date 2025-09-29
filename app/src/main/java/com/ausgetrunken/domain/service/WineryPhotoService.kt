package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import com.ausgetrunken.data.local.dao.WineryPhotoDao
import com.ausgetrunken.data.local.entities.WineryPhotoEntity
import com.ausgetrunken.data.local.entities.PhotoUploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.readBytes
import io.ktor.http.append
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.time.Instant
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SupabaseWineryPhoto(
    val id: String,
    @SerialName("winery_id") val wineryId: String,
    @SerialName("remote_url") val remoteUrl: String,
    @SerialName("local_path") val localPath: String? = null,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("upload_status") val uploadStatus: String = "UPLOADED",
    @SerialName("file_size") val fileSize: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

class WineryPhotoService(
    private val wineryPhotoDao: WineryPhotoDao,
    private val imageUploadService: ImageUploadService,
    private val imageCompressionService: ImageCompressionService,
    private val context: Context,
    private val httpClient: HttpClient,
    private val postgrest: Postgrest
) {
    companion object {
        private const val TAG = "WineryPhotoService"
        private const val PHOTOS_DIR = "winery_images"
        private const val MAX_PHOTOS_PER_VINEYARD = 3
    }

    // Use consistent, reliable storage location that persists across app restarts
    private val photosDirectory: File by lazy {
        // ALWAYS use app's private external files directory - it's reliable and persistent
        // This directory is accessible to the app regardless of external storage permissions
        val preferredDirectory = context.getExternalFilesDir(PHOTOS_DIR)
        
        val directory = preferredDirectory ?: File(context.filesDir, PHOTOS_DIR)
        
        directory.apply {
            if (!exists()) {
                val created = mkdirs()
            }
        }
    }
    
    private fun File.isPublicDirectory(): Boolean {
        return absolutePath.contains("/storage/emulated/0/Pictures/") || 
               absolutePath.contains("/storage/emulated/0/DCIM/")
    }

    /**
     * Get photos for a winery, checking local cache first, then downloading from Supabase if needed
     */
    fun getWineryPhotos(wineryId: String): Flow<List<String>> {
        
        // Auto-fix problematic photos in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fixMisuploadedPhotos(wineryId)
            } catch (e: Exception) {
            }
        }
        
        // Debug: Check if we can query the database at all
        try {
            return wineryPhotoDao.getPhotosByWineryId(wineryId).map { photoEntities ->
                // CRITICAL DEBUG: Compare what's actually in database vs what we're querying for
                try {
                    val allPhotos = wineryPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.UPLOADED)
                    allPhotos.forEach { photo ->
                    }
                    
                    val syncResult = wineryPhotoDao.getPhotosByWineryIdSync(wineryId)
                } catch (e: Exception) {
                }
                
                photoEntities.forEach { entity ->
                }
                
                val photoUrls = mutableListOf<String>()
                
                // REMOTE-FIRST PHOTO LOADING WITH LOCAL CACHE
                for (photo in photoEntities.sortedBy { it.displayOrder }) {
                    
                    when {
                        // Priority 1: Use local file if it exists and is valid
                        photo.localPath != null -> {
                            val file = File(photo.localPath)
                            if (file.exists() && file.length() > 0) {
                                photoUrls.add(photo.localPath)
                            } else {
                                // Local cache is missing - download from Supabase if available
                                if (photo.remoteUrl != null && photo.remoteUrl.startsWith("https://")) {
                                    val cleanRemoteUrl = photo.remoteUrl.replace(Regex("\\s+"), "").trim()
                                    photoUrls.add(cleanRemoteUrl)
                                    // Start background download to restore local cache
                                    CoroutineScope(Dispatchers.IO).launch {
                                        downloadPhotoInBackground(photo.id, cleanRemoteUrl)
                                    }
                                } else {
                                }
                            }
                        }
                        
                        // Priority 2: Use remote URL if no local path
                        photo.remoteUrl != null && photo.remoteUrl.startsWith("https://") -> {
                            // Clean the remote URL before adding
                            val cleanRemoteUrl = photo.remoteUrl.replace(Regex("\\s+"), "").trim()
                            photoUrls.add(cleanRemoteUrl)
                            // Start background download to create local cache
                            CoroutineScope(Dispatchers.IO).launch {
                                downloadPhotoInBackground(photo.id, cleanRemoteUrl)
                            }
                        }
                        
                        else -> {
                        }
                    }
                }
                
                // No more hardcoded URLs - using real sync now!
                
                photoUrls
            }
        } catch (e: Exception) {
            // Return empty flow if database query fails
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    /**
     * Fix photos that were marked as UPLOADED but have local paths as remoteUrl
     * This can happen due to previous upload logic that silently fell back to local paths
     */
    suspend fun fixMisuploadedPhotos(wineryId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            
            val photos = wineryPhotoDao.getPhotosByWineryIdSync(wineryId)
            val problematicPhotos = photos.filter { photo ->
                photo.uploadStatus == PhotoUploadStatus.UPLOADED && 
                photo.remoteUrl != null && 
                !photo.remoteUrl.startsWith("https://") // Local path instead of URL
            }
            
            
            var fixedCount = 0
            for (photo in problematicPhotos) {
                
                val localFile = File(photo.localPath ?: continue)
                if (!localFile.exists()) {
                    continue
                }
                
                try {
                    // Re-upload to Supabase with proper URL
                    val uploadResult = imageUploadService.uploadWineryImage(wineryId, localFile)
                    
                    uploadResult.fold(
                        onSuccess = { remoteUrl ->
                            
                            // Update database with correct remote URL
                            val updatedPhoto = photo.copy(
                                remoteUrl = remoteUrl,
                                updatedAt = System.currentTimeMillis()
                            )
                            wineryPhotoDao.updatePhoto(updatedPhoto)
                            fixedCount++
                            
                        },
                        onFailure = { error ->
                            
                            // Mark as failed so it can be retried later
                            val failedPhoto = photo.copy(
                                uploadStatus = PhotoUploadStatus.UPLOAD_FAILED,
                                updatedAt = System.currentTimeMillis()
                            )
                            wineryPhotoDao.updatePhoto(failedPhoto)
                        }
                    )
                } catch (e: Exception) {
                }
            }
            
            Result.success(fixedCount)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a new photo to a winery
     */
    suspend fun addPhoto(wineryId: String, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            
            // Check photo limit
            val currentPhotoCount = wineryPhotoDao.getPhotoCountForWinery(wineryId)
            
            if (currentPhotoCount >= MAX_PHOTOS_PER_VINEYARD) {
                return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_VINEYARD photos allowed per winery"))
            }
            
            // REMOTE-FIRST: Upload to Supabase FIRST
            
            // Copy to temporary local storage for upload
            val localFile = copyUriToLocalStorage(imageUri, wineryId)
            if (localFile == null) {
                return@withContext Result.failure(Exception("Failed to save photo locally"))
            }
            
            // Create deterministic photo ID based on content and winery
            val photoId = generatePhotoId(wineryId, localFile)
            
            // Upload to Supabase FIRST
            val uploadResult = imageUploadService.uploadWineryImage(wineryId, localFile)
            
            val remoteUrl = uploadResult.getOrElse { error ->
                return@withContext Result.failure(Exception("Failed to upload photo: ${error.message}"))
            }
            
            // CREATE PHOTO ENTITY
            val photoEntity = WineryPhotoEntity(
                id = photoId,
                wineryId = wineryId,
                localPath = localFile.absolutePath,
                remoteUrl = remoteUrl,
                displayOrder = currentPhotoCount,
                uploadStatus = PhotoUploadStatus.UPLOADED,
                fileSize = localFile.length()
            )
            
            // SAVE TO SUPABASE winery_photos TABLE
            try {
                val supabasePhoto = SupabaseWineryPhoto(
                    id = photoEntity.id,
                    wineryId = photoEntity.wineryId,
                    remoteUrl = photoEntity.remoteUrl ?: "",
                    localPath = photoEntity.localPath,
                    displayOrder = photoEntity.displayOrder,
                    uploadStatus = photoEntity.uploadStatus.name,
                    fileSize = photoEntity.fileSize,
                    createdAt = Instant.now().toString(),
                    updatedAt = Instant.now().toString()
                )
                
                postgrest.from("winery_photos").insert(supabasePhoto)
            } catch (e: Exception) {
                // Don't fail the entire operation - local database is still updated
            }
            
            // SAVE TO LOCAL DATABASE
            val saveSuccess = savePhotoToDatabase(photoEntity)
            if (!saveSuccess) {
                return@withContext Result.failure(Exception("Photo uploaded but failed to save reference"))
            }
            
            Result.success(localFile.absolutePath)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a photo from a file path
     */
    suspend fun addPhoto(wineryId: String, filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $filePath"))
            }
            
            
            // Check photo limit
            val currentPhotoCount = wineryPhotoDao.getPhotoCountForWinery(wineryId)
            if (currentPhotoCount >= MAX_PHOTOS_PER_VINEYARD) {
                return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_VINEYARD photos allowed per winery"))
            }
            
            // REMOTE-FIRST: Upload to Supabase FIRST (file path version)
            
            // If file is already in our photos directory, use it directly
            val finalFile = if (sourceFile.parent == photosDirectory.absolutePath) {
                sourceFile
            } else {
                // Copy to our photos directory
                copyFileToLocalStorage(sourceFile, wineryId) ?: return@withContext Result.failure(Exception("Failed to copy photo locally"))
            }
            
            // Create deterministic photo ID based on content and winery
            val photoId = generatePhotoId(wineryId, finalFile)
            
            // Upload to Supabase FIRST
            val uploadResult = imageUploadService.uploadWineryImage(wineryId, finalFile)
            
            val remoteUrl = uploadResult.getOrElse { error ->
                return@withContext Result.failure(Exception("Failed to upload photo: ${error.message}"))
            }
            
            // CREATE PHOTO ENTITY
            val photoEntity = WineryPhotoEntity(
                id = photoId,
                wineryId = wineryId,
                localPath = finalFile.absolutePath,
                remoteUrl = remoteUrl,
                displayOrder = currentPhotoCount,
                uploadStatus = PhotoUploadStatus.UPLOADED,
                fileSize = finalFile.length()
            )
            
            // SAVE TO SUPABASE winery_photos TABLE
            try {
                val supabasePhoto = SupabaseWineryPhoto(
                    id = photoEntity.id,
                    wineryId = photoEntity.wineryId,
                    remoteUrl = photoEntity.remoteUrl ?: "",
                    localPath = photoEntity.localPath,
                    displayOrder = photoEntity.displayOrder,
                    uploadStatus = photoEntity.uploadStatus.name,
                    fileSize = photoEntity.fileSize,
                    createdAt = Instant.now().toString(),
                    updatedAt = Instant.now().toString()
                )
                
                postgrest.from("winery_photos").insert(supabasePhoto)
            } catch (e: Exception) {
                // Don't fail the entire operation - local database is still updated
            }
            
            // SAVE TO LOCAL DATABASE
            val saveSuccess = savePhotoToDatabase(photoEntity)
            if (!saveSuccess) {
                return@withContext Result.failure(Exception("Photo uploaded but failed to save reference"))
            }
            
            Result.success(finalFile.absolutePath)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a photo from a winery
     */
    suspend fun removePhoto(photoPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            
            // Find photo entity by local path or remote URL
            val photoEntity = wineryPhotoDao.getPhotoByLocalPath(photoPath) 
                ?: wineryPhotoDao.getPhotoByRemoteUrl(photoPath)
            
            
            if (photoEntity != null) {
                // Delete from Supabase if it has a remote URL
                photoEntity.remoteUrl?.let { remoteUrl ->
                    try {
                        val supabaseResult = imageUploadService.deleteWineryImage(remoteUrl)
                        supabaseResult.fold(
                            onSuccess = {
                            },
                            onFailure = { error ->
                                // Continue with local deletion even if Supabase deletion fails
                            }
                        )
                    } catch (e: Exception) {
                        // Continue with local deletion even if Supabase deletion fails
                    }
                }
                
                // Delete local file if it exists
                photoEntity.localPath?.let { localPath ->
                    val localFile = File(localPath)
                    if (localFile.exists()) {
                        val deleted = localFile.delete()
                    } else {
                    }
                }
                
                // Remove from local database
                wineryPhotoDao.deletePhoto(photoEntity)
                
                // Remove from Supabase database
                try {
                    
                    val deleteResult = postgrest.from("winery_photos").delete {
                        filter {
                            eq("id", photoEntity.id)
                        }
                    }
                    
                } catch (e: Exception) {
                    // Continue - local deletion was successful, Supabase deletion failed but won't affect app functionality
                }
                
                Result.success(Unit)
            } else {
                
                // Fallback: try to delete by path directly
                val file = File(photoPath)
                if (file.exists()) {
                    file.delete()
                }
                
                // Try to delete from database by local path
                wineryPhotoDao.deletePhotoByLocalPath(photoPath)
                
                Result.success(Unit)
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync photos from Supabase storage to local database
     * This discovers photos that exist in Supabase but not in local database
     */
    suspend fun syncPhotosFromSupabase(wineryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            
            // Get the winery from Supabase to check its photos array
            try {
                // Query the winery_photos table directly instead of winerys.photos array
                val photosResponse = postgrest.from("winery_photos")
                    .select {
                        filter {
                            eq("winery_id", wineryId)
                        }
                    }.decodeList<SupabaseWineryPhoto>()
                
                
                val localPhotos = wineryPhotoDao.getPhotosByWineryIdSync(wineryId)
                val localPhotoIds = localPhotos.map { it.id }.toSet()
                
                
                var syncedCount = 0
                photosResponse.forEach { supabasePhoto ->
                    if (supabasePhoto.id !in localPhotoIds) {
                        
                        // Convert Supabase photo to Room entity
                        val roomPhoto = WineryPhotoEntity(
                            id = supabasePhoto.id,
                            wineryId = supabasePhoto.wineryId,
                            localPath = supabasePhoto.localPath,
                            remoteUrl = supabasePhoto.remoteUrl,
                            displayOrder = supabasePhoto.displayOrder,
                            uploadStatus = PhotoUploadStatus.valueOf(supabasePhoto.uploadStatus),
                            fileSize = supabasePhoto.fileSize,
                            createdAt = System.currentTimeMillis(), // Use current time for Room
                            updatedAt = System.currentTimeMillis()
                        )
                        
                        // Insert the converted photo into local Room database
                        wineryPhotoDao.insertPhoto(roomPhoto)
                        syncedCount++
                    } else {
                    }
                }
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Force refresh photos from Supabase, replacing local cache
     * This is used for pull-to-refresh functionality
     */
    suspend fun refreshPhotosFromSupabase(wineryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            
            val localPhotos = wineryPhotoDao.getPhotosByWineryIdSync(wineryId)
            
            var refreshCount = 0
            for (photo in localPhotos) {
                if (photo.remoteUrl != null && photo.remoteUrl.startsWith("https://")) {
                    
                    // Download to new file (force refresh)
                    val cachedFile = getCachedFileFromUrl(photo.remoteUrl)
                    if (downloadFile(photo.remoteUrl, cachedFile)) {
                        // Update local path to point to refreshed file
                        val updatedPhoto = photo.copy(
                            localPath = cachedFile.absolutePath,
                            updatedAt = System.currentTimeMillis()
                        )
                        wineryPhotoDao.updatePhoto(updatedPhoto)
                        refreshCount++
                    } else {
                    }
                } else {
                }
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync photos with Supabase - download missing photos and upload pending ones
     */
    suspend fun syncPhotosWithSupabase(wineryId: String, remotePhotos: List<String>) = withContext(Dispatchers.IO) {
        try {
            
            val localPhotos = wineryPhotoDao.getPhotosByWineryIdSync(wineryId)
            val localRemoteUrls = localPhotos.mapNotNull { it.remoteUrl }.toSet()
            
            // Download new remote photos that we don't have locally
            remotePhotos.forEachIndexed { index, remoteUrl ->
                if (remoteUrl !in localRemoteUrls) {
                    downloadAndSavePhoto(wineryId, remoteUrl, index)
                }
            }
            
            // Upload local photos that haven't been uploaded yet
            val photosToUpload = localPhotos.filter { 
                it.uploadStatus == PhotoUploadStatus.LOCAL_ONLY || it.uploadStatus == PhotoUploadStatus.UPLOAD_FAILED
            }
            
            photosToUpload.forEach { photo ->
                if (photo.localPath != null) {
                    val file = File(photo.localPath)
                    if (file.exists()) {
                        uploadPhotoInBackground(photo.id, file)
                    }
                }
            }
            
        } catch (e: Exception) {
        }
    }

    private suspend fun copyUriToLocalStorage(uri: Uri, wineryId: String): File? {
        return try {
            
            // Create deterministic filename based on URI content hash and timestamp
            val contentHash = generateContentHash(uri)
            val fileName = "winery_${wineryId}_${contentHash}.jpg"
            val destFile = File(photosDirectory, fileName)
            
            
            // Compress image from URI directly to destination file
            val compressionResult = imageCompressionService.compressImage(uri, destFile)
            
            compressionResult.fold(
                onSuccess = { finalSize ->
                    destFile
                },
                onFailure = { error ->
                    // Fallback to original copy method for compatibility
                    
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(destFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    if (destFile.exists() && destFile.length() > 0) {
                        destFile
                    } else {
                        null
                    }
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun copyFileToLocalStorage(sourceFile: File, wineryId: String): File? {
        return try {
            
            // Create deterministic filename based on file content hash
            val contentHash = generateFileHash(sourceFile)
            val fileName = "winery_${wineryId}_${contentHash}.jpg"
            val destFile = File(photosDirectory, fileName)
            
            
            // First copy file to destination
            sourceFile.copyTo(destFile, overwrite = true)
            
            if (destFile.exists() && destFile.length() > 0) {
                // Then compress the copied file in-place
                val compressionResult = imageCompressionService.compressImageFile(destFile)
                
                compressionResult.fold(
                    onSuccess = { finalSize ->
                        destFile
                    },
                    onFailure = { error ->
                        // File was already copied, so we can still use it
                        destFile
                    }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getCachedFileFromUrl(url: String): File {
        // Create a more reliable filename using URL-based hash and timestamp
        val urlHash = url.hashCode().toString().replace("-", "")
        val fileName = "cached_${urlHash}.jpg"
        
        val cachedFile = File(photosDirectory, fileName)
        
        return cachedFile
    }

    private suspend fun downloadPhotoInBackground(photoId: String, remoteUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                val cachedFile = getCachedFileFromUrl(remoteUrl)
                
                if (downloadFile(remoteUrl, cachedFile)) {
                    // Update database with local path
                    updatePhotoLocalPath(photoId, cachedFile.absolutePath)
                } else {
                }
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun downloadAndSavePhoto(wineryId: String, remoteUrl: String, displayOrder: Int) {
        try {
            val cachedFile = getCachedFileFromUrl(remoteUrl)
            
            if (downloadFile(remoteUrl, cachedFile)) {
                // Create deterministic photo ID for downloaded photo
                val photoId = generatePhotoId(wineryId, cachedFile)
                val photoEntity = WineryPhotoEntity(
                    id = photoId,
                    wineryId = wineryId,
                    localPath = cachedFile.absolutePath,
                    remoteUrl = remoteUrl,
                    displayOrder = displayOrder,
                    uploadStatus = PhotoUploadStatus.SYNCED,
                    fileSize = cachedFile.length()
                )
                
                wineryPhotoDao.insertPhoto(photoEntity)
            }
        } catch (e: Exception) {
        }
    }

    private suspend fun downloadFile(url: String, destFile: File): Boolean {
        return try {
            // Clean the URL - remove any whitespace, line breaks, or carriage returns
            val cleanUrl = url.replace(Regex("\\s+"), "").trim()
            
            val response = httpClient.get(cleanUrl) {
                // Add Supabase headers for authentication
                headers {
                    append("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhqbGJ5cHpoaXhlcXZrc3huaWxrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI2OTQ4MjEsImV4cCI6MjA2ODI3MDgyMX0.PrcrF1pA4KB30VlOJm2MYkOLlgf3e3SPn2Uo_eiDKfc")
                    append("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhqbGJ5cHpoaXhlcXZrc3huaWxrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI2OTQ4MjEsImV4cCI6MjA2ODI3MDgyMX0.PrcrF1pA4KB30VlOJm2MYkOLlgf3e3SPn2Uo_eiDKfc")
                }
            }
            
            if (response.status.value in 200..299) {
                val bytes = response.readBytes()
                FileOutputStream(destFile).use { outputStream ->
                    outputStream.write(bytes)
                }
                destFile.exists() && destFile.length() > 0
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun uploadPhotoInBackground(photoId: String, file: File) {
        withContext(Dispatchers.IO) {
            try {
                
                // Update status to uploading
                wineryPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOADING, null)
                
                val result = imageUploadService.uploadWineryImage("", file) // wineryId not needed in current implementation
                
                result.fold(
                    onSuccess = { remoteUrl ->
                        wineryPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOADED, remoteUrl)
                    },
                    onFailure = { error ->
                        wineryPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOAD_FAILED, null)
                    }
                )
            } catch (e: Exception) {
                wineryPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOAD_FAILED, null)
            }
        }
    }

    private suspend fun updatePhotoLocalPath(photoId: String, localPath: String) {
        try {
            val photo = wineryPhotoDao.getPhotoById(photoId)
            photo?.let {
                val updatedPhoto = it.copy(localPath = localPath, updatedAt = System.currentTimeMillis())
                wineryPhotoDao.updatePhoto(updatedPhoto)
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Find a valid photo path, trying migration strategies if the original path is broken
     */
    private suspend fun findValidPhotoPath(photo: WineryPhotoEntity): String? {
        
        // First, check if the current local path is valid
        if (photo.localPath != null) {
            val file = File(photo.localPath)
            
            if (file.exists() && file.length() > 0) {
                return photo.localPath
            } else {
            }
        } else {
        }
        
        // Try to find the file in different possible locations
        val possiblePaths = generatePossiblePaths(photo)
        
        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                return path
            }
        }
        
        return null
    }
    
    /**
     * Generate possible file paths where the photo might exist
     */
    private fun generatePossiblePaths(photo: WineryPhotoEntity): List<String> {
        val possiblePaths = mutableListOf<String>()
        
        // Extract filename from original path if available
        val originalFileName = photo.localPath?.let { File(it).name }
        
        if (originalFileName != null) {
            // Try in current photos directory FIRST
            possiblePaths.add(File(photosDirectory, originalFileName).absolutePath)
            
            // Try in alternative storage locations (for migration from old versions)
            val alternativeDirectories = listOf(
                // OLD: Public Pictures directory (may no longer be accessible)
                File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Ausgetrunken"),
                // OLD: Other possible locations
                File(context.filesDir, PHOTOS_DIR),
                context.getExternalFilesDir(PHOTOS_DIR),
                File(context.cacheDir, PHOTOS_DIR)
            )
            
            alternativeDirectories.filterNotNull().forEach { dir ->
                possiblePaths.add(File(dir, originalFileName).absolutePath)
            }
        }
        
        // Also try generating filename patterns that might have been used
        val possibleFileNames = listOf(
            "winery_${photo.wineryId}_${photo.id.take(8)}.jpg",
            "winery_${photo.wineryId}_${photo.id}.jpg"
        )
        
        possibleFileNames.forEach { fileName ->
            // Check in current directory first
            possiblePaths.add(File(photosDirectory, fileName).absolutePath)
            // Then check old public directory
            possiblePaths.add(File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Ausgetrunken/$fileName").absolutePath)
        }
        
        return possiblePaths.distinct()
    }
    
    /**
     * Validate and migrate photo storage on app startup
     */
    suspend fun validateAndMigratePhotos() {
        withContext(Dispatchers.IO) {
            try {
                
                // Force directory initialization and log the path
                val directory = photosDirectory
                
                // List actual files in the directory
                if (directory.exists()) {
                    val files = directory.listFiles()
                    files?.forEach { file ->
                    }
                } else {
                }
                
                // Check ALL photos in database regardless of status
                val allLocalOnlyPhotos = wineryPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.LOCAL_ONLY)
                val allUploadedPhotos = wineryPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.UPLOADED)
                val allUploadingPhotos = wineryPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.UPLOADING)
                val allSyncedPhotos = wineryPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.SYNCED)
                
                val totalPhotos = allLocalOnlyPhotos.size + allUploadedPhotos.size + allUploadingPhotos.size + allSyncedPhotos.size
                
                // Check all photos regardless of status
                val allPhotos = allLocalOnlyPhotos + allUploadedPhotos + allUploadingPhotos + allSyncedPhotos
                allPhotos.forEach { photo ->
                    if (photo.localPath != null) {
                        val file = File(photo.localPath)
                    }
                }
                
                
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Generate deterministic content hash from URI
     */
    private suspend fun generateContentHash(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()
                
                // Use a combination of content hash and size for uniqueness
                val contentHash = bytes.contentHashCode().toString()
                val sizeHash = bytes.size.toString()
                "${contentHash}_${sizeHash}".replace("-", "")
            } catch (e: Exception) {
                System.currentTimeMillis().toString()
            }
        }
    }
    
    /**
     * Generate deterministic file hash
     */
    private fun generateFileHash(file: File): String {
        return try {
            val bytes = file.readBytes()
            val contentHash = bytes.contentHashCode().toString()
            val sizeHash = bytes.size.toString()
            "${contentHash}_${sizeHash}".replace("-", "")
        } catch (e: Exception) {
            file.name.hashCode().toString().replace("-", "")
        }
    }
    
    /**
     * Generate deterministic photo ID based on winery and file content
     */
    private fun generatePhotoId(wineryId: String, file: File): String {
        return try {
            val fileHash = generateFileHash(file)
            "photo_${wineryId}_${fileHash}".take(50) // Limit length for database
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    /**
     * Migrate a photo file from old location to current photos directory
     */
    private suspend fun migratePhotoFile(oldPath: String, photo: WineryPhotoEntity): String? {
        return withContext(Dispatchers.IO) {
            try {
                val oldFile = File(oldPath)
                if (!oldFile.exists()) {
                    return@withContext null
                }
                
                // Generate filename for new location
                val originalFileName = oldFile.name
                val newFile = File(photosDirectory, originalFileName)
                
                // Copy file to new location
                oldFile.copyTo(newFile, overwrite = true)
                
                if (newFile.exists() && newFile.length() > 0) {
                    
                    // Optionally delete old file (but be cautious)
                    try {
                        if (oldPath.contains("/storage/emulated/0/Pictures/")) {
                            // Don't delete from public Pictures directory - user might want to keep it
                        } else {
                            oldFile.delete()
                        }
                    } catch (e: Exception) {
                    }
                    
                    newFile.absolutePath
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Save photo to database (using TRUNCATE mode for immediate consistency)
     */
    private suspend fun savePhotoToDatabase(photoEntity: WineryPhotoEntity): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                
                // Insert photo via Room DAO
                wineryPhotoDao.insertPhoto(photoEntity)
                
                // With TRUNCATE mode, data should be immediately visible
                // Immediate verification
                val savedPhoto = wineryPhotoDao.getPhotoById(photoEntity.id)
                val success = savedPhoto != null
                
                if (success) {
                    
                    // Additional verification - count all photos for this winery
                    val allPhotos = wineryPhotoDao.getPhotosByWineryIdSync(photoEntity.wineryId)
                    allPhotos.forEach { photo ->
                    }
                    
                    // Also verify we can count photos properly
                    val photoCount = wineryPhotoDao.getPhotoCountForWinery(photoEntity.wineryId)
                    
                    // CRITICAL: Force a small delay to ensure database consistency
                    kotlinx.coroutines.delay(100)
                    
                } else {
                }
                
                success
            } catch (e: Exception) {
                false
            }
        }
    }
}