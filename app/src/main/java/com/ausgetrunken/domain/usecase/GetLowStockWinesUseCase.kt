package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.dao.WineDao
import com.ausgetrunken.data.local.entities.WineEntity

class GetLowStockWinesUseCase(
    private val wineDao: WineDao
) {
    suspend operator fun invoke(threshold: Int = 20): List<WineEntity> {
        return wineDao.getLowStockWines(threshold)
    }
}