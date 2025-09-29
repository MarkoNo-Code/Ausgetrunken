package com.ausgetrunken.domain.service

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.storage.Storage
import java.io.File
import java.util.UUID

/**
 * Unified photo upload service for all photo types (winery, wine, profile)
 * Simplifies the upload process with a consistent interface
 */
class UnifiedPhotoUploadService(
    private val storage: Storage
) {
    companion object {
        private const val TAG = "UnifiedPhotoUpload"
    }

    /**
     * Upload any type of photo to Supabase storage
     *
     * @param imageFile The image file to upload
     * @param bucketName The target bucket (e.g., "winery-photos", "wine-photos", "profile-pictures")
     * @param storagePath The path within the bucket (e.g., "wineries/", "wines/wineId/", "users/")
     * @param filePrefix The filename prefix (e.g., "winery_", "wine_", "profile_")
     * @param entityId The ID of the entity (winery, wine, user)
     * @return Result with the public URL or error
     */
    suspend fun uploadPhoto(
        imageFile: File,
        bucketName: String,
        storagePath: String,
        filePrefix: String,
        entityId: String
    ): Result<String> {
        return try {

            // Validate file
            if (!imageFile.exists()) {
                return Result.failure(Exception("Image file does not exist"))
            }

            if (imageFile.length() == 0L) {
                return Result.failure(Exception("Image file is empty"))
            }

            // Generate unique filename
            val uniqueId = UUID.randomUUID()
            val fileName = "${filePrefix}${entityId}_${uniqueId}.jpg"
            val fullPath = "${storagePath.trimEnd('/')}/$fileName"


            // Upload to Supabase Storage
            val uploadResult = storage.from(bucketName).upload(fullPath, imageFile.readBytes())

            // Get public URL
            val publicUrl = storage.from(bucketName).publicUrl(fullPath)

            Result.success(publicUrl)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload photo from URI (for image picker results)
     */
    suspend fun uploadPhoto(
        imageUri: Uri,
        context: Context,
        bucketName: String,
        storagePath: String,
        filePrefix: String,
        entityId: String
    ): Result<String> {
        return try {

            // Create temporary file from URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("Cannot open image URI"))

            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }


            // Upload the temporary file
            val result = uploadPhoto(tempFile, bucketName, storagePath, filePrefix, entityId)

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
     * Delete photo from storage
     */
    suspend fun deletePhoto(photoUrl: String, bucketName: String): Result<Unit> {
        return try {
            // Extract file path from URL
            val filePath = extractFilePathFromUrl(photoUrl, bucketName)
            if (filePath.isBlank()) {
                return Result.failure(Exception("Invalid photo URL format"))
            }


            storage.from(bucketName).delete(filePath)

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract file path from Supabase storage URL
     */
    private fun extractFilePathFromUrl(url: String, bucketName: String): String {
        return try {
            val bucketPrefix = "/storage/v1/object/public/$bucketName/"
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

    // Convenience methods for specific photo types
    suspend fun uploadWineryPhoto(imageFile: File, wineryId: String): Result<String> {
        return uploadPhoto(imageFile, "winery-photos", "wineries", "winery_", wineryId)
    }

    suspend fun uploadWinePhoto(imageFile: File, wineId: String): Result<String> {
        return uploadPhoto(imageFile, "wine-photos", "wines/$wineId", "wine_", wineId)
    }

    suspend fun uploadProfilePhoto(imageFile: File, userId: String): Result<String> {
        return uploadPhoto(imageFile, "profile-pictures", "users", "profile_", userId)
    }

    suspend fun uploadWineryPhoto(imageUri: Uri, context: Context, wineryId: String): Result<String> {
        return uploadPhoto(imageUri, context, "winery-photos", "wineries", "winery_", wineryId)
    }

    suspend fun uploadWinePhoto(imageUri: Uri, context: Context, wineId: String): Result<String> {
        return uploadPhoto(imageUri, context, "wine-photos", "wines/$wineId", "wine_", wineId)
    }

    suspend fun uploadProfilePhoto(imageUri: Uri, context: Context, userId: String): Result<String> {
        return uploadPhoto(imageUri, context, "profile-pictures", "users", "profile_", userId)
    }
}