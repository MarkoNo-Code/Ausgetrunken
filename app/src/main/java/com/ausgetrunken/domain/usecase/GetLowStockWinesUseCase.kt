package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.service.WineService

class GetLowStockWinesUseCase(
    private val wineService: WineService
) {
    suspend operator fun invoke(wineyardId: String, threshold: Int = 20): List<WineEntity> {
        return try {
            println("🔍 GetLowStockWinesUseCase: Checking low stock wines for wineyard: $wineyardId")
            // Get wines from Supabase for the wineyard and filter by low stock
            val wines = wineService.getWinesByWineyardFromSupabase(wineyardId)
            println("🔍 GetLowStockWinesUseCase: Found ${wines.size} wines total")
            
            val lowStockWines = wines.filter { wine ->
                // Use each wine's individual lowStockThreshold instead of the global threshold parameter
                val isLowStock = wine.stockQuantity <= wine.lowStockThreshold
                println("🔍 GetLowStockWinesUseCase: Wine '${wine.name}' - Stock: ${wine.stockQuantity}, Threshold: ${wine.lowStockThreshold}, Low Stock: $isLowStock")
                isLowStock
            }
            
            println("🔍 GetLowStockWinesUseCase: Found ${lowStockWines.size} low stock wines")
            lowStockWines.forEach { wine ->
                println("🔍 GetLowStockWinesUseCase: Low stock wine: '${wine.name}' (${wine.stockQuantity}/${wine.lowStockThreshold})")
            }
            
            lowStockWines
        } catch (e: Exception) {
            println("❌ GetLowStockWinesUseCase: Error getting low stock wines: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}