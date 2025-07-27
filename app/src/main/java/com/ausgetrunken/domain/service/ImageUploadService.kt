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
        private const val BUCKET_NAME = "wineyard-photos" // Updated bucket name for better organization
        private const val TAG = "ImageUploadService"
    }

    /**
     * Upload image to Supabase Storage and return the public URL
     */
    suspend fun uploadWineyardImage(
        wineyardId: String, 
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
            Log.d(TAG, "UPLOAD: Uploading image for wineyard: $wineyardId")
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
            
            val fileName = "wineyard_${wineyardId}_${UUID.randomUUID()}.jpg"
            val filePath = "wineyards/$fileName"
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
    suspend fun uploadWineyardImageFromUri(
        wineyardId: String,
        imageUri: Uri,
        contentResolver: android.content.ContentResolver
    ): Result<String> {
        if (!ENABLE_CLOUD_UPLOAD) {
            Log.d(TAG, "Cloud upload disabled, returning URI: $imageUri")
            return Result.success(imageUri.toString())
        }
        
        return try {
            Log.d(TAG, "Uploading image from URI for wineyard: $wineyardId")
            
            val fileName = "wineyard_${wineyardId}_${UUID.randomUUID()}.jpg"
            val filePath = "wineyards/$fileName"
            
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
    suspend fun deleteWineyardImage(imageUrl: String): Result<Unit> {
        return try {
            // Extract file path from public URL
            val filePath = extractFilePathFromUrl(imageUrl)
            if (filePath.isNotEmpty()) {
                storage.from(BUCKET_NAME).delete(filePath)
                Log.d(TAG, "Image deleted successfully: $imageUrl")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: $imageUrl", e)
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