package com.ausgetrunken.domain.service

import android.net.Uri
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
            return Result.success(imageFile.absolutePath)
        }

        return try {

            if (!imageFile.exists()) {
                return Result.failure(Exception("Image file does not exist"))
            }

            val fileName = "wine_${wineId}_${UUID.randomUUID()}.jpg"
            val filePath = "wines/$wineId/$fileName"


            // Upload to Supabase Storage
            val uploadResult = storage.from(BUCKET_NAME).upload(filePath, imageFile.readBytes())


            // Get public URL
            val publicUrl = storage.from(BUCKET_NAME).publicUrl(filePath)

            Result.success(publicUrl)

        } catch (e: Exception) {
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

            // Create temporary file from URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("Cannot open image URI"))

            val tempFile = File.createTempFile("wine_upload_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }


            // Upload the temporary file
            val result = uploadWineImage(wineId, tempFile)

            // Clean up temporary file
            if (tempFile.exists()) {
                tempFile.delete()
            }

            result

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete wine image from cloud storage
     */
    suspend fun deleteWineImage(imageUrl: String): Result<Unit> {
        if (!ENABLE_CLOUD_UPLOAD) {
            return Result.success(Unit)
        }

        return try {
            // Extract file path from URL
            val filePath = extractFilePathFromUrl(imageUrl)
            if (filePath.isBlank()) {
                return Result.failure(Exception("Invalid image URL format"))
            }


            storage.from(BUCKET_NAME).delete(filePath)

            Result.success(Unit)

        } catch (e: Exception) {
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
                return ""
            }

            val path = url.substring(bucketIndex + bucketPrefix.length)
            return path

        } catch (e: Exception) {
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