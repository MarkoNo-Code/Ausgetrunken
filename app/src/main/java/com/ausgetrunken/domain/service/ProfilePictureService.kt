package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Service for managing user profile pictures with Supabase Storage persistence.
 * Only one profile picture per user - new uploads replace the previous one.
 */
class ProfilePictureService(
    private val context: Context,
    private val storage: Storage
) {
    companion object {
        private const val TAG = "ProfilePictureService"
        private const val BUCKET_NAME = "profile-pictures"
        private const val PROFILE_PICTURES_DIR = "profile_images"
    }
    
    // Local storage directory for profile pictures
    private val profilePicturesDirectory: File by lazy {
        val preferredDirectory = context.getExternalFilesDir(PROFILE_PICTURES_DIR)
        val directory = preferredDirectory ?: File(context.filesDir, PROFILE_PICTURES_DIR)
        
        directory.apply {
            if (!exists()) {
                val created = mkdirs()
            }
        }
    }
    
    /**
     * Upload a profile picture from URI and return the public URL.
     * This replaces any existing profile picture for the user.
     */
    suspend fun uploadProfilePicture(userId: String, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            
            // Copy to local storage first for backup/caching
            val localFile = copyUriToLocalStorage(imageUri, userId)
            if (localFile == null || !localFile.exists() || localFile.length() == 0L) {
                return@withContext Result.failure(Exception("Failed to save profile picture locally"))
            }
            
            
            // Delete any existing profile picture from Supabase first
            deleteExistingProfilePicture(userId)
            
            // Upload to Supabase Storage
            val fileName = "profile_${userId}_${UUID.randomUUID()}.jpg"
            val filePath = "users/$fileName"
            
            storage.from(BUCKET_NAME).upload(filePath, localFile.readBytes(), upsert = false)
            
            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)
            
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Upload a profile picture from file path and return the public URL.
     * This replaces any existing profile picture for the user.
     */
    suspend fun uploadProfilePicture(userId: String, filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $filePath"))
            }
            
            if (sourceFile.length() == 0L) {
                return@withContext Result.failure(Exception("Source file is empty"))
            }
            
            // Delete any existing profile picture from Supabase first
            deleteExistingProfilePicture(userId)
            
            // Upload to Supabase Storage
            val fileName = "profile_${userId}_${UUID.randomUUID()}.jpg"
            val supabaseFilePath = "users/$fileName"
            
            storage.from(BUCKET_NAME).upload(supabaseFilePath, sourceFile.readBytes(), upsert = false)
            
            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(supabaseFilePath)
            
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a user's profile picture from Supabase Storage.
     */
    suspend fun deleteProfilePicture(profilePictureUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            
            // Extract file path from public URL
            val filePath = extractFilePathFromUrl(profilePictureUrl)
            
            if (filePath.isNotEmpty()) {
                storage.from(BUCKET_NAME).delete(filePath)
            } else {
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete any existing profile pictures for a user from Supabase.
     * This is called before uploading a new one to ensure only one picture per user.
     */
    private suspend fun deleteExistingProfilePicture(userId: String) {
        try {
            
            // List files in the user's directory
            val userFolderPath = "users"
            val existingFiles = storage.from(BUCKET_NAME).list(userFolderPath)
            
            // Find files that belong to this user (start with "profile_{userId}_")
            val userFiles = existingFiles.filter { 
                it.name.startsWith("profile_${userId}_") 
            }
            
            
            // Delete each existing file
            userFiles.forEach { file ->
                try {
                    val filePath = "$userFolderPath/${file.name}"
                    storage.from(BUCKET_NAME).delete(filePath)
                } catch (e: Exception) {
                }
            }
            
        } catch (e: Exception) {
        }
    }
    
    /**
     * Copy URI to local storage for caching
     */
    private suspend fun copyUriToLocalStorage(uri: Uri, userId: String): File? {
        return try {
            val fileName = "profile_${userId}.jpg"
            val destFile = File(profilePicturesDirectory, fileName)
            
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
     * Extract file path from Supabase public URL
     */
    private fun extractFilePathFromUrl(url: String): String {
        return try {
            // Extract the path after the bucket name in the URL
            // URL format: https://xxx.supabase.co/storage/v1/object/public/profile-pictures/users/profile_user_uuid.jpg
            val parts = url.split("/storage/v1/object/public/$BUCKET_NAME/")
            if (parts.size >= 2) {
                parts[1].trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}