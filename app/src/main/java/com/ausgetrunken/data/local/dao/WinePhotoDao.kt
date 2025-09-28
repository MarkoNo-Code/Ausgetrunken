package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WinePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WinePhotoDao {

    @Query("SELECT * FROM wine_photos WHERE wine_id = :wineId ORDER BY display_order ASC, created_at ASC")
    fun getWinePhotos(wineId: String): Flow<List<WinePhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: WinePhotoEntity)

    @Update
    suspend fun updatePhoto(photo: WinePhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: WinePhotoEntity)

    @Query("DELETE FROM wine_photos WHERE wine_id = :wineId")
    suspend fun deleteAllPhotosForWine(wineId: String)

    @Query("SELECT COUNT(*) FROM wine_photos WHERE wine_id = :wineId")
    suspend fun getPhotoCount(wineId: String): Int

    @Query("SELECT * FROM wine_photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: String): WinePhotoEntity?

    @Query("DELETE FROM wine_photos WHERE wine_id = :wineId AND remote_url = :remoteUrl")
    suspend fun deletePhotoByRemoteUrl(wineId: String, remoteUrl: String)

    @Query("SELECT * FROM wine_photos WHERE wine_id = :wineId AND remote_url = :remoteUrl")
    suspend fun getPhotoByRemoteUrl(wineId: String, remoteUrl: String): WinePhotoEntity?
}