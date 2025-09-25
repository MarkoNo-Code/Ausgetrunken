package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.dao.WineyardDao
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.remote.model.Wineyard
import com.ausgetrunken.domain.util.NetworkConnectivityManager
import com.ausgetrunken.domain.service.SupabaseWineyardPhoto
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.format.DateTimeFormatter

class WineyardRepository(
    private val wineyardDao: WineyardDao,
    private val postgrest: Postgrest,
    private val networkManager: NetworkConnectivityManager,
    authRepository: SupabaseAuthRepository
) : BaseRepository(authRepository) {
    fun getAllWineyards(): Flow<List<WineyardEntity>> = wineyardDao.getAllWineyards()
    
    fun getWineyardsByOwner(ownerId: String): Flow<List<WineyardEntity>> = 
        wineyardDao.getWineyardsByOwner(ownerId)
    
    fun getWineyardById(wineyardId: String): Flow<WineyardEntity?> = 
        wineyardDao.getWineyardByIdFlow(wineyardId)
    
    suspend fun getAllWineyardsPaginated(limit: Int, offset: Int): List<WineyardEntity> = 
        wineyardDao.getWineyardsPaginated(limit, offset)
    
    // ================================================================================================
    // REMOTE-FIRST DATA STRATEGY METHODS
    // ================================================================================================
    // These methods implement the new architectural pattern:
    // 1. Always try Supabase first (source of truth)
    // 2. Fall back to local database only if network unavailable
    // 3. Update local cache with fresh remote data
    // ================================================================================================
    
    /**
     * Get all wineyards using remote-first strategy
     */
    suspend fun getAllWineyardsRemoteFirst(): List<WineyardEntity> {
        return networkManager.executeRemoteFirst(
            operationName = "getAllWineyards",
            remoteOperation = {
                try {
                    println("🌐 WineyardRepository: Fetching all wineyards from Supabase...")
                    val response = postgrest.from("wineyards")
                        .select()
                        .decodeList<Wineyard>()
                    
                    val entities = response.map { wineyardData ->
                        WineyardEntity(
                            id = wineyardData.id,
                            name = wineyardData.name,
                            description = wineyardData.description ?: "",
                            ownerId = wineyardData.ownerId,
                            address = wineyardData.address,
                            latitude = wineyardData.latitude,
                            longitude = wineyardData.longitude,
                            photos = wineyardData.photos ?: emptyList(),
                            createdAt = (wineyardData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000,
                            updatedAt = (wineyardData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000
                        )
                    }
                    
                    println("✅ WineyardRepository: Fetched ${entities.size} wineyards from Supabase")
                    Result.success(entities)
                } catch (e: Exception) {
                    println("❌ WineyardRepository: Supabase fetch failed: ${e.message}")
                    Result.failure(e)
                }
            },
            localFallback = {
                println("📱 WineyardRepository: Using local database for all wineyards")
                wineyardDao.getAllWineyardsList()
            },
            cacheUpdate = { entities ->
                println("💾 WineyardRepository: Updating local cache with ${entities.size} wineyards")
                // Clear and replace local data to ensure consistency
                wineyardDao.clearAllWineyards()
                entities.forEach { wineyardDao.insertWineyard(it) }
            }
        )
    }
    
    /**
     * Get wineyards by owner using remote-first strategy
     */
    suspend fun getWineyardsByOwnerRemoteFirst(ownerId: String): List<WineyardEntity> {
        return networkManager.executeRemoteFirst(
            operationName = "getWineyardsByOwner",
            remoteOperation = {
                try {
                    println("🌐 WineyardRepository: Fetching wineyards for owner $ownerId from Supabase...")
                    val response = postgrest.from("wineyards")
                        .select {
                            filter {
                                eq("owner_id", ownerId)
                            }
                        }
                        .decodeList<Wineyard>()
                    
                    val entities = response.map { wineyardData ->
                        // Fetch photos for this wineyard from the wineyard_photos table
                        val photos = try {
                            // Get photos from the separate table and extract just the remote_url field
                            val photosResult = postgrest.from("wineyard_photos")
                                .select {
                                    filter {
                                        eq("wineyard_id", wineyardData.id)
                                    }
                                }
                                .decodeList<kotlinx.serialization.json.JsonObject>()
                                
                            photosResult.mapNotNull { jsonObject ->
                                jsonObject["remote_url"]?.jsonPrimitive?.content
                            }
                        } catch (e: Exception) {
                            println("⚠️ WineyardRepository: Failed to fetch photos for wineyard ${wineyardData.id}: ${e.message}")
                            emptyList<String>()
                        }
                        
                        WineyardEntity(
                            id = wineyardData.id,
                            name = wineyardData.name,
                            description = wineyardData.description ?: "",
                            ownerId = wineyardData.ownerId,
                            address = wineyardData.address,
                            latitude = wineyardData.latitude,
                            longitude = wineyardData.longitude,
                            photos = photos, // Use photos from wineyard_photos table
                            createdAt = (wineyardData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000,
                            updatedAt = (wineyardData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000
                        )
                    }
                    
                    println("✅ WineyardRepository: Fetched ${entities.size} wineyards for owner $ownerId from Supabase")
                    Result.success(entities)
                } catch (e: Exception) {
                    println("❌ WineyardRepository: Supabase fetch failed for owner $ownerId: ${e.message}")
                    Result.failure(e)
                }
            },
            localFallback = {
                println("📱 WineyardRepository: Using local database for wineyards by owner $ownerId")
                wineyardDao.getWineyardsByOwnerList(ownerId)
            },
            cacheUpdate = { entities ->
                println("💾 WineyardRepository: Updating local cache with ${entities.size} wineyards for owner $ownerId")
                // Update only this owner's wineyards in local cache
                entities.forEach { wineyardDao.insertWineyard(it) }
            }
        )
    }
    
    /**
     * Get single wineyard by ID using remote-first strategy
     */
    suspend fun getWineyardByIdRemoteFirst(wineyardId: String): WineyardEntity? {
        return networkManager.executeRemoteFirst(
            operationName = "getWineyardById",
            remoteOperation = {
                try {
                    println("🌐 WineyardRepository: Fetching wineyard $wineyardId from Supabase...")
                    val response = postgrest.from("wineyards")
                        .select {
                            filter {
                                eq("id", wineyardId)
                            }
                        }
                        .decodeSingleOrNull<Wineyard>()
                    
                    val entity = response?.let { wineyardData ->
                        WineyardEntity(
                            id = wineyardData.id,
                            name = wineyardData.name,
                            description = wineyardData.description ?: "",
                            ownerId = wineyardData.ownerId,
                            address = wineyardData.address,
                            latitude = wineyardData.latitude,
                            longitude = wineyardData.longitude,
                            photos = wineyardData.photos ?: emptyList(),
                            createdAt = (wineyardData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000,
                            updatedAt = (wineyardData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000
                        )
                    }
                    
                    println("✅ WineyardRepository: Fetched wineyard $wineyardId from Supabase: ${entity?.name ?: "not found"}")
                    Result.success(entity)
                } catch (e: Exception) {
                    println("❌ WineyardRepository: Supabase fetch failed for wineyard $wineyardId: ${e.message}")
                    Result.failure(e)
                }
            },
            localFallback = {
                println("📱 WineyardRepository: Using local database for wineyard $wineyardId")
                wineyardDao.getWineyardById(wineyardId)
            },
            cacheUpdate = { entity ->
                entity?.let { 
                    println("💾 WineyardRepository: Updating local cache for wineyard ${entity.id}")
                    wineyardDao.insertWineyard(it) 
                }
            }
        )
    }

    suspend fun createWineyard(wineyard: WineyardEntity): Result<WineyardEntity> {
        return execute {
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
                            // Note: photos are stored in separate wineyard_photos table, not here
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
        return execute {
            try {
                println("🔄 WineyardRepository: Starting remote-first wineyard update for ${wineyard.name} (${wineyard.id})")
                
                // REMOTE-FIRST: Update Supabase FIRST
                // Note: Only update fields that exist in Supabase schema
                val supabaseResponse = postgrest.from("wineyards")
                    .update(
                        buildJsonObject {
                            put("name", wineyard.name)
                            put("description", wineyard.description)
                            put("address", wineyard.address)
                            put("latitude", wineyard.latitude)
                            put("longitude", wineyard.longitude)
                            // Note: 'photos' column doesn't exist in Supabase wineyards table
                            // Photos are stored separately in wineyard_photos table
                            put("updated_at", Instant.now().toString()) // Use current time in ISO format
                        }
                    ) {
                        filter {
                            eq("id", wineyard.id)
                        }
                    }
                
                println("✅ WineyardRepository: Supabase update successful for wineyard ${wineyard.id}")
                
                // LOCAL SECOND: Only update local database AFTER successful remote update
                wineyardDao.updateWineyard(wineyard)
                println("✅ WineyardRepository: Local database updated for wineyard ${wineyard.id}")
                
                Result.success(Unit)
            } catch (e: Exception) {
                println("❌ WineyardRepository: Wineyard update failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun deleteWineyard(wineyardId: String): Result<Unit> {
        return execute {
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
        return execute {
            try {
                println("🔄 WineyardRepository: Starting sync from Supabase...")
                
                // For now, get all wineyards - filtering will be implemented via database views or RPC later
                // The main filtering happens at login time to prevent flagged users from accessing the app
                val response = postgrest.from("wineyards")
                    .select()
                    .decodeList<Wineyard>()
                    
                println("📊 WineyardRepository: Fetched ${response.size} wineyards from Supabase")
                
                val entities = response.map { wineyardData ->
                    println("🏭 WineyardRepository: Processing wineyard: ${wineyardData.name} (ID: ${wineyardData.id}, Owner: ${wineyardData.ownerId})")

                    // Fetch photos for this wineyard from the wineyard_photos table
                    val photos = try {
                        // Get photos from the separate table and extract just the remote_url field
                        val photosResult = postgrest.from("wineyard_photos")
                            .select {
                                filter {
                                    eq("wineyard_id", wineyardData.id)
                                }
                            }
                            .decodeList<kotlinx.serialization.json.JsonObject>()

                        photosResult.mapNotNull { jsonObject ->
                            jsonObject["remote_url"]?.jsonPrimitive?.content
                        }
                    } catch (e: Exception) {
                        println("⚠️ WineyardRepository: Failed to fetch photos for wineyard ${wineyardData.id}: ${e.message}")
                        emptyList<String>()
                    }

                    WineyardEntity(
                        id = wineyardData.id,
                        name = wineyardData.name,
                        description = wineyardData.description ?: "",
                        ownerId = wineyardData.ownerId,
                        address = wineyardData.address,
                        latitude = wineyardData.latitude,
                        longitude = wineyardData.longitude,
                        photos = photos, // Use photos from wineyard_photos table
                        createdAt = (wineyardData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000, // Convert seconds to milliseconds for local storage
                        updatedAt = (wineyardData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000 // Convert seconds to milliseconds for local storage
                    )
                }

                // Insert all entities
                entities.forEach { entity ->
                    wineyardDao.insertWineyard(entity)
                    println("💾 WineyardRepository: Saved wineyard to local database: ${entity.name} with ${entity.photos.size} photos")
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