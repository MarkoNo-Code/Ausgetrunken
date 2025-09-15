package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "wine_photos",
    foreignKeys = [
        ForeignKey(
            entity = WineEntity::class,
            parentColumns = ["id"],
            childColumns = ["wine_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WinePhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "wine_id") val wineId: String,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "remote_url") val remoteUrl: String? = null,
    @ColumnInfo(name = "display_order") val displayOrder: Int = 0,
    @ColumnInfo(name = "upload_status") val uploadStatus: PhotoUploadStatus = PhotoUploadStatus.LOCAL_ONLY,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)