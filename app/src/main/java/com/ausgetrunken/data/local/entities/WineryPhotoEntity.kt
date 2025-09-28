package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "winery_photos",
    foreignKeys = [
        ForeignKey(
            entity = WineryEntity::class,
            parentColumns = ["id"],
            childColumns = ["winery_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["winery_id"])
    ]
)
data class WineryPhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "winery_id") val wineryId: String,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "remote_url") val remoteUrl: String? = null,
    @ColumnInfo(name = "display_order") val displayOrder: Int = 0,
    @ColumnInfo(name = "upload_status") val uploadStatus: PhotoUploadStatus = PhotoUploadStatus.LOCAL_ONLY,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class PhotoUploadStatus {
    LOCAL_ONLY,      // Photo exists only locally
    UPLOADING,       // Photo is being uploaded to Supabase
    UPLOADED,        // Photo successfully uploaded to Supabase
    UPLOAD_FAILED,   // Photo upload failed, needs retry
    SYNCED           // Photo exists both locally and remotely
}