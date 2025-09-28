package com.ausgetrunken.domain.service

import android.net.Uri
import android.util.Log
import io.github.jan.supabase.storage.Storage
import java.io.File
import java.util.UUID

class WineImageUploadService(
    private val storage: Storage
) {

    // Feature flag to control cloud uploads - ENABLED for remote-first strategy
    private val ENABLE_CLOUD_UPLOAD = true
    companion object {
        private const val BUCKET_NAME = "wine-photos" // Dedicated bucket for wine photos
        private const val TAG = "WineImageUploadService"
    }

    /**
     * Upload wine image to Supabase Storage and return the public URL
     */
    suspend fun uploadWineImage(
        wineId: String,
        imageFile: File
    ): Result<String> {
        if (!ENABLE_CLOUD_UPLOAD) {
            Log.d(TAG, "Cloud upload disabled, returning local file path: ${imageFile.absolutePath}")
            return Result.success(imageFile.absolutePath)
        }

        return try {
            Log.d(TAG, "UPLOAD: Starting wine image upload for wine: $wineId")
            Log.d(TAG, "UPLOAD: File path: ${imageFile.absolutePath}")
            Log.d(TAG, "UPLOAD: File size: ${imageFile.length()} bytes")
            Log.d(TAG, "UPLOAD: Target bucket: $BUCKET_NAME")

            if (!imageFile.exists()) {
                Log.e(TAG, "UPLOAD ERROR: Image file does not exist: ${imageFile.absolutePath}")
                return Result.failure(Exception("Image file does not exist"))
            }

            val fileName = "wine_${wineId}_${UUID.randomUUID()}.jpg"
            val filePath = "wines/$wineId/$fileName"

            Log.d(TAG, "UPLOAD: Generated path: $filePath")

            // Upload to Supabase Storage
            val uploadResult = storage.from(BUCKET_NAME).upload(filePath, imageFile.readBytes())

            Log.d(TAG, "UPLOAD: Upload successful, key: ${uploadResult.path}")

            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)
            Log.d(TAG, "UPLOAD: Public URL generated: $publicUrl")

            Result.success(publicUrl)

        } catch (e: Exception) {
            Log.e(TAG, "UPLOAD ERROR: Failed to upload wine image", e)
            Result.failure(e)
        }
    }

    /**
     * Upload wine image from URI
     */
    suspend fun uploadWineImage(
        wineId: String,
        imageUri: Uri,
        context: android.content.Context
    ): Result<String> {
        return try {
            Log.d(TAG, "UPLOAD: Converting URI to file for wine: $wineId")
            Log.d(TAG, "UPLOAD: URI: $imageUri")

            // Create temporary file from URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("Cannot open image URI"))

            val tempFile = File.createTempFile("wine_upload_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            Log.d(TAG, "UPLOAD: Temporary file created: ${tempFile.absolutePath} (${tempFile.length()} bytes)")

            // Upload the temporary file
            val result = uploadWineImage(wineId, tempFile)

            // Clean up temporary file
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d(TAG, "UPLOAD: Temporary file cleaned up")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "UPLOAD ERROR: Failed to upload wine image from URI", e)
            Result.failure(e)
        }
    }

    /**
     * Delete wine image from cloud storage
     */
    suspend fun deleteWineImage(imageUrl: String): Result<Unit> {
        if (!ENABLE_CLOUD_UPLOAD) {
            Log.d(TAG, "Cloud upload disabled, skipping deletion for: $imageUrl")
            return Result.success(Unit)
        }

        return try {
            // Extract file path from URL
            val filePath = extractFilePathFromUrl(imageUrl)
            if (filePath.isBlank()) {
                Log.w(TAG, "Cannot extract file path from URL: $imageUrl")
                return Result.failure(Exception("Invalid image URL format"))
            }

            Log.d(TAG, "Attempting to delete from bucket '$BUCKET_NAME' with path: $filePath")

            storage.from(BUCKET_NAME).delete(filePath)
            Log.d(TAG, "Successfully deleted wine image: $filePath")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete wine image: $imageUrl", e)
            Result.failure(e)
        }
    }

    /**
     * Extract file path from Supabase storage URL
     */
    private fun extractFilePathFromUrl(url: String): String {
        try {
            // Format: https://[project-id].supabase.co/storage/v1/object/public/[bucket]/[path]
            val bucketPrefix = "/storage/v1/object/public/$BUCKET_NAME/"
            val bucketIndex = url.indexOf(bucketPrefix)

            if (bucketIndex == -1) {
                Log.w(TAG, "URL does not contain expected bucket path: $url")
                return ""
            }

            val path = url.substring(bucketIndex + bucketPrefix.length)
            Log.d(TAG, "Extracted file path: $path")
            return path

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting file path from URL: $url", e)
            return ""
        }
    }

    /**
     * Generate wine photo upload path
     */
    fun generateWinePhotoPath(wineId: String, fileName: String = UUID.randomUUID().toString()): String {
        return "wines/$wineId/$fileName.jpg"
    }
}