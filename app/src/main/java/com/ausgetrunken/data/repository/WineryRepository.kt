package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.dao.WineryDao
import com.ausgetrunken.data.local.entities.WineryEntity
import com.ausgetrunken.data.remote.model.Winery
import com.ausgetrunken.domain.util.NetworkConnectivityManager
import com.ausgetrunken.domain.service.SupabaseWineryPhoto
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

class WineryRepository(
    private val wineryDao: WineryDao,
    private val postgrest: Postgrest,
    private val networkManager: NetworkConnectivityManager,
    authRepository: SupabaseAuthRepository
) : BaseRepository(authRepository) {
    fun getAllWinerys(): Flow<List<WineryEntity>> = wineryDao.getAllWinerys()
    
    fun getWinerysByOwner(ownerId: String): Flow<List<WineryEntity>> =
        wineryDao.getWinerysByOwner(ownerId)
    
    fun getWineryById(wineryId: String): Flow<WineryEntity?> =
        wineryDao.getWineryByIdFlow(wineryId)
    
    suspend fun getAllWinerysPaginated(limit: Int, offset: Int): List<WineryEntity> =
        wineryDao.getWinerysPaginated(limit, offset)
    
    // ================================================================================================
    // REMOTE-FIRST DATA STRATEGY METHODS
    // ================================================================================================
    // These methods implement the new architectural pattern:
    // 1. Always try Supabase first (source of truth)
    // 2. Fall back to local database only if network unavailable
    // 3. Update local cache with fresh remote data
    // ================================================================================================
    
    /**
     * Get all wineries using remote-first strategy
     */
    suspend fun getAllWinerysRemoteFirst(): List<WineryEntity> {
        return networkManager.executeRemoteFirst(
            operationName = "getAllWinerys",
            remoteOperation = {
                try {
                    println("🌐 WineryRepository: Fetching all wineries from Supabase...")
                    val response = postgrest.from("wineries")
                        .select()
                        .decodeList<Winery>()
                    
                    val entities = response.map { wineryData ->
                        WineryEntity(
                            id = wineryData.id,
                            name = wineryData.name,
                            description = wineryData.description ?: "",
                            ownerId = wineryData.ownerId,
                            address = wineryData.address,
                            latitude = wineryData.latitude,
                            longitude = wineryData.longitude,
                            photos = wineryData.photos ?: emptyList(),
                            createdAt = (wineryData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000,
                            updatedAt = (wineryData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000
                        )
                    }
                    
                    // Removed println: "✅ WineryRepository: Fetched ${entities.size} wineries from Supabase"
                    Result.success(entities)
                } catch (e: Exception) {
                    // Removed println: "❌ WineryRepository: Supabase fetch failed: ${e.message}"
                    Result.failure(e)
                }
            },
            localFallback = {
                println("📱 WineryRepository: Using local database for all wineries")
                wineryDao.getAllWinerysList()
            },
            cacheUpdate = { entities ->
                println("💾 WineryRepository: Updating local cache with ${entities.size} wineries")
                // Clear and replace local data to ensure consistency
                wineryDao.clearAllWinerys()
                entities.forEach { wineryDao.insertWinery(it) }
            }
        )
    }
    
    /**
     * Get wineries by owner using remote-first strategy
     */
    suspend fun getWinerysByOwnerRemoteFirst(ownerId: String): List<WineryEntity> {
        return networkManager.executeRemoteFirst(
            operationName = "getWinerysByOwner",
            remoteOperation = {
                try {
                    println("🌐 WineryRepository: Fetching wineries for owner $ownerId from Supabase...")
                    val response = postgrest.from("wineries")
                        .select {
                            filter {
                                eq("owner_id", ownerId)
                            }
                        }
                        .decodeList<Winery>()
                    
                    val entities = response.map { wineryData ->
                        // Fetch photos for this winery from the winery_photos table
                        val photos = try {
                            // Get photos from the separate table and extract just the remote_url field
                            val photosResult = postgrest.from("winery_photos")
                                .select {
                                    filter {
                                        eq("winery_id", wineryData.id)
                                    }
                                }
                                .decodeList<kotlinx.serialization.json.JsonObject>()
                                
                            photosResult.mapNotNull { jsonObject ->
                                jsonObject["remote_url"]?.jsonPrimitive?.content
                            }
                        } catch (e: Exception) {
                            // Removed println: "⚠️ WineryRepository: Failed to fetch photos for winery ${wineryData.id}: ${e.message}"
                            emptyList<String>()
                        }
                        
                        WineryEntity(
                            id = wineryData.id,
                            name = wineryData.name,
                            description = wineryData.description ?: "",
                            ownerId = wineryData.ownerId,
                            address = wineryData.address,
                            latitude = wineryData.latitude,
                            longitude = wineryData.longitude,
                            photos = photos, // Use photos from winery_photos table
                            createdAt = (wineryData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000,
                            updatedAt = (wineryData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000
                        )
                    }
                    
                    // Removed println: "✅ WineryRepository: Fetched ${entities.size} wineries for owner $ownerId from Supabase"
                    Result.success(entities)
                } catch (e: Exception) {
                    // Removed println: "❌ WineryRepository: Supabase fetch failed for owner $ownerId: ${e.message}"
                    Result.failure(e)
                }
            },
            localFallback = {
                println("📱 WineryRepository: Using local database for wineries by owner $ownerId")
                wineryDao.getWinerysByOwnerList(ownerId)
            },
            cacheUpdate = { entities ->
                println("💾 WineryRepository: Updating local cache with ${entities.size} wineries for owner $ownerId")
                // Update only this owner's wineries in local cache
                entities.forEach { wineryDao.insertWinery(it) }
            }
        )
    }
    
    /**
     * Get single winery by ID using remote-first strategy
     */
    suspend fun getWineryByIdRemoteFirst(wineryId: String): WineryEntity? {
        return networkManager.executeRemoteFirst(
            operationName = "getWineryById",
            remoteOperation = {
                try {
                    println("🌐 WineryRepository: Fetching winery $wineryId from Supabase...")
                    val response = postgrest.from("wineries")
                        .select {
                            filter {
                                eq("id", wineryId)
                            }
                        }
                        .decodeSingleOrNull<Winery>()
                    
                    val entity = response?.let { wineryData ->
                        WineryEntity(
                            id = wineryData.id,
                            name = wineryData.name,
                            description = wineryData.description ?: "",
                            ownerId = wineryData.ownerId,
                            address = wineryData.address,
                            latitude = wineryData.latitude,
                            longitude = wineryData.longitude,
                            photos = wineryData.photos ?: emptyList(),
                            createdAt = (wineryData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000,
                            updatedAt = (wineryData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000
                        )
                    }
                    
                    // Removed println: "✅ WineryRepository: Fetched winery $wineryId from Supabase: ${entity?.name ?: "not found"}"
                    Result.success(entity)
                } catch (e: Exception) {
                    // Removed println: "❌ WineryRepository: Supabase fetch failed for winery $wineryId: ${e.message}"
                    Result.failure(e)
                }
            },
            localFallback = {
                println("📱 WineryRepository: Using local database for winery $wineryId")
                wineryDao.getWineryById(wineryId)
            },
            cacheUpdate = { entity ->
                entity?.let { 
                    println("💾 WineryRepository: Updating local cache for winery ${entity.id}")
                    wineryDao.insertWinery(it) 
                }
            }
        )
    }

    suspend fun createWinery(winery: WineryEntity): Result<WineryEntity> {
        return execute {
            try {
                wineryDao.insertWinery(winery)
                
                postgrest.from("wineries")
                    .insert(
                        buildJsonObject {
                            put("id", winery.id)
                            put("name", winery.name)
                            put("description", winery.description)
                            put("owner_id", winery.ownerId)
                            put("address", winery.address)
                            put("latitude", winery.latitude)
                            put("longitude", winery.longitude)
                            // Note: photos are stored in separate winery_photos table, not here
                            put("created_at", Instant.now().toString()) // Use current time in ISO format
                            put("updated_at", Instant.now().toString()) // Use current time in ISO format
                        }
                    )
                Result.success(winery)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateWinery(winery: WineryEntity): Result<Unit> {
        return execute {
            try {
                println("🔄 WineryRepository: Starting remote-first winery update for ${winery.name} (${winery.id})")
                
                // REMOTE-FIRST: Update Supabase FIRST
                // Note: Only update fields that exist in Supabase schema
                val supabaseResponse = postgrest.from("wineries")
                    .update(
                        buildJsonObject {
                            put("name", winery.name)
                            put("description", winery.description)
                            put("address", winery.address)
                            put("latitude", winery.latitude)
                            put("longitude", winery.longitude)
                            // Note: 'photos' column doesn't exist in Supabase wineries table
                            // Photos are stored separately in winery_photos table
                            put("updated_at", Instant.now().toString()) // Use current time in ISO format
                        }
                    ) {
                        filter {
                            eq("id", winery.id)
                        }
                    }
                
                // Removed println: "✅ WineryRepository: Supabase update successful for winery ${winery.id}"
                
                // LOCAL SECOND: Only update local database AFTER successful remote update
                wineryDao.updateWinery(winery)
                // Removed println: "✅ WineryRepository: Local database updated for winery ${winery.id}"
                
                Result.success(Unit)
            } catch (e: Exception) {
                // Removed println: "❌ WineryRepository: Winery update failed: ${e.message}"
                Result.failure(e)
            }
        }
    }

    suspend fun deleteWinery(wineryId: String): Result<Unit> {
        return execute {
            try {
                wineryDao.deleteWinery(wineryId)
                
                postgrest.from("wineries")
                    .delete {
                        filter {
                            eq("id", wineryId)
                        }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWinerysNearLocation(lat: Double, lng: Double, radiusKm: Double): List<WineryEntity> {
        val radiusSquared = radiusKm * radiusKm / (111.0 * 111.0) // Rough conversion from km to degrees
        return wineryDao.getWinerysNearLocation(lat, lng, radiusSquared)
    }

    suspend fun syncWinerysFromFirestore(): Result<Unit> {
        return execute {
            try {
                println("🔄 WineryRepository: Starting sync from Supabase...")
                
                // For now, get all wineries - filtering will be implemented via database views or RPC later
                // The main filtering happens at login time to prevent flagged users from accessing the app
                val response = postgrest.from("wineries")
                    .select()
                    .decodeList<Winery>()
                    
                println("📊 WineryRepository: Fetched ${response.size} wineries from Supabase")
                
                val entities = response.map { wineryData ->
                    println("🏭 WineryRepository: Processing winery: ${wineryData.name} (ID: ${wineryData.id}, Owner: ${wineryData.ownerId})")

                    // Fetch photos for this winery from the winery_photos table
                    val photos = try {
                        // Get photos from the separate table and extract just the remote_url field
                        val photosResult = postgrest.from("winery_photos")
                            .select {
                                filter {
                                    eq("winery_id", wineryData.id)
                                }
                            }
                            .decodeList<kotlinx.serialization.json.JsonObject>()

                        photosResult.mapNotNull { jsonObject ->
                            jsonObject["remote_url"]?.jsonPrimitive?.content
                        }
                    } catch (e: Exception) {
                        // Removed println: "⚠️ WineryRepository: Failed to fetch photos for winery ${wineryData.id}: ${e.message}"
                        emptyList<String>()
                    }

                    WineryEntity(
                        id = wineryData.id,
                        name = wineryData.name,
                        description = wineryData.description ?: "",
                        ownerId = wineryData.ownerId,
                        address = wineryData.address,
                        latitude = wineryData.latitude,
                        longitude = wineryData.longitude,
                        photos = photos, // Use photos from winery_photos table
                        createdAt = (wineryData.createdAt.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000, // Convert seconds to milliseconds for local storage
                        updatedAt = (wineryData.updatedAt?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) * 1000 // Convert seconds to milliseconds for local storage
                    )
                }

                // Insert all entities
                entities.forEach { entity ->
                    wineryDao.insertWinery(entity)
                    println("💾 WineryRepository: Saved winery to local database: ${entity.name} with ${entity.photos.size} photos")
                }
                
                // Removed println: "✅ WineryRepository: Sync completed successfully"
                Result.success(Unit)
            } catch (e: Exception) {
                // Removed println: "❌ WineryRepository: Sync failed: ${e.message}"
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}