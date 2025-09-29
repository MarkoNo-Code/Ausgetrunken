package com.ausgetrunken.domain.service

import android.net.Uri
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
            return Result.success(imageFile.absolutePath)
        }
        
        return try {
            
            if (!imageFile.exists()) {
                return Result.failure(Exception("Image file does not exist: ${imageFile.absolutePath}"))
            }
            
            if (imageFile.length() == 0L) {
                return Result.failure(Exception("Image file is empty"))
            }
            
            val fileName = "winery_${wineryId}_${UUID.randomUUID()}.jpg"
            val filePath = "wineries/$fileName"
            
            // Upload file to Supabase Storage
            storage.from(BUCKET_NAME).upload(filePath, imageFile.readBytes(), upsert = false)
            
            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)
            
            Result.success(publicUrl)
            
        } catch (e: Exception) {
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
            return Result.success(imageUri.toString())
        }
        
        return try {

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
            
            Result.success(publicUrl)
            
        } catch (e: Exception) {
            // Don't silently fallback - this causes photos to be marked as uploaded with wrong URLs
            Result.failure(e)
        }
    }

    /**
     * Delete image from Supabase Storage
     */
    suspend fun deleteWineryImage(imageUrl: String): Result<Unit> {
        return try {
            
            // Extract file path from public URL
            val filePath = extractFilePathFromUrl(imageUrl)
            
            if (filePath.isNotEmpty()) {
                val deleteResult = storage.from(BUCKET_NAME).delete(filePath)
                
                // Verify deletion by trying to check if file still exists
                try {
                    val folderPath = filePath.substringBeforeLast("/")
                    val fileName = filePath.substringAfterLast("/")
                    
                    val listResult = storage.from(BUCKET_NAME).list(folderPath)
                    
                    val fileStillExists = listResult.any { it.name == fileName }
                    
                    if (fileStillExists) {
                    } else {
                    }
                } catch (e: Exception) {
                }
            } else {
                return Result.failure(Exception("Could not extract file path from URL: $imageUrl"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
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
            ""
        }
    }
}