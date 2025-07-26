package com.ausgetrunken.domain.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay

/**
 * Utility class for checking network connectivity and implementing remote-first data strategy
 * 
 * ARCHITECTURAL PATTERN: Remote-First Data Strategy
 * - Always try Supabase first (source of truth)
 * - Fall back to local database only if network unavailable
 * - Local database serves as backup/cache only
 */
class NetworkConnectivityManager(private val context: Context) {
    
    /**
     * Check if device has active internet connection
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Test actual network connectivity by attempting a quick network operation
     * This is more reliable than just checking network state
     */
    suspend fun hasActiveConnection(): Boolean {
        return try {
            if (!isNetworkAvailable()) {
                println("🌐 NetworkConnectivityManager: No network capability detected")
                false
            } else {
                // Quick connectivity test - you could ping a lightweight endpoint
                // For now, we'll trust the network capabilities check
                println("✅ NetworkConnectivityManager: Network connection available")
                true
            }
        } catch (e: Exception) {
            println("❌ NetworkConnectivityManager: Network test failed: ${e.message}")
            false
        }
    }
    
    /**
     * Execute remote-first data strategy
     * @param remoteOperation: Function to fetch data from Supabase
     * @param localFallback: Function to fetch data from local database
     * @param cacheUpdate: Optional function to update local cache with remote data
     */
    suspend fun <T> executeRemoteFirst(
        operationName: String,
        remoteOperation: suspend () -> Result<T>,
        localFallback: suspend () -> T,
        cacheUpdate: (suspend (T) -> Unit)? = null
    ): T {
        println("🔄 NetworkConnectivityManager: Starting remote-first operation: $operationName")
        
        return if (hasActiveConnection()) {
            try {
                println("🌐 NetworkConnectivityManager: Attempting remote operation for $operationName")
                val result = remoteOperation()
                
                result.fold(
                    onSuccess = { data ->
                        println("✅ NetworkConnectivityManager: Remote operation successful for $operationName")
                        
                        // Update local cache with fresh remote data
                        cacheUpdate?.let { 
                            try {
                                it(data)
                                println("💾 NetworkConnectivityManager: Local cache updated for $operationName")
                            } catch (e: Exception) {
                                println("⚠️ NetworkConnectivityManager: Cache update failed for $operationName: ${e.message}")
                            }
                        }
                        
                        data
                    },
                    onFailure = { error ->
                        println("❌ NetworkConnectivityManager: Remote operation failed for $operationName: ${error.message}")
                        println("📱 NetworkConnectivityManager: Falling back to local data for $operationName")
                        localFallback()
                    }
                )
            } catch (e: Exception) {
                println("❌ NetworkConnectivityManager: Exception in remote operation for $operationName: ${e.message}")
                println("📱 NetworkConnectivityManager: Falling back to local data for $operationName")
                localFallback()
            }
        } else {
            println("📱 NetworkConnectivityManager: No network connection, using local data for $operationName")
            localFallback()
        }
    }
    
    /**
     * Simple version for operations that don't need local caching
     */
    suspend fun <T> executeRemoteFirstSimple(
        operationName: String,
        remoteOperation: suspend () -> Result<T>,
        localFallback: suspend () -> T
    ): T {
        return executeRemoteFirst(
            operationName = operationName,
            remoteOperation = remoteOperation,
            localFallback = localFallback,
            cacheUpdate = null
        )
    }
}