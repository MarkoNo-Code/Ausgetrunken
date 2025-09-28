package com.ausgetrunken.domain.service

import com.ausgetrunken.data.local.entities.WineryEntity
import com.ausgetrunken.data.repository.WineryRepository
import com.ausgetrunken.domain.common.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WineryService(
    private val wineryRepository: WineryRepository
) {
    fun getAllWinerys(): Flow<List<WineryEntity>> {
        return wineryRepository.getAllWinerys()
    }

    fun getWinerysByOwner(ownerId: String): Flow<List<WineryEntity>> {
        return wineryRepository.getWinerysByOwner(ownerId)
    }

    suspend fun getWinerysByOwnerRemoteFirst(ownerId: String): List<WineryEntity> {
        return wineryRepository.getWinerysByOwnerRemoteFirst(ownerId)
    }

    fun getWineryById(wineryId: String): Flow<WineryEntity?> {
        return wineryRepository.getWineryById(wineryId)
    }

    suspend fun getWineryByIdRemoteFirst(wineryId: String): WineryEntity? {
        return wineryRepository.getWineryByIdRemoteFirst(wineryId)
    }

    suspend fun createWinery(winery: WineryEntity): Result<WineryEntity> {
        return wineryRepository.createWinery(winery)
    }

    suspend fun updateWinery(winery: WineryEntity): Result<Unit> {
        return wineryRepository.updateWinery(winery)
    }

    suspend fun deleteWinery(wineryId: String): Result<Unit> {
        return wineryRepository.deleteWinery(wineryId)
    }

    suspend fun getNearbyWinerys(lat: Double, lng: Double, radiusKm: Double = 50.0): List<WineryEntity> {
        return wineryRepository.getWinerysNearLocation(lat, lng, radiusKm)
    }

    suspend fun syncWinerys(): Result<Unit> {
        return wineryRepository.syncWinerysFromFirestore()
    }

    suspend fun getAllWinerysPaginated(limit: Int, offset: Int): List<WineryEntity> {
        return wineryRepository.getAllWinerysPaginated(limit, offset)
    }

    suspend fun validateWineryOwnership(userId: String, wineryId: String): Boolean {
        return try {
            val winery = getWineryById(wineryId).first()
            winery?.ownerId == userId
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateWineryLocation(
        wineryId: String,
        latitude: Double,
        longitude: Double,
        address: String
    ): AppResult<Unit> {
        return AppResult.catchingSuspend {
            val winery = getWineryById(wineryId).first()
                ?: throw Exception("Winery not found")
            
            val updatedWinery = winery.copy(
                latitude = latitude,
                longitude = longitude,
                address = address
            )
            
            val result = updateWinery(updatedWinery)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Failed to update winery location")
            }
        }
    }
}