package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import android.util.Log
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
                Log.d(TAG, "Created profile pictures directory: $absolutePath (success: $created)")
            }
        }
    }
    
    /**
     * Upload a profile picture from URI and return the public URL.
     * This replaces any existing profile picture for the user.
     */
    suspend fun uploadProfilePicture(userId: String, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== UPLOADING PROFILE PICTURE ===")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Image URI: $imageUri")
            
            // Copy to local storage first for backup/caching
            val localFile = copyUriToLocalStorage(imageUri, userId)
            if (localFile == null || !localFile.exists() || localFile.length() == 0L) {
                Log.e(TAG, "Failed to save profile picture locally")
                return@withContext Result.failure(Exception("Failed to save profile picture locally"))
            }
            
            Log.d(TAG, "Profile picture saved locally: ${localFile.absolutePath} (${localFile.length()} bytes)")
            
            // Delete any existing profile picture from Supabase first
            deleteExistingProfilePicture(userId)
            
            // Upload to Supabase Storage
            val fileName = "profile_${userId}_${UUID.randomUUID()}.jpg"
            val filePath = "users/$fileName"
            
            Log.d(TAG, "Uploading to Supabase: $filePath")
            storage.from(BUCKET_NAME).upload(filePath, localFile.readBytes(), upsert = false)
            
            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)
            
            Log.d(TAG, "✅ Profile picture uploaded successfully: $publicUrl")
            Log.d(TAG, "=== UPLOAD COMPLETED ===")
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload profile picture: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a profile picture from file path and return the public URL.
     * This replaces any existing profile picture for the user.
     */
    suspend fun uploadProfilePicture(userId: String, filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== UPLOADING PROFILE PICTURE FROM FILE ===")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "File path: $filePath")
            
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
            
            Log.d(TAG, "Uploading to Supabase: $supabaseFilePath")
            storage.from(BUCKET_NAME).upload(supabaseFilePath, sourceFile.readBytes(), upsert = false)
            
            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(supabaseFilePath)
            
            Log.d(TAG, "✅ Profile picture uploaded successfully: $publicUrl")
            Log.d(TAG, "=== UPLOAD COMPLETED ===")
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload profile picture: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a user's profile picture from Supabase Storage.
     */
    suspend fun deleteProfilePicture(profilePictureUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "=== DELETING PROFILE PICTURE ===")
            Log.d(TAG, "URL to delete: $profilePictureUrl")
            
            // Extract file path from public URL
            val filePath = extractFilePathFromUrl(profilePictureUrl)
            Log.d(TAG, "Extracted file path: $filePath")
            
            if (filePath.isNotEmpty()) {
                Log.d(TAG, "Deleting from bucket '$BUCKET_NAME' with path: $filePath")
                storage.from(BUCKET_NAME).delete(filePath)
                Log.d(TAG, "✅ Profile picture deleted successfully from Supabase")
            } else {
                Log.w(TAG, "Could not extract file path from URL, skipping deletion")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete profile picture: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete any existing profile pictures for a user from Supabase.
     * This is called before uploading a new one to ensure only one picture per user.
     */
    private suspend fun deleteExistingProfilePicture(userId: String) {
        try {
            Log.d(TAG, "Checking for existing profile pictures for user: $userId")
            
            // List files in the user's directory
            val userFolderPath = "users"
            val existingFiles = storage.from(BUCKET_NAME).list(userFolderPath)
            
            // Find files that belong to this user (start with "profile_{userId}_")
            val userFiles = existingFiles.filter { 
                it.name.startsWith("profile_${userId}_") 
            }
            
            Log.d(TAG, "Found ${userFiles.size} existing profile pictures for user $userId")
            
            // Delete each existing file
            userFiles.forEach { file ->
                try {
                    val filePath = "$userFolderPath/${file.name}"
                    Log.d(TAG, "Deleting existing profile picture: $filePath")
                    storage.from(BUCKET_NAME).delete(filePath)
                    Log.d(TAG, "✅ Deleted existing profile picture: ${file.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete existing profile picture ${file.name}: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check/delete existing profile pictures: ${e.message}")
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
                Log.d(TAG, "Successfully copied URI to local storage: ${destFile.absolutePath}")
                destFile
            } else {
                Log.e(TAG, "Failed to copy URI - file is empty or doesn't exist")
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to local storage", e)
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
            Log.e(TAG, "Failed to extract file path from URL: $url", e)
            ""
        }
    }
}