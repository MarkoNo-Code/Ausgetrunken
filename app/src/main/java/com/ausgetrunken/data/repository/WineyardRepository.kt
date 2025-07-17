package com.ausgetrunken.data.repository

import com.ausgetrunken.data.local.dao.WineyardDao
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.remote.model.Wineyard
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WineyardRepository(
    private val wineyardDao: WineyardDao,
    private val postgrest: Postgrest
) {
    fun getAllWineyards(): Flow<List<WineyardEntity>> = wineyardDao.getAllWineyards()
    
    fun getWineyardsByOwner(ownerId: String): Flow<List<WineyardEntity>> = 
        wineyardDao.getWineyardsByOwner(ownerId)
    
    fun getWineyardById(wineyardId: String): Flow<WineyardEntity?> = 
        wineyardDao.getWineyardByIdFlow(wineyardId)

    suspend fun createWineyard(wineyard: WineyardEntity): Result<WineyardEntity> {
        return try {
            wineyardDao.insertWineyard(wineyard)
            
            postgrest.from("wineyards")
                .insert(
                    buildJsonObject {
                        put("id", wineyard.id)
                        put("name", wineyard.name)
                        put("description", wineyard.description)
                        put("owner_id", wineyard.ownerId)
                        put("address", wineyard.address)
                        put("latitude", wineyard.latitude)
                        put("longitude", wineyard.longitude)
                        put("created_at", wineyard.createdAt.toString())
                        put("updated_at", wineyard.updatedAt.toString())
                    }
                )
            Result.success(wineyard)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWineyard(wineyard: WineyardEntity): Result<Unit> {
        return try {
            wineyardDao.updateWineyard(wineyard)
            
            postgrest.from("wineyards")
                .update(
                    buildJsonObject {
                        put("name", wineyard.name)
                        put("description", wineyard.description)
                        put("address", wineyard.address)
                        put("latitude", wineyard.latitude)
                        put("longitude", wineyard.longitude)
                        put("updated_at", System.currentTimeMillis().toString())
                    }
                ) {
                    filter {
                        eq("id", wineyard.id)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteWineyard(wineyardId: String): Result<Unit> {
        return try {
            wineyardDao.deleteWineyard(wineyardId)
            
            postgrest.from("wineyards")
                .delete {
                    filter {
                        eq("id", wineyardId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWineyardsNearLocation(lat: Double, lng: Double, radiusKm: Double): List<WineyardEntity> {
        val radiusSquared = radiusKm * radiusKm / (111.0 * 111.0) // Rough conversion from km to degrees
        return wineyardDao.getWineyardsNearLocation(lat, lng, radiusSquared)
    }

    suspend fun syncWineyardsFromFirestore(): Result<Unit> {
        return try {
            val response = postgrest.from("wineyards")
                .select()
                .decodeList<Wineyard>()
                
            response.forEach { wineyardData ->
                val entity = WineyardEntity(
                    id = wineyardData.id,
                    name = wineyardData.name,
                    description = wineyardData.description ?: "",
                    ownerId = wineyardData.ownerId,
                    address = wineyardData.address,
                    latitude = wineyardData.latitude,
                    longitude = wineyardData.longitude,
                    createdAt = wineyardData.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = wineyardData.updatedAt?.toLongOrNull() ?: System.currentTimeMillis()
                )
                wineyardDao.insertWineyard(entity)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}