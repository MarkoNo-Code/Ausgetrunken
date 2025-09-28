package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WineDao {
    @Query("SELECT * FROM wines")
    fun getAllWines(): Flow<List<WineEntity>>
    
    @Query("SELECT * FROM wines WHERE wineryId = :wineryId")
    fun getWinesByWinery(wineryId: String): Flow<List<WineEntity>>
    
    @Query("SELECT * FROM wines WHERE id = :wineId")
    suspend fun getWineById(wineId: String): WineEntity?
    
    @Query("SELECT * FROM wines WHERE id = :wineId")
    fun getWineByIdFlow(wineId: String): Flow<WineEntity?>
    
    @Query("SELECT * FROM wines WHERE stockQuantity <= :threshold")
    suspend fun getLowStockWines(threshold: Int): List<WineEntity>
    
    @Query("SELECT * FROM wines WHERE stockQuantity <= lowStockThreshold")
    suspend fun getWinesAtLowStockThreshold(): List<WineEntity>
    
    @Query("SELECT * FROM wines WHERE wineryId IN (SELECT id FROM wineries WHERE ownerId = :ownerId)")
    fun getWinesByOwner(ownerId: String): Flow<List<WineEntity>>
    
    @Query("SELECT * FROM wines ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getWinesPaginated(limit: Int, offset: Int): List<WineEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWine(wine: WineEntity)
    
    @Update
    suspend fun updateWine(wine: WineEntity)
    
    @Delete
    suspend fun deleteWine(wine: WineEntity)
    
    @Query("DELETE FROM wines WHERE id = :wineId")
    suspend fun deleteWine(wineId: String)
    
    @Query("UPDATE wines SET stockQuantity = :newQuantity, updatedAt = :timestamp WHERE id = :wineId")
    suspend fun updateWineStock(wineId: String, newQuantity: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM wines")
    suspend fun getAllWinesList(): List<WineEntity>
    
    @Query("SELECT * FROM wines WHERE wineryId = :wineryId")
    suspend fun getWinesByWineryList(wineryId: String): List<WineEntity>
    
    @Query("DELETE FROM wines")
    suspend fun clearAllWines()
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWines(wines: List<WineEntity>)
}