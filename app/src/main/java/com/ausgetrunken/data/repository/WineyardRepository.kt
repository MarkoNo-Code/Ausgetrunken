package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.dao.WineyardDao
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.remote.model.Wineyard
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.format.DateTimeFormatter

class WineyardRepository(
    private val wineyardDao: WineyardDao,
    private val postgrest: Postgrest,
    authRepository: SupabaseAuthRepository
) : BaseRepository(authRepository) {
    fun getAllWineyards(): Flow<List<WineyardEntity>> = wineyardDao.getAllWineyards()
    
    fun getWineyardsByOwner(ownerId: String): Flow<List<WineyardEntity>> = 
        wineyardDao.getWineyardsByOwner(ownerId)
    
    fun getWineyardById(wineyardId: String): Flow<WineyardEntity?> = 
        wineyardDao.getWineyardByIdFlow(wineyardId)
    
    suspend fun getAllWineyardsPaginated(limit: Int, offset: Int): List<WineyardEntity> = 
        wineyardDao.getWineyardsPaginated(limit, offset)

    suspend fun createWineyard(wineyard: WineyardEntity): Result<WineyardEntity> {
        return withSessionValidation {
            try {
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
                            put("created_at", Instant.now().toString()) // Use current time in ISO format
                            put("updated_at", Instant.now().toString()) // Use current time in ISO format
                        }
                    )
                Result.success(wineyard)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateWineyard(wineyard: WineyardEntity): Result<Unit> {
        return withSessionValidation {
            try {
                wineyardDao.updateWineyard(wineyard)
                
                postgrest.from("wineyards")
                    .update(
                        buildJsonObject {
                            put("name", wineyard.name)
                            put("description", wineyard.description)
                            put("address", wineyard.address)
                            put("latitude", wineyard.latitude)
                            put("longitude", wineyard.longitude)
                            put("updated_at", Instant.now().toString()) // Use current time in ISO format
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
    }

    suspend fun deleteWineyard(wineyardId: String): Result<Unit> {
        return withSessionValidation {
            try {
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
    }

    suspend fun getWineyardsNearLocation(lat: Double, lng: Double, radiusKm: Double): List<WineyardEntity> {
        val radiusSquared = radiusKm * radiusKm / (111.0 * 111.0) // Rough conversion from km to degrees
        return wineyardDao.getWineyardsNearLocation(lat, lng, radiusSquared)
    }

    suspend fun syncWineyardsFromFirestore(): Result<Unit> {
        return withSessionValidation {
            try {
                println("🔄 WineyardRepository: Starting sync from Supabase...")
                
                // For now, get all wineyards - filtering will be implemented via database views or RPC later
                // The main filtering happens at login time to prevent flagged users from accessing the app
                val response = postgrest.from("wineyards")
                    .select()
                    .decodeList<Wineyard>()
                    
                println("📊 WineyardRepository: Fetched ${response.size} wineyards from Supabase")
                
                response.forEach { wineyardData ->
                    println("🏭 WineyardRepository: Processing wineyard: ${wineyardData.name} (ID: ${wineyardData.id}, Owner: ${wineyardData.ownerId})")
                    
                    val entity = WineyardEntity(
                        id = wineyardData.id,
                        name = wineyardData.name,
                        description = wineyardData.description ?: "",
                        ownerId = wineyardData.ownerId,
                        address = wineyardData.address,
                        latitude = wineyardData.latitude,
                        longitude = wineyardData.longitude,
                        createdAt = (wineyardData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000, // Convert seconds to milliseconds for local storage
                        updatedAt = (wineyardData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000 // Convert seconds to milliseconds for local storage
                    )
                    wineyardDao.insertWineyard(entity)
                    println("💾 WineyardRepository: Saved wineyard to local database: ${entity.name}")
                }
                
                println("✅ WineyardRepository: Sync completed successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ WineyardRepository: Sync failed: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}