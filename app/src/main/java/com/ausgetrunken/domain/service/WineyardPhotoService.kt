package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ausgetrunken.data.local.dao.WineyardPhotoDao
import com.ausgetrunken.data.local.entities.WineyardPhotoEntity
import com.ausgetrunken.data.local.entities.PhotoUploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class WineyardPhotoService(
    private val wineyardPhotoDao: WineyardPhotoDao,
    private val imageUploadService: ImageUploadService,
    private val context: Context,
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "WineyardPhotoService"
        private const val PHOTOS_DIR = "wineyard_images"
        private const val MAX_PHOTOS_PER_WINEYARD = 3
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
                Log.d(TAG, "Created photos directory: $absolutePath (success: $created)")
            }
            Log.d(TAG, "Using photos directory: $absolutePath (persistent: true)")
        }
    }
    
    private fun File.isPublicDirectory(): Boolean {
        return absolutePath.contains("/storage/emulated/0/Pictures/") || 
               absolutePath.contains("/storage/emulated/0/DCIM/")
    }

    /**
     * Get photos for a wineyard, checking local cache first, then downloading from Supabase if needed
     */
    fun getWineyardPhotos(wineyardId: String): Flow<List<String>> {
        Log.d(TAG, "=== GET WINEYARD PHOTOS START ===")
        Log.d(TAG, "QUERY: Loading photos for wineyardId: $wineyardId")
        Log.d(TAG, "STORAGE: Current photos directory: ${photosDirectory.absolutePath}")
        
        // Debug: Check if we can query the database at all
        try {
            return wineyardPhotoDao.getPhotosByWineyardId(wineyardId).map { photoEntities ->
                // CRITICAL DEBUG: Compare what's actually in database vs what we're querying for
                try {
                    val allPhotos = wineyardPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.UPLOADED)
                    Log.d(TAG, "DEBUG: Total UPLOADED photos in database: ${allPhotos.size}")
                    allPhotos.forEach { photo ->
                        Log.d(TAG, "DEBUG: UPLOADED photo - id=${photo.id}, wineyardId='${photo.wineyardId}', query_wineyardId='$wineyardId', match=${photo.wineyardId == wineyardId}")
                    }
                    
                    val syncResult = wineyardPhotoDao.getPhotosByWineyardIdSync(wineyardId)
                    Log.d(TAG, "DEBUG: Sync query returned ${syncResult.size} photos for wineyardId: $wineyardId")
                } catch (e: Exception) {
                    Log.e(TAG, "DEBUG: Error in comparison queries", e)
                }
                
                Log.d(TAG, "DATABASE: Retrieved ${photoEntities.size} photo entities for wineyard $wineyardId")
                photoEntities.forEach { entity ->
                    Log.d(TAG, "DATABASE: Photo entity - id=${entity.id}, localPath=${entity.localPath}, remoteUrl=${entity.remoteUrl}, status=${entity.uploadStatus}")
                }
                
                val photoUrls = mutableListOf<String>()
                
                // SIMPLIFIED PHOTO LOADING - Just use the database paths without complex validation
                for (photo in photoEntities.sortedBy { it.displayOrder }) {
                    Log.d(TAG, "Processing photo: id=${photo.id}, localPath=${photo.localPath}, remoteUrl=${photo.remoteUrl}")
                    
                    when {
                        // Priority 1: Use local path if available
                        photo.localPath != null -> {
                            val file = File(photo.localPath)
                            if (file.exists() && file.length() > 0) {
                                Log.d(TAG, "✅ Using local photo: ${photo.localPath}")
                                photoUrls.add(photo.localPath)
                            } else {
                                Log.w(TAG, "⚠️ Local file missing: ${photo.localPath}")
                                // Don't remove from database - file might be temporarily unavailable
                                // Instead, try remote URL as fallback
                                if (photo.remoteUrl != null) {
                                    Log.d(TAG, "Using remote URL as fallback: ${photo.remoteUrl}")
                                    photoUrls.add(photo.remoteUrl)
                                }
                            }
                        }
                        
                        // Priority 2: Use remote URL if no local path
                        photo.remoteUrl != null -> {
                            Log.d(TAG, "Using remote photo: ${photo.remoteUrl}")
                            photoUrls.add(photo.remoteUrl)
                        }
                        
                        else -> {
                            Log.w(TAG, "Photo ${photo.id} has no valid local or remote path")
                        }
                    }
                }
                
                Log.d(TAG, "FINAL RESULT: Returning ${photoUrls.size} photo URLs: $photoUrls")
                photoUrls
            }
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in getWineyardPhotos: Database query failed", e)
            // Return empty flow if database query fails
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    /**
     * Add a new photo to a wineyard
     */
    suspend fun addPhoto(wineyardId: String, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== STARTING PHOTO ADD PROCESS ===")
            Log.d(TAG, "Adding photo to wineyard $wineyardId from URI: $imageUri")
            Log.d(TAG, "Photos directory: ${photosDirectory.absolutePath}")
            Log.d(TAG, "Photos directory exists: ${photosDirectory.exists()}")
            
            // Check photo limit
            val currentPhotoCount = wineyardPhotoDao.getPhotoCountForWineyard(wineyardId)
            Log.d(TAG, "Current photo count for wineyard: $currentPhotoCount")
            
            if (currentPhotoCount >= MAX_PHOTOS_PER_WINEYARD) {
                Log.w(TAG, "Photo limit reached: $currentPhotoCount >= $MAX_PHOTOS_PER_WINEYARD")
                return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINEYARD photos allowed per wineyard"))
            }
            
            // Copy to local storage first
            Log.d(TAG, "Copying URI to local storage...")
            val localFile = copyUriToLocalStorage(imageUri, wineyardId)
            if (localFile == null) {
                Log.e(TAG, "Failed to copy URI to local storage")
                return@withContext Result.failure(Exception("Failed to save photo locally"))
            }
            Log.d(TAG, "Successfully copied to local file: ${localFile.absolutePath}, size: ${localFile.length()}")
            
            // Create deterministic photo ID based on content and wineyard
            val photoId = generatePhotoId(wineyardId, localFile)
            val photoEntity = WineyardPhotoEntity(
                id = photoId,
                wineyardId = wineyardId,
                localPath = localFile.absolutePath,
                displayOrder = currentPhotoCount,
                uploadStatus = PhotoUploadStatus.LOCAL_ONLY,
                fileSize = localFile.length()
            )
            Log.d(TAG, "Created photo entity: $photoEntity")
            
            // Save to database
            Log.d(TAG, "Attempting to save photo to database...")
            val saveSuccess = savePhotoToDatabase(photoEntity)
            if (!saveSuccess) {
                Log.e(TAG, "DATABASE SAVE FAILED - returning failure")
                return@withContext Result.failure(Exception("Failed to save photo to database"))
            }
            Log.d(TAG, "DATABASE SAVE SUCCESS - Photo entity saved with id=$photoId")
            
            // Trigger background upload to Supabase
            uploadPhotoInBackground(photoId, localFile)
            
            Log.d(TAG, "=== PHOTO ADD PROCESS COMPLETED SUCCESSFULLY ===")
            Result.success(localFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "=== PHOTO ADD PROCESS FAILED ===", e)
            Result.failure(e)
        }
    }

    /**
     * Add a photo from a file path
     */
    suspend fun addPhoto(wineyardId: String, filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $filePath"))
            }
            
            Log.d(TAG, "Adding photo to wineyard $wineyardId from file: $filePath")
            
            // Check photo limit
            val currentPhotoCount = wineyardPhotoDao.getPhotoCountForWineyard(wineyardId)
            if (currentPhotoCount >= MAX_PHOTOS_PER_WINEYARD) {
                return@withContext Result.failure(Exception("Maximum of $MAX_PHOTOS_PER_WINEYARD photos allowed per wineyard"))
            }
            
            // If file is already in our photos directory, use it directly
            val finalFile = if (sourceFile.parent == photosDirectory.absolutePath) {
                sourceFile
            } else {
                // Copy to our photos directory
                copyFileToLocalStorage(sourceFile, wineyardId) ?: return@withContext Result.failure(Exception("Failed to copy photo locally"))
            }
            
            // Create deterministic photo ID based on content and wineyard
            val photoId = generatePhotoId(wineyardId, finalFile)
            val photoEntity = WineyardPhotoEntity(
                id = photoId,
                wineyardId = wineyardId,
                localPath = finalFile.absolutePath,
                displayOrder = currentPhotoCount,
                uploadStatus = PhotoUploadStatus.LOCAL_ONLY,
                fileSize = finalFile.length()
            )
            
            // Save to database
            val saveSuccess = savePhotoToDatabase(photoEntity)
            if (!saveSuccess) {
                return@withContext Result.failure(Exception("Failed to save photo to database"))
            }
            Log.d(TAG, "PHOTO SAVED TO DATABASE (FILE): Photo entity saved with id=$photoId, wineyardId=$wineyardId, localPath=${finalFile.absolutePath}")
            
            // Trigger background upload to Supabase
            uploadPhotoInBackground(photoId, finalFile)
            
            Result.success(finalFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add photo from file", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a photo from a wineyard
     */
    suspend fun removePhoto(photoPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Removing photo: $photoPath")
            
            // Find photo entity by local path or remote URL
            val photoEntity = wineyardPhotoDao.getPhotoByLocalPath(photoPath) 
                ?: wineyardPhotoDao.getPhotoByRemoteUrl(photoPath)
            
            if (photoEntity != null) {
                // Delete from Supabase if it has a remote URL
                photoEntity.remoteUrl?.let { remoteUrl ->
                    try {
                        imageUploadService.deleteWineyardImage(remoteUrl)
                        Log.d(TAG, "Deleted photo from Supabase: $remoteUrl")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete photo from Supabase: $remoteUrl", e)
                    }
                }
                
                // Delete local file if it exists
                photoEntity.localPath?.let { localPath ->
                    val localFile = File(localPath)
                    if (localFile.exists()) {
                        localFile.delete()
                        Log.d(TAG, "Deleted local photo file: $localPath")
                    }
                }
                
                // Remove from database
                wineyardPhotoDao.deletePhoto(photoEntity)
                Log.d(TAG, "Removed photo entity from database: ${photoEntity.id}")
                
                Result.success(Unit)
            } else {
                // Fallback: try to delete by path directly
                val file = File(photoPath)
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Deleted orphaned photo file: $photoPath")
                }
                
                // Try to delete from database by local path
                wineyardPhotoDao.deletePhotoByLocalPath(photoPath)
                
                Result.success(Unit)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove photo", e)
            Result.failure(e)
        }
    }

    /**
     * Sync photos with Supabase - download missing photos and upload pending ones
     */
    suspend fun syncPhotosWithSupabase(wineyardId: String, remotePhotos: List<String>) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing photos for wineyard $wineyardId with ${remotePhotos.size} remote photos")
            
            val localPhotos = wineyardPhotoDao.getPhotosByWineyardIdSync(wineyardId)
            val localRemoteUrls = localPhotos.mapNotNull { it.remoteUrl }.toSet()
            
            // Download new remote photos that we don't have locally
            remotePhotos.forEachIndexed { index, remoteUrl ->
                if (remoteUrl !in localRemoteUrls) {
                    Log.d(TAG, "Downloading new remote photo: $remoteUrl")
                    downloadAndSavePhoto(wineyardId, remoteUrl, index)
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
            Log.e(TAG, "Failed to sync photos with Supabase", e)
        }
    }

    private suspend fun copyUriToLocalStorage(uri: Uri, wineyardId: String): File? {
        return try {
            // Create deterministic filename based on URI content hash and timestamp
            val contentHash = generateContentHash(uri)
            val fileName = "wineyard_${wineyardId}_${contentHash}.jpg"
            val destFile = File(photosDirectory, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (destFile.exists() && destFile.length() > 0) {
                Log.d(TAG, "Successfully copied URI to local storage: ${destFile.absolutePath}")
                destFile
            } else {
                Log.e(TAG, "Failed to copy URI - file is empty or doesn't exist")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to local storage", e)
            null
        }
    }

    private suspend fun copyFileToLocalStorage(sourceFile: File, wineyardId: String): File? {
        return try {
            // Create deterministic filename based on file content hash
            val contentHash = generateFileHash(sourceFile)
            val fileName = "wineyard_${wineyardId}_${contentHash}.jpg"
            val destFile = File(photosDirectory, fileName)
            
            sourceFile.copyTo(destFile, overwrite = true)
            
            if (destFile.exists() && destFile.length() > 0) {
                Log.d(TAG, "Successfully copied file to local storage: ${destFile.absolutePath}")
                destFile
            } else {
                Log.e(TAG, "Failed to copy file - destination is empty or doesn't exist")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to local storage", e)
            null
        }
    }

    private fun getCachedFileFromUrl(url: String): File {
        // Create a more reliable filename using URL-based hash and timestamp
        val urlHash = url.hashCode().toString().replace("-", "")
        val fileName = "cached_${urlHash}.jpg"
        
        val cachedFile = File(photosDirectory, fileName)
        Log.d(TAG, "Generated cached file path for URL $url: ${cachedFile.absolutePath}")
        
        return cachedFile
    }

    private suspend fun downloadPhotoInBackground(photoId: String, remoteUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                val cachedFile = getCachedFileFromUrl(remoteUrl)
                
                if (downloadFile(remoteUrl, cachedFile)) {
                    // Update database with local path
                    updatePhotoLocalPath(photoId, cachedFile.absolutePath)
                    Log.d(TAG, "Successfully downloaded and cached photo: $remoteUrl")
                } else {
                    Log.w(TAG, "Failed to download photo: $remoteUrl")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading photo in background", e)
            }
        }
    }

    private suspend fun downloadAndSavePhoto(wineyardId: String, remoteUrl: String, displayOrder: Int) {
        try {
            val cachedFile = getCachedFileFromUrl(remoteUrl)
            
            if (downloadFile(remoteUrl, cachedFile)) {
                // Create deterministic photo ID for downloaded photo
                val photoId = generatePhotoId(wineyardId, cachedFile)
                val photoEntity = WineyardPhotoEntity(
                    id = photoId,
                    wineyardId = wineyardId,
                    localPath = cachedFile.absolutePath,
                    remoteUrl = remoteUrl,
                    displayOrder = displayOrder,
                    uploadStatus = PhotoUploadStatus.SYNCED,
                    fileSize = cachedFile.length()
                )
                
                wineyardPhotoDao.insertPhoto(photoEntity)
                Log.d(TAG, "Successfully downloaded and saved remote photo: $remoteUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and save photo: $remoteUrl", e)
        }
    }

    private suspend fun downloadFile(url: String, destFile: File): Boolean {
        return try {
            val response = httpClient.get(url)
            
            if (response.status.value in 200..299) {
                val bytes = response.readBytes()
                FileOutputStream(destFile).use { outputStream ->
                    outputStream.write(bytes)
                }
                destFile.exists() && destFile.length() > 0
            } else {
                Log.w(TAG, "HTTP error downloading file: ${response.status}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception downloading file: $url", e)
            false
        }
    }

    private suspend fun uploadPhotoInBackground(photoId: String, file: File) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting background upload for photo $photoId")
                
                // Update status to uploading
                wineyardPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOADING, null)
                
                val result = imageUploadService.uploadWineyardImage("", file) // wineyardId not needed in current implementation
                
                result.fold(
                    onSuccess = { remoteUrl ->
                        Log.d(TAG, "Successfully uploaded photo $photoId to: $remoteUrl")
                        wineyardPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOADED, remoteUrl)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to upload photo $photoId", error)
                        wineyardPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOAD_FAILED, null)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during background upload", e)
                wineyardPhotoDao.updatePhotoUploadStatus(photoId, PhotoUploadStatus.UPLOAD_FAILED, null)
            }
        }
    }

    private suspend fun updatePhotoLocalPath(photoId: String, localPath: String) {
        try {
            val photo = wineyardPhotoDao.getPhotoById(photoId)
            photo?.let {
                val updatedPhoto = it.copy(localPath = localPath, updatedAt = System.currentTimeMillis())
                wineyardPhotoDao.updatePhoto(updatedPhoto)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update photo local path", e)
        }
    }

    /**
     * Find a valid photo path, trying migration strategies if the original path is broken
     */
    private suspend fun findValidPhotoPath(photo: WineyardPhotoEntity): String? {
        Log.d(TAG, "findValidPhotoPath: Checking photo ${photo.id}")
        
        // First, check if the current local path is valid
        if (photo.localPath != null) {
            val file = File(photo.localPath)
            Log.d(TAG, "findValidPhotoPath: Checking stored path: ${photo.localPath}")
            Log.d(TAG, "findValidPhotoPath: File exists: ${file.exists()}, size: ${if (file.exists()) file.length() else "N/A"}")
            
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Photo ${photo.id} has valid local path: ${photo.localPath}")
                return photo.localPath
            } else {
                Log.w(TAG, "Photo ${photo.id} local path is broken: ${photo.localPath}")
            }
        } else {
            Log.d(TAG, "findValidPhotoPath: Photo ${photo.id} has no stored local path")
        }
        
        // Try to find the file in different possible locations
        val possiblePaths = generatePossiblePaths(photo)
        Log.d(TAG, "findValidPhotoPath: Checking ${possiblePaths.size} possible paths: $possiblePaths")
        
        for (path in possiblePaths) {
            val file = File(path)
            Log.d(TAG, "findValidPhotoPath: Checking $path - exists: ${file.exists()}, size: ${if (file.exists()) file.length() else "N/A"}")
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Found migrated photo for ${photo.id} at: $path")
                return path
            }
        }
        
        Log.w(TAG, "Could not find valid local path for photo ${photo.id}")
        return null
    }
    
    /**
     * Generate possible file paths where the photo might exist
     */
    private fun generatePossiblePaths(photo: WineyardPhotoEntity): List<String> {
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
            "wineyard_${photo.wineyardId}_${photo.id.take(8)}.jpg",
            "wineyard_${photo.wineyardId}_${photo.id}.jpg"
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
                Log.d(TAG, "=== STARTING PHOTO VALIDATION AND MIGRATION ===")
                
                // Force directory initialization and log the path
                val directory = photosDirectory
                Log.d(TAG, "STORAGE: Photos will be stored at: ${directory.absolutePath}")
                Log.d(TAG, "STORAGE: Directory exists: ${directory.exists()}")
                Log.d(TAG, "STORAGE: Directory is writable: ${directory.canWrite()}")
                Log.d(TAG, "STORAGE: Directory is readable: ${directory.canRead()}")
                Log.d(TAG, "STORAGE: Directory parent: ${directory.parentFile?.absolutePath}")
                
                // List actual files in the directory
                if (directory.exists()) {
                    val files = directory.listFiles()
                    Log.d(TAG, "STORAGE: Files in directory: ${files?.size ?: 0}")
                    files?.forEach { file ->
                        Log.d(TAG, "STORAGE: File found: ${file.name} (size: ${file.length()}, readable: ${file.canRead()})")
                    }
                } else {
                    Log.w(TAG, "STORAGE: Directory does not exist!")
                }
                
                // Check ALL photos in database regardless of status
                val allLocalOnlyPhotos = wineyardPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.LOCAL_ONLY)
                val allUploadedPhotos = wineyardPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.UPLOADED)
                val allUploadingPhotos = wineyardPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.UPLOADING)
                val allSyncedPhotos = wineyardPhotoDao.getPhotosByUploadStatus(PhotoUploadStatus.SYNCED)
                
                val totalPhotos = allLocalOnlyPhotos.size + allUploadedPhotos.size + allUploadingPhotos.size + allSyncedPhotos.size
                Log.d(TAG, "DATABASE: Total photos in database: $totalPhotos")
                Log.d(TAG, "DATABASE: LOCAL_ONLY photos: ${allLocalOnlyPhotos.size}")
                Log.d(TAG, "DATABASE: UPLOADED photos: ${allUploadedPhotos.size}")
                Log.d(TAG, "DATABASE: UPLOADING photos: ${allUploadingPhotos.size}")
                Log.d(TAG, "DATABASE: SYNCED photos: ${allSyncedPhotos.size}")
                
                // Check all photos regardless of status
                val allPhotos = allLocalOnlyPhotos + allUploadedPhotos + allUploadingPhotos + allSyncedPhotos
                allPhotos.forEach { photo ->
                    Log.d(TAG, "DATABASE: Photo ${photo.id} - wineyardId: ${photo.wineyardId}, localPath: ${photo.localPath}, status: ${photo.uploadStatus}")
                    if (photo.localPath != null) {
                        val file = File(photo.localPath)
                        Log.d(TAG, "DATABASE: File exists: ${file.exists()}, size: ${if (file.exists()) file.length() else "N/A"}")
                    }
                }
                
                Log.d(TAG, "=== PHOTO VALIDATION COMPLETED ===")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during photo validation and migration", e)
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
                Log.w(TAG, "Failed to generate content hash, using timestamp fallback", e)
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
            Log.w(TAG, "Failed to generate file hash, using filename fallback", e)
            file.name.hashCode().toString().replace("-", "")
        }
    }
    
    /**
     * Generate deterministic photo ID based on wineyard and file content
     */
    private fun generatePhotoId(wineyardId: String, file: File): String {
        return try {
            val fileHash = generateFileHash(file)
            "photo_${wineyardId}_${fileHash}".take(50) // Limit length for database
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate photo ID, using UUID fallback", e)
            UUID.randomUUID().toString()
        }
    }

    /**
     * Migrate a photo file from old location to current photos directory
     */
    private suspend fun migratePhotoFile(oldPath: String, photo: WineyardPhotoEntity): String? {
        return withContext(Dispatchers.IO) {
            try {
                val oldFile = File(oldPath)
                if (!oldFile.exists()) {
                    Log.w(TAG, "Cannot migrate - old file doesn't exist: $oldPath")
                    return@withContext null
                }
                
                // Generate filename for new location
                val originalFileName = oldFile.name
                val newFile = File(photosDirectory, originalFileName)
                
                // Copy file to new location
                oldFile.copyTo(newFile, overwrite = true)
                
                if (newFile.exists() && newFile.length() > 0) {
                    Log.d(TAG, "Successfully copied file from $oldPath to ${newFile.absolutePath}")
                    
                    // Optionally delete old file (but be cautious)
                    try {
                        if (oldPath.contains("/storage/emulated/0/Pictures/")) {
                            // Don't delete from public Pictures directory - user might want to keep it
                            Log.d(TAG, "Keeping original file in public directory: $oldPath")
                        } else {
                            oldFile.delete()
                            Log.d(TAG, "Deleted old file: $oldPath")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not delete old file: $oldPath", e)
                    }
                    
                    newFile.absolutePath
                } else {
                    Log.e(TAG, "Migration failed - new file is empty or doesn't exist")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate photo file from $oldPath", e)
                null
            }
        }
    }
    
    /**
     * Save photo to database (using TRUNCATE mode for immediate consistency)
     */
    private suspend fun savePhotoToDatabase(photoEntity: WineyardPhotoEntity): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "SAVE: Starting photo save (TRUNCATE mode): ${photoEntity.id}")
                Log.d(TAG, "SAVE: Photo entity details: wineyardId=${photoEntity.wineyardId}, localPath=${photoEntity.localPath}")
                
                // Insert photo via Room DAO
                wineyardPhotoDao.insertPhoto(photoEntity)
                Log.d(TAG, "SAVE: Photo inserted via Room DAO")
                
                // With TRUNCATE mode, data should be immediately visible
                // Immediate verification
                val savedPhoto = wineyardPhotoDao.getPhotoById(photoEntity.id)
                val success = savedPhoto != null
                
                if (success) {
                    Log.d(TAG, "SAVE: SUCCESS - Photo verified: $savedPhoto")
                    
                    // Additional verification - count all photos for this wineyard
                    val allPhotos = wineyardPhotoDao.getPhotosByWineyardIdSync(photoEntity.wineyardId)
                    Log.d(TAG, "SAVE: Total photos for wineyard ${photoEntity.wineyardId}: ${allPhotos.size}")
                    allPhotos.forEach { photo ->
                        Log.d(TAG, "SAVE: Found photo: ${photo.id} with path: ${photo.localPath}, status: ${photo.uploadStatus}")
                    }
                    
                    // Also verify we can count photos properly
                    val photoCount = wineyardPhotoDao.getPhotoCountForWineyard(photoEntity.wineyardId)
                    Log.d(TAG, "SAVE: Photo count via DAO: $photoCount")
                    
                    // CRITICAL: Force a small delay to ensure database consistency
                    kotlinx.coroutines.delay(100)
                    
                } else {
                    Log.e(TAG, "SAVE: FAILED - Photo not found after insert")
                }
                
                success
            } catch (e: Exception) {
                Log.e(TAG, "SAVE: Exception during photo save", e)
                false
            }
        }
    }
}