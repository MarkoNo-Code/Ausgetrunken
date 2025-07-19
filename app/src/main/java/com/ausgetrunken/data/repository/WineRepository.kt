package com.ausgetrunken.data.repository

import com.ausgetrunken.data.local.dao.WineDao
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.data.remote.model.Wine
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WineRepository(
    private val wineDao: WineDao,
    private val postgrest: Postgrest
) {
    fun getAllWines(): Flow<List<WineEntity>> = wineDao.getAllWines()
    
    fun getWinesByWineyard(wineyardId: String): Flow<List<WineEntity>> = 
        wineDao.getWinesByWineyard(wineyardId)
    
    fun getWineById(wineId: String): Flow<WineEntity?> = 
        wineDao.getWineByIdFlow(wineId)
    
    suspend fun getAllWinesPaginated(limit: Int, offset: Int): List<WineEntity> = 
        wineDao.getWinesPaginated(limit, offset)

    suspend fun createWine(wine: WineEntity): Result<WineEntity> {
        return try {
            // Insert to Supabase first
            postgrest.from("wines")
                .insert(
                    buildJsonObject {
                        put("id", wine.id)
                        put("wineyard_id", wine.wineyardId)
                        put("name", wine.name)
                        put("description", wine.description)
                        put("wine_type", wine.wineType.name)
                        put("vintage", wine.vintage)
                        put("price", wine.price)
                        put("stock_quantity", wine.stockQuantity)
                        // Only include columns that exist in the database schema
                        // discounted_price, low_stock_threshold, photos are not in the current schema
                    }
                )
            
            // Also save to local Room database for immediate availability
            wineDao.insertWine(wine)
            
            Result.success(wine)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWine(wine: WineEntity): Result<Unit> {
        return try {
            wineDao.updateWine(wine)
            
            postgrest.from("wines")
                .update(
                    buildJsonObject {
                        put("name", wine.name)
                        put("description", wine.description)
                        put("wine_type", wine.wineType.name)
                        put("vintage", wine.vintage)
                        put("price", wine.price)
                        put("stock_quantity", wine.stockQuantity)
                        // Only include columns that exist in the database schema
                        // discounted_price, low_stock_threshold, photos are not in the current schema
                    }
                ) {
                    filter {
                        eq("id", wine.id)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteWine(wineId: String): Result<Unit> {
        return try {
            wineDao.deleteWine(wineId)
            
            postgrest.from("wines")
                .delete {
                    filter {
                        eq("id", wineId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLowStockWines(threshold: Int = 20): List<WineEntity> {
        return wineDao.getLowStockWines(threshold)
    }
    
    suspend fun getWinesByWineyardFromSupabase(wineyardId: String): List<WineEntity> {
        return try {
            val response = postgrest.from("wines")
                .select() {
                    filter {
                        eq("wineyard_id", wineyardId)
                    }
                }
                .decodeList<Wine>()
            
            response.map { wineData ->
                WineEntity(
                    id = wineData.id,
                    wineyardId = wineData.wineyardId,
                    name = wineData.name,
                    description = wineData.description,
                    wineType = com.ausgetrunken.data.local.entities.WineType.valueOf(wineData.wineType),
                    vintage = wineData.vintage,
                    price = wineData.price,
                    discountedPrice = null, // Not in current database schema
                    stockQuantity = wineData.stockQuantity,
                    lowStockThreshold = 20, // Default value since not in database schema
                    photos = emptyList(), // Not in current database schema
                    createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun syncWinesFromSupabase(): Result<Unit> {
        return try {
            val response = postgrest.from("wines")
                .select()
                .decodeList<Wine>()
                
            response.forEach { wineData ->
                val entity = WineEntity(
                    id = wineData.id,
                    wineyardId = wineData.wineyardId,
                    name = wineData.name,
                    description = wineData.description,
                    wineType = com.ausgetrunken.data.local.entities.WineType.valueOf(wineData.wineType),
                    vintage = wineData.vintage,
                    price = wineData.price,
                    discountedPrice = null, // Not in current database schema
                    stockQuantity = wineData.stockQuantity,
                    lowStockThreshold = 20, // Default value since not in database schema
                    photos = emptyList(), // Not in current database schema
                    createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                )
                try {
                    wineDao.insertWine(entity)
                } catch (e: Exception) {
                    // Continue with other wines even if one fails
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}