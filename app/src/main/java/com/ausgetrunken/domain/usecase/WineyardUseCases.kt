package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.repository.WineyardRepository
import kotlinx.coroutines.flow.Flow

class GetAllWineyardsUseCase(
    private val wineyardRepository: WineyardRepository
) {
    operator fun invoke(): Flow<List<WineyardEntity>> {
        return wineyardRepository.getAllWineyards()
    }
}

class GetWineyardsByOwnerUseCase(
    private val wineyardRepository: WineyardRepository
) {
    operator fun invoke(ownerId: String): Flow<List<WineyardEntity>> {
        return wineyardRepository.getWineyardsByOwner(ownerId)
    }
}

class GetWineyardByIdUseCase(
    private val wineyardRepository: WineyardRepository
) {
    operator fun invoke(wineyardId: String): Flow<WineyardEntity?> {
        return wineyardRepository.getWineyardById(wineyardId)
    }
}

class CreateWineyardUseCase(
    private val wineyardRepository: WineyardRepository
) {
    suspend operator fun invoke(wineyard: WineyardEntity): Result<WineyardEntity> {
        return wineyardRepository.createWineyard(wineyard)
    }
}

class UpdateWineyardUseCase(
    private val wineyardRepository: WineyardRepository
) {
    suspend operator fun invoke(wineyard: WineyardEntity): Result<Unit> {
        return wineyardRepository.updateWineyard(wineyard)
    }
}

class DeleteWineyardUseCase(
    private val wineyardRepository: WineyardRepository
) {
    suspend operator fun invoke(wineyardId: String): Result<Unit> {
        return wineyardRepository.deleteWineyard(wineyardId)
    }
}

class GetNearbyWineyardsUseCase(
    private val wineyardRepository: WineyardRepository
) {
    suspend operator fun invoke(lat: Double, lng: Double, radiusKm: Double = 50.0): List<WineyardEntity> {
        return wineyardRepository.getWineyardsNearLocation(lat, lng, radiusKm)
    }
}

class SyncWineyardsUseCase(
    private val wineyardRepository: WineyardRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return wineyardRepository.syncWineyardsFromFirestore()
    }
}