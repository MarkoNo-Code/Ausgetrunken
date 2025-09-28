package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WineryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WineryDao {
    @Query("SELECT * FROM wineries")
    fun getAllWinerys(): Flow<List<WineryEntity>>
    
    @Query("SELECT * FROM wineries WHERE id = :wineryId")
    suspend fun getWineryById(wineryId: String): WineryEntity?
    
    @Query("SELECT * FROM wineries WHERE id = :wineryId")
    fun getWineryByIdFlow(wineryId: String): Flow<WineryEntity?>
    
    @Query("SELECT * FROM wineries WHERE ownerId = :ownerId")
    fun getWinerysByOwner(ownerId: String): Flow<List<WineryEntity>>
    
    // Remote-first strategy support methods
    @Query("SELECT * FROM wineries")
    suspend fun getAllWinerysList(): List<WineryEntity>
    
    @Query("SELECT * FROM wineries WHERE ownerId = :ownerId")
    suspend fun getWinerysByOwnerList(ownerId: String): List<WineryEntity>
    
    @Query("DELETE FROM wineries")
    suspend fun clearAllWinerys()
    
    @Query("SELECT * FROM wineries ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getWinerysPaginated(limit: Int, offset: Int): List<WineryEntity>
    
    @Query("""
        SELECT * FROM wineries 
        WHERE (:lat - latitude) * (:lat - latitude) + (:lng - longitude) * (:lng - longitude) < :radiusSquared
    """)
    suspend fun getWinerysNearLocation(lat: Double, lng: Double, radiusSquared: Double): List<WineryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWinery(winery: WineryEntity)
    
    @Update
    suspend fun updateWinery(winery: WineryEntity)
    
    @Delete
    suspend fun deleteWinery(winery: WineryEntity)
    
    @Query("DELETE FROM wineries WHERE id = :wineryId")
    suspend fun deleteWinery(wineryId: String)
}