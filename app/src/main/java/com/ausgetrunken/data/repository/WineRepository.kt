package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.dao.WineDao
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.data.remote.model.Wine
import com.ausgetrunken.domain.util.NetworkConnectivityManager
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonNull

class WineRepository(
    private val wineDao: WineDao,
    private val postgrest: Postgrest,
    private val networkManager: NetworkConnectivityManager,
    authRepository: SupabaseAuthRepository
) : BaseRepository(authRepository) {
    fun getAllWines(): Flow<List<WineEntity>> = wineDao.getAllWines()
    
    fun getWinesByWineyard(wineyardId: String): Flow<List<WineEntity>> = 
        wineDao.getWinesByWineyard(wineyardId)
    
    fun getWineById(wineId: String): Flow<WineEntity?> = 
        wineDao.getWineByIdFlow(wineId)
    
    suspend fun getAllWinesPaginated(limit: Int, offset: Int): List<WineEntity> = 
        wineDao.getWinesPaginated(limit, offset)

    suspend fun createWine(wine: WineEntity): Result<WineEntity> {
        return execute {
            try {
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
                            put("low_stock_threshold", wine.lowStockThreshold)
                            put("full_stock_quantity", wine.fullStockQuantity)
                            // Include discounted_price if it exists, otherwise null
                            wine.discountedPrice?.let { put("discounted_price", it) } ?: put("discounted_price", JsonNull)
                            // Note: photos are not stored in the database schema yet
                        }
                    )
                
                // Also save to local Room database for immediate availability
                wineDao.insertWine(wine)
                
                Result.success(wine)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateWine(wine: WineEntity): Result<Unit> {
        return execute {
            try {
                println("🔄 WineRepository: Starting remote-first wine update for ${wine.name} (${wine.id})")
                
                // REMOTE-FIRST: Update Supabase FIRST
                val supabaseResponse = postgrest.from("wines")
                    .update(
                        buildJsonObject {
                            put("name", wine.name)
                            put("description", wine.description)
                            put("wine_type", wine.wineType.name)
                            put("vintage", wine.vintage)
                            put("price", wine.price)
                            put("stock_quantity", wine.stockQuantity)
                            put("low_stock_threshold", wine.lowStockThreshold)
                            put("full_stock_quantity", wine.fullStockQuantity)
                            // Include discounted_price if it exists, otherwise null
                            wine.discountedPrice?.let { put("discounted_price", it) } ?: put("discounted_price", JsonNull)
                        }
                    ) {
                        filter {
                            eq("id", wine.id)
                        }
                    }
                
                println("✅ WineRepository: Supabase update successful for wine ${wine.id}")
                
                // LOCAL SECOND: Only update local database AFTER successful remote update
                wineDao.updateWine(wine)
                println("✅ WineRepository: Local database updated for wine ${wine.id}")
                
                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ WineRepository: Wine update failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun deleteWine(wineId: String): Result<Unit> {
        return execute {
            try {
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
    }

    suspend fun getLowStockWines(threshold: Int = 20): List<WineEntity> {
        return wineDao.getLowStockWines(threshold)
    }
    
    suspend fun getWinesByWineyardFromSupabase(wineyardId: String): List<WineEntity> {
        return try {
            println("🍷 WineRepository: Fetching wines for wineyard: $wineyardId")
            val response = postgrest.from("wines")
                .select() {
                    filter {
                        eq("wineyard_id", wineyardId)
                    }
                }
                .decodeList<Wine>()
            
            println("🍷 WineRepository: Found ${response.size} wines for wineyard $wineyardId in Supabase")
            
            val wines = response.map { wineData ->
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
                    fullStockQuantity = wineData.fullStockQuantity ?: wineData.stockQuantity, // Use current stock as fallback
                    lowStockThreshold = wineData.lowStockThreshold ?: 20, // Use database value or default to 20
                    photos = emptyList(), // Not in current database schema
                    createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                )
                println("🍷 WineRepository: Wine: ${entity.name} (${entity.id})")
                entity
            }
            
            wines
        } catch (e: Exception) {
            println("❌ WineRepository: Failed to fetch wines for wineyard $wineyardId: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun syncWinesFromSupabase(): Result<Unit> {
        return execute {
            try {
                println("🍷 WineRepository: Starting sync of wines from Supabase...")
                val response = postgrest.from("wines")
                    .select()
                    .decodeList<Wine>()
                    
                println("🍷 WineRepository: Found ${response.size} wines in Supabase")
                
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
                        fullStockQuantity = wineData.fullStockQuantity ?: wineData.stockQuantity, // Use current stock as fallback
                        lowStockThreshold = wineData.lowStockThreshold ?: 20, // Use database value or default to 20
                        photos = emptyList(), // Not in current database schema
                        createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                        updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                    try {
                        wineDao.insertWine(entity)
                        println("🍷 WineRepository: Synced wine: ${entity.name} (${entity.id}) for wineyard ${entity.wineyardId}")
                    } catch (e: Exception) {
                        println("⚠️ WineRepository: Failed to insert wine ${entity.name}: ${e.message}")
                        // Continue with other wines even if one fails
                    }
                }
                println("✅ WineRepository: Wine sync completed successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ WineRepository: Wine sync failed: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
    
    suspend fun getAllWinesRemoteFirst(): List<WineEntity> {
        return networkManager.executeRemoteFirst(
            operationName = "getAllWines",
            remoteOperation = {
                val response = postgrest.from("wines")
                    .select()
                    .decodeList<Wine>()
                    
                val wines = response.map { wineData ->
                    WineEntity(
                        id = wineData.id,
                        wineyardId = wineData.wineyardId,
                        name = wineData.name,
                        description = wineData.description,
                        wineType = com.ausgetrunken.data.local.entities.WineType.valueOf(wineData.wineType),
                        vintage = wineData.vintage,
                        price = wineData.price,
                        discountedPrice = wineData.discountedPrice,
                        stockQuantity = wineData.stockQuantity,
                        fullStockQuantity = wineData.fullStockQuantity ?: wineData.stockQuantity,
                        lowStockThreshold = wineData.lowStockThreshold ?: 20,
                        photos = emptyList(),
                        createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                        updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                }
                Result.success(wines)
            },
            localFallback = {
                wineDao.getAllWinesList()
            },
            cacheUpdate = { wines ->
                wineDao.clearAllWines()
                wineDao.insertWines(wines)
            }
        )
    }
    
    suspend fun getWinesByWineyardRemoteFirst(wineyardId: String): List<WineEntity> {
        return networkManager.executeRemoteFirst(
            operationName = "getWinesByWineyard",
            remoteOperation = {
                val response = postgrest.from("wines")
                    .select() {
                        filter {
                            eq("wineyard_id", wineyardId)
                        }
                    }
                    .decodeList<Wine>()
                    
                val wines = response.map { wineData ->
                    WineEntity(
                        id = wineData.id,
                        wineyardId = wineData.wineyardId,
                        name = wineData.name,
                        description = wineData.description,
                        wineType = com.ausgetrunken.data.local.entities.WineType.valueOf(wineData.wineType),
                        vintage = wineData.vintage,
                        price = wineData.price,
                        discountedPrice = wineData.discountedPrice,
                        stockQuantity = wineData.stockQuantity,
                        fullStockQuantity = wineData.fullStockQuantity ?: wineData.stockQuantity,
                        lowStockThreshold = wineData.lowStockThreshold ?: 20,
                        photos = emptyList(),
                        createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                        updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                }
                Result.success(wines)
            },
            localFallback = {
                wineDao.getWinesByWineyardList(wineyardId)
            }
        )
    }
    
    suspend fun getWineByIdRemoteFirst(wineId: String): WineEntity? {
        val wines = networkManager.executeRemoteFirst(
            operationName = "getWineById",
            remoteOperation = {
                val response = postgrest.from("wines")
                    .select() {
                        filter {
                            eq("id", wineId)
                        }
                    }
                    .decodeList<Wine>()
                    
                val wine = response.firstOrNull()?.let { wineData ->
                    WineEntity(
                        id = wineData.id,
                        wineyardId = wineData.wineyardId,
                        name = wineData.name,
                        description = wineData.description,
                        wineType = com.ausgetrunken.data.local.entities.WineType.valueOf(wineData.wineType),
                        vintage = wineData.vintage,
                        price = wineData.price,
                        discountedPrice = wineData.discountedPrice,
                        stockQuantity = wineData.stockQuantity,
                        fullStockQuantity = wineData.fullStockQuantity ?: wineData.stockQuantity,
                        lowStockThreshold = wineData.lowStockThreshold ?: 20,
                        photos = emptyList(),
                        createdAt = wineData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                        updatedAt = wineData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                }
                Result.success(listOfNotNull(wine))
            },
            localFallback = {
                listOfNotNull(wineDao.getWineById(wineId))
            },
            cacheUpdate = { wines ->
                wines.forEach { wine ->
                    wineDao.insertWine(wine)
                }
            }
        )
        return wines.firstOrNull()
    }
}