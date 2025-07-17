package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.dao.WineDao
import com.ausgetrunken.data.local.entities.WineEntity

class GetLowStockWinesUseCase(
    private val wineDao: WineDao
) {
    suspend operator fun invoke(): List<WineEntity> {
        return wineDao.getLowStockWines()
    }
}