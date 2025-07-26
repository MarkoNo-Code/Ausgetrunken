package com.ausgetrunken.domain.util

import com.ausgetrunken.data.repository.WineyardRepository
import com.ausgetrunken.data.repository.WineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Utility class for testing remote-first data strategy
 * This can be called from ViewModels to test the new implementation
 */
class RemoteFirstTestUtils(
    private val wineyardRepository: WineyardRepository,
    private val wineRepository: WineRepository
) {
    
    /**
     * Test remote-first getAllWineyards functionality
     * This will show detailed logs of the remote-first process
     */
    suspend fun testGetAllWineyardsRemoteFirst(): List<com.ausgetrunken.data.local.entities.WineyardEntity> {
        println("🧪 RemoteFirstTestUtils: Testing getAllWineyardsRemoteFirst()")
        println("🧪 This will show detailed logs of the remote-first data strategy")
        println("🧪 Expected behavior:")
        println("   1. Check network connectivity")
        println("   2. Try Supabase first")
        println("   3. Update local cache with fresh data") 
        println("   4. Return Supabase data if successful")
        println("   5. Fall back to local data if Supabase fails")
        println("========================================")
        
        val result = wineyardRepository.getAllWineyardsRemoteFirst()
        
        println("========================================")
        println("🧪 RemoteFirstTestUtils: Test completed!")
        println("🧪 Result: Found ${result.size} wineyards")
        result.forEach { wineyard ->
            println("   📍 ${wineyard.name} (ID: ${wineyard.id})")
        }
        
        return result
    }
    
    /**
     * Test remote-first getWineyardById functionality
     */
    suspend fun testGetWineyardByIdRemoteFirst(wineyardId: String): com.ausgetrunken.data.local.entities.WineyardEntity? {
        println("🧪 RemoteFirstTestUtils: Testing getWineyardByIdRemoteFirst($wineyardId)")
        println("========================================")
        
        val result = wineyardRepository.getWineyardByIdRemoteFirst(wineyardId)
        
        println("========================================")
        println("🧪 RemoteFirstTestUtils: Single wineyard test completed!")
        println("🧪 Result: ${result?.name ?: "Wineyard not found"}")
        
        return result
    }
    
    /**
     * Test remote-first getWineyardsByOwner functionality
     */
    suspend fun testGetWineyardsByOwnerRemoteFirst(ownerId: String): List<com.ausgetrunken.data.local.entities.WineyardEntity> {
        println("🧪 RemoteFirstTestUtils: Testing getWineyardsByOwnerRemoteFirst($ownerId)")
        println("========================================")
        
        val result = wineyardRepository.getWineyardsByOwnerRemoteFirst(ownerId)
        
        println("========================================")
        println("🧪 RemoteFirstTestUtils: Owner wineyards test completed!")
        println("🧪 Result: Found ${result.size} wineyards for owner $ownerId")
        
        return result
    }
    
    /**
     * Test remote-first getAllWines functionality
     */
    suspend fun testGetAllWinesRemoteFirst(): List<com.ausgetrunken.data.local.entities.WineEntity> {
        println("🧪 RemoteFirstTestUtils: Testing getAllWinesRemoteFirst()")
        println("🧪 This will show detailed logs of the remote-first data strategy for wines")
        println("🧪 Expected behavior:")
        println("   1. Check network connectivity")
        println("   2. Try Supabase first")
        println("   3. Update local cache with fresh data") 
        println("   4. Return Supabase data if successful")
        println("   5. Fall back to local data if Supabase fails")
        println("========================================")
        
        val result = wineRepository.getAllWinesRemoteFirst()
        
        println("========================================")
        println("🧪 RemoteFirstTestUtils: Wine test completed!")
        println("🧪 Result: Found ${result.size} wines")
        result.take(5).forEach { wine ->
            println("   🍷 ${wine.name} (ID: ${wine.id}, Wineyard: ${wine.wineyardId})")
        }
        if (result.size > 5) {
            println("   ... and ${result.size - 5} more wines")
        }
        
        return result
    }
    
    /**
     * Test remote-first getWinesByWineyard functionality
     */
    suspend fun testGetWinesByWineyardRemoteFirst(wineyardId: String): List<com.ausgetrunken.data.local.entities.WineEntity> {
        println("🧪 RemoteFirstTestUtils: Testing getWinesByWineyardRemoteFirst($wineyardId)")
        println("========================================")
        
        val result = wineRepository.getWinesByWineyardRemoteFirst(wineyardId)
        
        println("========================================")
        println("🧪 RemoteFirstTestUtils: Wineyard wines test completed!")
        println("🧪 Result: Found ${result.size} wines for wineyard $wineyardId")
        
        return result
    }
    
    /**
     * Test remote-first getWineById functionality
     */
    suspend fun testGetWineByIdRemoteFirst(wineId: String): com.ausgetrunken.data.local.entities.WineEntity? {
        println("🧪 RemoteFirstTestUtils: Testing getWineByIdRemoteFirst($wineId)")
        println("========================================")
        
        val result = wineRepository.getWineByIdRemoteFirst(wineId)
        
        println("========================================")
        println("🧪 RemoteFirstTestUtils: Single wine test completed!")
        println("🧪 Result: ${result?.name ?: "Wine not found"}")
        
        return result
    }
    
    /**
     * Run all remote-first tests
     */
    suspend fun runAllTests() {
        println("🧪🧪🧪 RemoteFirstTestUtils: STARTING COMPREHENSIVE REMOTE-FIRST TESTS 🧪🧪🧪")
        
        try {
            // Test 1: Get all wineyards
            println("\n📋 TEST 1: Get All Wineyards")
            val allWineyards = testGetAllWineyardsRemoteFirst()
            
            if (allWineyards.isNotEmpty()) {
                // Test 2: Get specific wineyard
                val firstWineyard = allWineyards.first()
                println("\n📋 TEST 2: Get Specific Wineyard")
                testGetWineyardByIdRemoteFirst(firstWineyard.id)
                
                // Test 3: Get wineyards by owner
                println("\n📋 TEST 3: Get Wineyards by Owner")
                testGetWineyardsByOwnerRemoteFirst(firstWineyard.ownerId)
            }
            
            // Test 4: Get all wines
            println("\n📋 TEST 4: Get All Wines")
            val allWines = testGetAllWinesRemoteFirst()
            
            if (allWines.isNotEmpty()) {
                // Test 5: Get specific wine
                val firstWine = allWines.first()
                println("\n📋 TEST 5: Get Specific Wine")
                testGetWineByIdRemoteFirst(firstWine.id)
                
                // Test 6: Get wines by wineyard
                println("\n📋 TEST 6: Get Wines by Wineyard")
                testGetWinesByWineyardRemoteFirst(firstWine.wineyardId)
            }
            
            println("\n✅✅✅ ALL REMOTE-FIRST TESTS COMPLETED SUCCESSFULLY ✅✅✅")
            
        } catch (e: Exception) {
            println("\n❌❌❌ REMOTE-FIRST TESTS FAILED: ${e.message} ❌❌❌")
            e.printStackTrace()
        }
    }
}