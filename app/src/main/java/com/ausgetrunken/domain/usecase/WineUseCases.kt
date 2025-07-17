package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.data.repository.WineRepository
import kotlinx.coroutines.flow.Flow

class GetAllWinesUseCase(
    private val wineRepository: WineRepository
) {
    operator fun invoke(): Flow<List<WineEntity>> {
        return wineRepository.getAllWines()
    }
}

class GetWinesByWineyardUseCase(
    private val wineRepository: WineRepository
) {
    operator fun invoke(wineyardId: String): Flow<List<WineEntity>> {
        return wineRepository.getWinesByWineyard(wineyardId)
    }
}

class GetWineByIdUseCase(
    private val wineRepository: WineRepository
) {
    operator fun invoke(wineId: String): Flow<WineEntity?> {
        return wineRepository.getWineById(wineId)
    }
}

class CreateWineUseCase(
    private val wineRepository: WineRepository
) {
    suspend operator fun invoke(wine: WineEntity): Result<WineEntity> {
        return wineRepository.createWine(wine)
    }
}

class UpdateWineUseCase(
    private val wineRepository: WineRepository
) {
    suspend operator fun invoke(wine: WineEntity): Result<Unit> {
        return wineRepository.updateWine(wine)
    }
}

class DeleteWineUseCase(
    private val wineRepository: WineRepository
) {
    suspend operator fun invoke(wineId: String): Result<Unit> {
        return wineRepository.deleteWine(wineId)
    }
}

class SyncWinesUseCase(
    private val wineRepository: WineRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return wineRepository.syncWinesFromSupabase()
    }
}