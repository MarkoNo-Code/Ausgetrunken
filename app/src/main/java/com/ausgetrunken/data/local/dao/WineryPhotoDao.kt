package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WineryPhotoEntity
import com.ausgetrunken.data.local.entities.PhotoUploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WineryPhotoDao {
    
    @Query("SELECT * FROM winery_photos WHERE winery_id = :wineryId ORDER BY display_order ASC, created_at ASC")
    fun getPhotosByWineryId(wineryId: String): Flow<List<WineryPhotoEntity>>
    
    @Query("SELECT * FROM winery_photos WHERE winery_id = :wineryId ORDER BY display_order ASC, created_at ASC")
    suspend fun getPhotosByWineryIdSync(wineryId: String): List<WineryPhotoEntity>
    
    @Query("SELECT * FROM winery_photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: String): WineryPhotoEntity?
    
    @Query("SELECT * FROM winery_photos WHERE upload_status = :status")
    suspend fun getPhotosByUploadStatus(status: PhotoUploadStatus): List<WineryPhotoEntity>
    
    @Query("SELECT * FROM winery_photos WHERE local_path = :localPath")
    suspend fun getPhotoByLocalPath(localPath: String): WineryPhotoEntity?
    
    @Query("SELECT * FROM winery_photos WHERE remote_url = :remoteUrl")
    suspend fun getPhotoByRemoteUrl(remoteUrl: String): WineryPhotoEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: WineryPhotoEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<WineryPhotoEntity>)
    
    @Update
    suspend fun updatePhoto(photo: WineryPhotoEntity)
    
    @Query("UPDATE winery_photos SET upload_status = :status, remote_url = :remoteUrl, updated_at = :updatedAt WHERE id = :photoId")
    suspend fun updatePhotoUploadStatus(photoId: String, status: PhotoUploadStatus, remoteUrl: String?, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE winery_photos SET display_order = :order WHERE id = :photoId")
    suspend fun updatePhotoDisplayOrder(photoId: String, order: Int)
    
    @Delete
    suspend fun deletePhoto(photo: WineryPhotoEntity)
    
    @Query("DELETE FROM winery_photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: String)
    
    @Query("DELETE FROM winery_photos WHERE winery_id = :wineryId")
    suspend fun deletePhotosByWineryId(wineryId: String)
    
    @Query("DELETE FROM winery_photos WHERE local_path = :localPath")
    suspend fun deletePhotoByLocalPath(localPath: String)
    
    @Query("SELECT COUNT(*) FROM winery_photos WHERE winery_id = :wineryId")
    suspend fun getPhotoCountForWinery(wineryId: String): Int
    
}