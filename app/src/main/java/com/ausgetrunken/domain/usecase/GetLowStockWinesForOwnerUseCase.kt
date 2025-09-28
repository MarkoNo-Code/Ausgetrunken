package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineryService

class GetLowStockWinesForOwnerUseCase(
    private val wineService: WineService,
    private val wineryService: WineryService
) {
    suspend operator fun invoke(ownerId: String): List<WineEntity> {
        return try {
            println("🔍 GetLowStockWinesForOwnerUseCase: Getting low stock wines for owner: $ownerId")
            
            // First, get all wineries owned by this user
            val ownerWineries = wineryService.getWinerysByOwnerRemoteFirst(ownerId)
            println("🔍 GetLowStockWinesForOwnerUseCase: Found ${ownerWineries.size} wineries for owner")
            
            // Then, get all wines from all those wineries and filter by low stock
            val allLowStockWines = mutableListOf<WineEntity>()
            
            for (winery in ownerWineries) {
                println("🔍 GetLowStockWinesForOwnerUseCase: Checking winery: ${winery.name} (${winery.id})")

                val wineryWines = wineService.getWinesByWineryFromSupabase(winery.id)
                println("🔍 GetLowStockWinesForOwnerUseCase: Found ${wineryWines.size} wines in winery ${winery.name}")
                
                val lowStockWines = wineryWines.filter { wine ->
                    val isLowStock = wine.stockQuantity <= wine.lowStockThreshold
                    if (isLowStock) {
                        println("🔍 GetLowStockWinesForOwnerUseCase: Low stock wine found: '${wine.name}' in winery '${winery.name}' - Stock: ${wine.stockQuantity}, Threshold: ${wine.lowStockThreshold}")
                    }
                    isLowStock
                }
                
                allLowStockWines.addAll(lowStockWines)
            }
            
            println("🔍 GetLowStockWinesForOwnerUseCase: Total low stock wines found across all wineries: ${allLowStockWines.size}")
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