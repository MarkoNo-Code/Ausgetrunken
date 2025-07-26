package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WineyardPhotoEntity
import com.ausgetrunken.data.local.entities.PhotoUploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WineyardPhotoDao {
    
    @Query("SELECT * FROM wineyard_photos WHERE wineyard_id = :wineyardId ORDER BY display_order ASC, created_at ASC")
    fun getPhotosByWineyardId(wineyardId: String): Flow<List<WineyardPhotoEntity>>
    
    @Query("SELECT * FROM wineyard_photos WHERE wineyard_id = :wineyardId ORDER BY display_order ASC, created_at ASC")
    suspend fun getPhotosByWineyardIdSync(wineyardId: String): List<WineyardPhotoEntity>
    
    @Query("SELECT * FROM wineyard_photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: String): WineyardPhotoEntity?
    
    @Query("SELECT * FROM wineyard_photos WHERE upload_status = :status")
    suspend fun getPhotosByUploadStatus(status: PhotoUploadStatus): List<WineyardPhotoEntity>
    
    @Query("SELECT * FROM wineyard_photos WHERE local_path = :localPath")
    suspend fun getPhotoByLocalPath(localPath: String): WineyardPhotoEntity?
    
    @Query("SELECT * FROM wineyard_photos WHERE remote_url = :remoteUrl")
    suspend fun getPhotoByRemoteUrl(remoteUrl: String): WineyardPhotoEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: WineyardPhotoEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<WineyardPhotoEntity>)
    
    @Update
    suspend fun updatePhoto(photo: WineyardPhotoEntity)
    
    @Query("UPDATE wineyard_photos SET upload_status = :status, remote_url = :remoteUrl, updated_at = :updatedAt WHERE id = :photoId")
    suspend fun updatePhotoUploadStatus(photoId: String, status: PhotoUploadStatus, remoteUrl: String?, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE wineyard_photos SET display_order = :order WHERE id = :photoId")
    suspend fun updatePhotoDisplayOrder(photoId: String, order: Int)
    
    @Delete
    suspend fun deletePhoto(photo: WineyardPhotoEntity)
    
    @Query("DELETE FROM wineyard_photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: String)
    
    @Query("DELETE FROM wineyard_photos WHERE wineyard_id = :wineyardId")
    suspend fun deletePhotosByWineyardId(wineyardId: String)
    
    @Query("DELETE FROM wineyard_photos WHERE local_path = :localPath")
    suspend fun deletePhotoByLocalPath(localPath: String)
    
    @Query("SELECT COUNT(*) FROM wineyard_photos WHERE wineyard_id = :wineyardId")
    suspend fun getPhotoCountForWineyard(wineyardId: String): Int
    
}