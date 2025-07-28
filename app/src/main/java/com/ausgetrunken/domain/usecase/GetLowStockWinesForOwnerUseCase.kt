package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService

class GetLowStockWinesForOwnerUseCase(
    private val wineService: WineService,
    private val wineyardService: WineyardService
) {
    suspend operator fun invoke(ownerId: String): List<WineEntity> {
        return try {
            println("🔍 GetLowStockWinesForOwnerUseCase: Getting low stock wines for owner: $ownerId")
            
            // First, get all wineyards owned by this user
            val ownerWineyards = wineyardService.getWineyardsByOwnerRemoteFirst(ownerId)
            println("🔍 GetLowStockWinesForOwnerUseCase: Found ${ownerWineyards.size} wineyards for owner")
            
            // Then, get all wines from all those wineyards and filter by low stock
            val allLowStockWines = mutableListOf<WineEntity>()
            
            for (wineyard in ownerWineyards) {
                println("🔍 GetLowStockWinesForOwnerUseCase: Checking wineyard: ${wineyard.name} (${wineyard.id})")
                
                val wineyardWines = wineService.getWinesByWineyardFromSupabase(wineyard.id)
                println("🔍 GetLowStockWinesForOwnerUseCase: Found ${wineyardWines.size} wines in wineyard ${wineyard.name}")
                
                val lowStockWines = wineyardWines.filter { wine ->
                    val isLowStock = wine.stockQuantity <= wine.lowStockThreshold
                    if (isLowStock) {
                        println("🔍 GetLowStockWinesForOwnerUseCase: Low stock wine found: '${wine.name}' in wineyard '${wineyard.name}' - Stock: ${wine.stockQuantity}, Threshold: ${wine.lowStockThreshold}")
                    }
                    isLowStock
                }
                
                allLowStockWines.addAll(lowStockWines)
            }
            
            println("🔍 GetLowStockWinesForOwnerUseCase: Total low stock wines found across all wineyards: ${allLowStockWines.size}")
            allLowStockWines.forEach { wine ->
                println("🔍 GetLowStockWinesForOwnerUseCase: - '${wine.name}' (${wine.stockQuantity}/${wine.lowStockThreshold})")
            }
            
            allLowStockWines
        } catch (e: Exception) {
            println("❌ GetLowStockWinesForOwnerUseCase: Error getting low stock wines for owner: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}