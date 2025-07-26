package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "wineyard_photos",
    foreignKeys = [
        ForeignKey(
            entity = WineyardEntity::class,
            parentColumns = ["id"],
            childColumns = ["wineyard_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WineyardPhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "wineyard_id") val wineyardId: String,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "remote_url") val remoteUrl: String? = null,
    @ColumnInfo(name = "display_order") val displayOrder: Int = 0,
    @ColumnInfo(name = "upload_status") val uploadStatus: PhotoUploadStatus = PhotoUploadStatus.LOCAL_ONLY,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

enum class PhotoUploadStatus {
    LOCAL_ONLY,      // Photo exists only locally
    UPLOADING,       // Photo is being uploaded to Supabase
    UPLOADED,        // Photo successfully uploaded to Supabase
    UPLOAD_FAILED,   // Photo upload failed, needs retry
    SYNCED           // Photo exists both locally and remotely
}