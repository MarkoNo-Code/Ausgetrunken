package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WineyardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WineyardDao {
    @Query("SELECT * FROM wineyards")
    fun getAllWineyards(): Flow<List<WineyardEntity>>
    
    @Query("SELECT * FROM wineyards WHERE id = :wineyardId")
    suspend fun getWineyardById(wineyardId: String): WineyardEntity?
    
    @Query("SELECT * FROM wineyards WHERE id = :wineyardId")
    fun getWineyardByIdFlow(wineyardId: String): Flow<WineyardEntity?>
    
    @Query("SELECT * FROM wineyards WHERE ownerId = :ownerId")
    fun getWineyardsByOwner(ownerId: String): Flow<List<WineyardEntity>>
    
    @Query("""
        SELECT * FROM wineyards 
        WHERE (:lat - latitude) * (:lat - latitude) + (:lng - longitude) * (:lng - longitude) < :radiusSquared
    """)
    suspend fun getWineyardsNearLocation(lat: Double, lng: Double, radiusSquared: Double): List<WineyardEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWineyard(wineyard: WineyardEntity)
    
    @Update
    suspend fun updateWineyard(wineyard: WineyardEntity)
    
    @Delete
    suspend fun deleteWineyard(wineyard: WineyardEntity)
    
    @Query("DELETE FROM wineyards WHERE id = :wineyardId")
    suspend fun deleteWineyard(wineyardId: String)
}