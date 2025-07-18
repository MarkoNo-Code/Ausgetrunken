package com.ausgetrunken.domain.service

import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.data.repository.WineRepository
import kotlinx.coroutines.flow.Flow

class WineService(
    private val wineRepository: WineRepository
) {
    fun getAllWines(): Flow<List<WineEntity>> {
        return wineRepository.getAllWines()
    }

    fun getWinesByWineyard(wineyardId: String): Flow<List<WineEntity>> {
        return wineRepository.getWinesByWineyard(wineyardId)
    }

    fun getWineById(wineId: String): Flow<WineEntity?> {
        return wineRepository.getWineById(wineId)
    }

    suspend fun createWine(wine: WineEntity): Result<WineEntity> {
        return wineRepository.createWine(wine)
    }

    suspend fun updateWine(wine: WineEntity): Result<Unit> {
        return wineRepository.updateWine(wine)
    }

    suspend fun deleteWine(wineId: String): Result<Unit> {
        return wineRepository.deleteWine(wineId)
    }

    suspend fun syncWines(): Result<Unit> {
        return wineRepository.syncWinesFromSupabase()
    }

    suspend fun getWinesByWineyardFromSupabase(wineyardId: String): List<WineEntity> {
        return wineRepository.getWinesByWineyardFromSupabase(wineyardId)
    }

    suspend fun getAllWinesPaginated(limit: Int, offset: Int): List<WineEntity> {
        return wineRepository.getAllWinesPaginated(limit, offset)
    }
}