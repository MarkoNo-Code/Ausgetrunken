package com.ausgetrunken.domain.model

/**
 * Represents the upload status of a photo to cloud storage
 */
data class PhotoUploadInfo(
    val localPath: String,
    val status: UploadStatus,
    val cloudUrl: String? = null,
    val uploadProgress: Float = 0f,
    val lastAttempt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

enum class UploadStatus {
    PENDING,     // Waiting to be uploaded
    UPLOADING,   // Currently uploading
    COMPLETED,   // Successfully uploaded
    FAILED,      // Upload failed
    LOCAL_ONLY   // Keep local only (not for cloud)
}

/**
 * UI state for photo with upload status
 */
data class PhotoWithStatus(
    val localPath: String,
    val uploadInfo: PhotoUploadInfo
) {
    val isUploaded: Boolean get() = uploadInfo.status == UploadStatus.COMPLETED
    val isUploading: Boolean get() = uploadInfo.status == UploadStatus.UPLOADING
    val hasFailed: Boolean get() = uploadInfo.status == UploadStatus.FAILED
    val cloudUrl: String? get() = uploadInfo.cloudUrl
}