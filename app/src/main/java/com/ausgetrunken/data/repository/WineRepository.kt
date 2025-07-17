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

    suspend fun createWine(wine: WineEntity): Result<WineEntity> {
        return try {
            wineDao.insertWine(wine)
            
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
                        wine.discountedPrice?.let { put("discounted_price", it) }
                        put("stock_quantity", wine.stockQuantity)
                        put("low_stock_threshold", wine.lowStockThreshold)
                        put("photos", wine.photos.joinToString(","))
                        put("created_at", wine.createdAt.toString())
                        put("updated_at", wine.updatedAt.toString())
                    }
                )
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
                        wine.discountedPrice?.let { put("discounted_price", it) }
                        put("stock_quantity", wine.stockQuantity)
                        put("low_stock_threshold", wine.lowStockThreshold)
                        put("photos", wine.photos.joinToString(","))
                        put("updated_at", System.currentTimeMillis().toString())
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
                    discountedPrice = wineData.discountedPrice,
                    stockQuantity = wineData.stockQuantity,
                    lowStockThreshold = wineData.lowStockThreshold,
                    photos = wineData.photos?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                )
                wineDao.insertWine(entity)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}