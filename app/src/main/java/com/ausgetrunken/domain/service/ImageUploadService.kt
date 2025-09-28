package com.ausgetrunken.domain.service

import android.net.Uri
import android.util.Log
import io.github.jan.supabase.storage.Storage
import java.io.File
import java.util.UUID

class ImageUploadService(
    private val storage: Storage
) {
    
    // Feature flag to control cloud uploads - ENABLED for remote-first strategy
    private val ENABLE_CLOUD_UPLOAD = true
    companion object {
        private const val BUCKET_NAME = "winery-photos" // Updated bucket name for better organization
        private const val TAG = "ImageUploadService"
    }

    /**
     * Upload image to Supabase Storage and return the public URL
     */
    suspend fun uploadWineryImage(
        wineryId: String,
        imageFile: File
    ): Result<String> {
        if (!ENABLE_CLOUD_UPLOAD) {
            Log.d(TAG, "Cloud upload disabled, returning local file path: ${imageFile.absolutePath}")
            return Result.success(imageFile.absolutePath)
        }
        
        return try {
            Log.d(TAG, "=== STARTING CLOUD UPLOAD ===")
            Log.d(TAG, "UPLOAD: Cloud upload enabled: $ENABLE_CLOUD_UPLOAD")
            Log.d(TAG, "UPLOAD: Target bucket: $BUCKET_NAME")
            Log.d(TAG, "UPLOAD: Uploading image for winery: $wineryId")
            Log.d(TAG, "UPLOAD: Image file: ${imageFile.absolutePath}")
            Log.d(TAG, "UPLOAD: File exists: ${imageFile.exists()}")
            Log.d(TAG, "UPLOAD: File size: ${imageFile.length()} bytes")
            
            if (!imageFile.exists()) {
                Log.e(TAG, "ERROR: Image file does not exist!")
                return Result.failure(Exception("Image file does not exist: ${imageFile.absolutePath}"))
            }
            
            if (imageFile.length() == 0L) {
                Log.e(TAG, "ERROR: Image file is empty!")
                return Result.failure(Exception("Image file is empty"))
            }
            
            val fileName = "winery_${wineryId}_${UUID.randomUUID()}.jpg"
            val filePath = "wineries/$fileName"
            Log.d(TAG, "UPLOAD: Target path: $filePath")
            
            // Upload file to Supabase Storage
            Log.d(TAG, "UPLOAD: Starting upload to Supabase...")
            storage.from(BUCKET_NAME).upload(filePath, imageFile.readBytes(), upsert = false)
            Log.d(TAG, "UPLOAD: Upload to Supabase completed successfully")
            
            // Get public URL
            Log.d(TAG, "UPLOAD: Getting public URL...")
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)
            
            Log.d(TAG, "UPLOAD: Image uploaded successfully: $publicUrl")
            Log.d(TAG, "=== CLOUD UPLOAD COMPLETED ===")
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image: ${e.message}", e)
            // Don't silently fallback - this causes photos to be marked as uploaded with wrong URLs
            Result.failure(e)
        }
    }

    /**
     * Upload image from URI to Supabase Storage
     */
    suspend fun uploadWineryImageFromUri(
        wineryId: String,
        imageUri: Uri,
        contentResolver: android.content.ContentResolver
    ): Result<String> {
        if (!ENABLE_CLOUD_UPLOAD) {
            Log.d(TAG, "Cloud upload disabled, returning URI: $imageUri")
            return Result.success(imageUri.toString())
        }
        
        return try {
            Log.d(TAG, "Uploading image from URI for winery: $wineryId")

            val fileName = "winery_${wineryId}_${UUID.randomUUID()}.jpg"
            val filePath = "wineries/$fileName"
            
            // Read bytes from URI
            val inputStream = contentResolver.openInputStream(imageUri)
            val imageBytes = inputStream?.readBytes() ?: throw Exception("Could not read image from URI")
            inputStream?.close()
            
            // Upload to Supabase Storage
            storage.from(BUCKET_NAME).upload(filePath, imageBytes, upsert = false)
            
            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)
            
            Log.d(TAG, "Image uploaded successfully from URI: $publicUrl")
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image from URI: ${e.message}", e)
            // Don't silently fallback - this causes photos to be marked as uploaded with wrong URLs
            Result.failure(e)
        }
    }

    /**
     * Delete image from Supabase Storage
     */
    suspend fun deleteWineryImage(imageUrl: String): Result<Unit> {
        return try {
            Log.d(TAG, "=== DELETING IMAGE FROM SUPABASE ===")
            Log.d(TAG, "Image URL to delete: ${imageUrl.replace("\n", "\\n")}")
            
            // Extract file path from public URL
            val filePath = extractFilePathFromUrl(imageUrl)
            Log.d(TAG, "Extracted file path: '${filePath.replace("\n", "\\n")}'")
            
            if (filePath.isNotEmpty()) {
                Log.d(TAG, "Attempting to delete from bucket '$BUCKET_NAME' with path: $filePath")
                val deleteResult = storage.from(BUCKET_NAME).delete(filePath)
                Log.d(TAG, "Delete operation result: $deleteResult")
                Log.d(TAG, "Image deleted successfully from Supabase: $imageUrl")
                
                // Verify deletion by trying to check if file still exists
                try {
                    val folderPath = filePath.substringBeforeLast("/")
                    val fileName = filePath.substringAfterLast("/")
                    Log.d(TAG, "Verification: Checking folder '$folderPath' for file '$fileName'")
                    
                    val listResult = storage.from(BUCKET_NAME).list(folderPath)
                    Log.d(TAG, "Verification: Found ${listResult.size} files in folder")
                    
                    val fileStillExists = listResult.any { it.name == fileName }
                    Log.d(TAG, "Verification: File '$fileName' still exists in storage: $fileStillExists")
                    
                    if (fileStillExists) {
                        Log.w(TAG, "⚠️ WARNING: File was not deleted from storage despite delete operation!")
                        Log.w(TAG, "⚠️ File that still exists: $fileName")
                    } else {
                        Log.d(TAG, "✅ CONFIRMED: File was successfully deleted from storage")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify deletion: ${e.message}")
                }
            } else {
                Log.w(TAG, "Empty file path extracted from URL, cannot delete: $imageUrl")
                return Result.failure(Exception("Could not extract file path from URL: $imageUrl"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image from Supabase: $imageUrl", e)
            Result.failure(e)
        }
    }

    private fun extractFilePathFromUrl(url: String): String {
        // Extract the file path from Supabase Storage URL
        // Format: https://[project-id].supabase.co/storage/v1/object/public/[bucket]/[path]
        return try {
            val parts = url.split("/storage/v1/object/public/$BUCKET_NAME/")
            if (parts.size > 1) parts[1] else ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract file path from URL: $url")
            ""
        }
    }
}