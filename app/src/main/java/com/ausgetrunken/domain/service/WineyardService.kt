package com.ausgetrunken.domain.service

import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.repository.WineyardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WineyardService(
    private val wineyardRepository: WineyardRepository
) {
    fun getAllWineyards(): Flow<List<WineyardEntity>> {
        return wineyardRepository.getAllWineyards()
    }

    fun getWineyardsByOwner(ownerId: String): Flow<List<WineyardEntity>> {
        return wineyardRepository.getWineyardsByOwner(ownerId)
    }

    fun getWineyardById(wineyardId: String): Flow<WineyardEntity?> {
        return wineyardRepository.getWineyardById(wineyardId)
    }

    suspend fun createWineyard(wineyard: WineyardEntity): Result<WineyardEntity> {
        return wineyardRepository.createWineyard(wineyard)
    }

    suspend fun updateWineyard(wineyard: WineyardEntity): Result<Unit> {
        return wineyardRepository.updateWineyard(wineyard)
    }

    suspend fun deleteWineyard(wineyardId: String): Result<Unit> {
        return wineyardRepository.deleteWineyard(wineyardId)
    }

    suspend fun getNearbyWineyards(lat: Double, lng: Double, radiusKm: Double = 50.0): List<WineyardEntity> {
        return wineyardRepository.getWineyardsNearLocation(lat, lng, radiusKm)
    }

    suspend fun syncWineyards(): Result<Unit> {
        return wineyardRepository.syncWineyardsFromFirestore()
    }

    suspend fun getAllWineyardsPaginated(limit: Int, offset: Int): List<WineyardEntity> {
        return wineyardRepository.getAllWineyardsPaginated(limit, offset)
    }

    suspend fun validateWineyardOwnership(userId: String, wineyardId: String): Boolean {
        return try {
            val wineyard = getWineyardById(wineyardId).first()
            wineyard?.ownerId == userId
        } catch (e: Exception) {
            false
        }
    }
}