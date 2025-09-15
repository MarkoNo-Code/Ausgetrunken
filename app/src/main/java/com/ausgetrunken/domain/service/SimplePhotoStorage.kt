package com.ausgetrunken.domain.service

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Simple, reliable photo storage using DataStore for metadata and file system for images.
 * This replaces the complex Room database approach with a proven, bulletproof solution.
 */
class SimplePhotoStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "SimplePhotoStorage"
        private const val DATASTORE_NAME = "wineyard_photos"
        
        private val Context.photoDataStore: DataStore<Preferences> by preferencesDataStore(
            name = DATASTORE_NAME
        )
    }
    
    private val dataStore = context.photoDataStore
    
    /**
     * Save a photo path for a wineyard - immediately persisted
     */
    suspend fun addPhotoPath(wineyardId: String, filePath: String) {
        Log.d(TAG, "Adding photo path for wineyard $wineyardId: $filePath")
        
        try {
            val key = stringSetPreferencesKey("photos_$wineyardId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                currentPaths.add(filePath)
                preferences[key] = currentPaths
                Log.d(TAG, "✅ Photo path saved. Total paths for wineyard: ${currentPaths.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save photo path", e)
            throw e
        }
    }
    
    /**
     * Get all photo paths for a wineyard - returns Flow for reactive UI
     */
    fun getPhotoPaths(wineyardId: String): Flow<List<String>> {
        Log.d(TAG, "Getting photo paths for wineyard: $wineyardId")
        
        val key = stringSetPreferencesKey("photos_$wineyardId")
        return dataStore.data
            .map { preferences ->
                val paths = preferences[key]?.toList() ?: emptyList()
                
                // Filter out paths to files that no longer exist
                val validPaths = paths.filter { path ->
                    val exists = File(path).exists()
                    Log.d(TAG, "Checking path $path: exists=$exists")
                    exists
                }
                
                Log.d(TAG, "Returning ${validPaths.size} valid photo paths for wineyard $wineyardId")
                validPaths.sortedByDescending { File(it).lastModified() } // Most recent first
            }
    }
    
    /**
     * Get photo paths synchronously (for background operations)
     */
    suspend fun getPhotoPathsSync(wineyardId: String): List<String> {
        return getPhotoPaths(wineyardId).first()
    }
    
    /**
     * Remove a photo path from a wineyard
     */
    suspend fun removePhotoPath(wineyardId: String, filePath: String) {
        Log.d(TAG, "Removing photo path for wineyard $wineyardId: $filePath")
        
        try {
            val key = stringSetPreferencesKey("photos_$wineyardId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                val removed = currentPaths.remove(filePath)
                preferences[key] = currentPaths
                Log.d(TAG, if (removed) "✅ Photo path removed" else "⚠️ Photo path not found")
            }
            
            // Also delete the actual file
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Physical file deletion: $deleted")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete physical file: $filePath", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove photo path", e)
            throw e
        }
    }
    
    /**
     * Clear all photos for a wineyard
     */
    suspend fun clearAllPhotos(wineyardId: String) {
        Log.d(TAG, "Clearing all photos for wineyard: $wineyardId")
        
        try {
            // Get current paths to delete physical files
            val currentPaths = getPhotoPathsSync(wineyardId)
            
            // Clear from DataStore
            val key = stringSetPreferencesKey("photos_$wineyardId")
            dataStore.edit { preferences ->
                preferences.remove(key)
            }
            
            // Delete physical files
            currentPaths.forEach { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete file: $path", e)
                }
            }
            
            Log.d(TAG, "✅ Cleared ${currentPaths.size} photos for wineyard $wineyardId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear photos", e)
            throw e
        }
    }
    
    /**
     * Get total photo count for a wineyard
     */
    suspend fun getPhotoCount(wineyardId: String): Int {
        return getPhotoPathsSync(wineyardId).size
    }
    
    // ========== WINE PHOTO METHODS ==========

    /**
     * Save a photo path for a wine - immediately persisted
     */
    suspend fun addWinePhoto(wineId: String, filePath: String) {
        Log.d(TAG, "Adding wine photo for wine $wineId: $filePath")

        try {
            val key = stringSetPreferencesKey("wine_photos_$wineId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                currentPaths.add(filePath)
                preferences[key] = currentPaths
                Log.d(TAG, "✅ Wine photo path saved. Total paths for wine: ${currentPaths.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save wine photo path", e)
            throw e
        }
    }

    /**
     * Get all photo paths for a wine - returns Flow for reactive UI
     */
    fun getWinePhotos(wineId: String): Flow<List<String>> {
        Log.d(TAG, "Getting wine photo paths for wine: $wineId")

        val key = stringSetPreferencesKey("wine_photos_$wineId")
        return dataStore.data
            .map { preferences ->
                val paths = preferences[key]?.toList() ?: emptyList()

                // Filter out paths to files that no longer exist
                val validPaths = paths.filter { path ->
                    val exists = File(path).exists()
                    Log.d(TAG, "Checking wine photo path $path: exists=$exists")
                    exists
                }

                Log.d(TAG, "Returning ${validPaths.size} valid wine photo paths for wine $wineId")
                validPaths.sortedByDescending { File(it).lastModified() } // Most recent first
            }
    }

    /**
     * Get wine photo paths synchronously (for background operations)
     */
    suspend fun getWinePhotosSync(wineId: String): List<String> {
        return getWinePhotos(wineId).first()
    }

    /**
     * Remove a photo path from a wine
     */
    suspend fun removeWinePhoto(wineId: String, filePath: String) {
        Log.d(TAG, "Removing wine photo for wine $wineId: $filePath")

        try {
            val key = stringSetPreferencesKey("wine_photos_$wineId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                val removed = currentPaths.remove(filePath)
                preferences[key] = currentPaths
                Log.d(TAG, if (removed) "✅ Wine photo path removed" else "⚠️ Wine photo path not found")
            }

            // Also delete the actual file
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Physical wine photo file deletion: $deleted")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete physical wine photo file: $filePath", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove wine photo path", e)
            throw e
        }
    }

    /**
     * Clear all photos for a wine
     */
    suspend fun clearWinePhotos(wineId: String) {
        Log.d(TAG, "Clearing all photos for wine: $wineId")

        try {
            // Get current paths to delete physical files
            val currentPaths = getWinePhotosSync(wineId)

            // Clear from DataStore
            val key = stringSetPreferencesKey("wine_photos_$wineId")
            dataStore.edit { preferences ->
                preferences.remove(key)
            }

            // Delete physical files
            currentPaths.forEach { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete wine photo file: $path", e)
                }
            }

            Log.d(TAG, "✅ Cleared ${currentPaths.size} photos for wine $wineId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear wine photos", e)
            throw e
        }
    }

    /**
     * Get total photo count for a wine
     */
    suspend fun getWinePhotoCount(wineId: String): Int {
        return getWinePhotosSync(wineId).size
    }

    /**
     * Debug: Log current storage state
     */
    suspend fun logStorageState() {
        try {
            val allData = dataStore.data.first()
            Log.d(TAG, "=== PHOTO STORAGE STATE ===")
            allData.asMap().forEach { (key, value) ->
                when {
                    key.name.startsWith("photos_") -> {
                        val wineyardId = key.name.removePrefix("photos_")
                        val paths = value as? Set<String> ?: emptySet()
                        Log.d(TAG, "Wineyard $wineyardId: ${paths.size} photos")
                        paths.forEach { path ->
                            val exists = File(path).exists()
                            Log.d(TAG, "  - $path (exists: $exists)")
                        }
                    }
                    key.name.startsWith("wine_photos_") -> {
                        val wineId = key.name.removePrefix("wine_photos_")
                        val paths = value as? Set<String> ?: emptySet()
                        Log.d(TAG, "Wine $wineId: ${paths.size} photos")
                        paths.forEach { path ->
                            val exists = File(path).exists()
                            Log.d(TAG, "  - $path (exists: $exists)")
                        }
                    }
                }
            }
            Log.d(TAG, "=== END STORAGE STATE ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log storage state", e)
        }
    }
}