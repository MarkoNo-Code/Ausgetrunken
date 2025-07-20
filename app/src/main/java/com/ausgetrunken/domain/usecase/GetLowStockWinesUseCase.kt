package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.service.WineService

class GetLowStockWinesUseCase(
    private val wineService: WineService
) {
    suspend operator fun invoke(wineyardId: String, threshold: Int = 20): List<WineEntity> {
        return try {
            // Get wines from Supabase for the wineyard and filter by low stock
            val wines = wineService.getWinesByWineyardFromSupabase(wineyardId)
            wines.filter { wine ->
                wine.stockQuantity <= threshold
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}