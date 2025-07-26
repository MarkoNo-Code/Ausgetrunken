package com.ausgetrunken.domain.service

import android.net.Uri
import android.util.Log
import io.github.jan.supabase.storage.Storage
import java.io.File
import java.util.UUID

class ImageUploadService(
    private val storage: Storage
) {
    
    // Feature flag to control cloud uploads - TEMPORARILY DISABLED FOR DEBUGGING
    private val ENABLE_CLOUD_UPLOAD = false
    companion object {
        private const val BUCKET_NAME = "images" // Use default bucket that likely exists
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
            Log.d(TAG, "UPLOAD: Uploading image for wineyard: $wineyardId")
            Log.d(TAG, "UPLOAD: Image file: ${imageFile.absolutePath}, size: ${imageFile.length()}")
            
            val fileName = "wineyard_${wineyardId}_${UUID.randomUUID()}.jpg"
            val filePath = "wineyards/$fileName"
            Log.d(TAG, "UPLOAD: Target path: $filePath")
            
            // Upload file to Supabase Storage
            storage.from(BUCKET_NAME).upload(filePath, imageFile.readBytes(), upsert = false)
            
            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)
            
            Log.d(TAG, "UPLOAD: Image uploaded successfully: $publicUrl")
            Log.d(TAG, "=== CLOUD UPLOAD COMPLETED ===")
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image: ${e.message}, falling back to local path", e)
            // Always fallback to local path
            Result.success(imageFile.absolutePath)
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
            Log.e(TAG, "Failed to upload image from URI: ${e.message}, falling back to URI", e)
            // Always fallback to original URI
            Result.success(imageUri.toString())
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