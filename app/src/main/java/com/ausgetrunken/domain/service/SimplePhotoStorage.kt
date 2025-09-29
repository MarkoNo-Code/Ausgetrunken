package com.ausgetrunken.domain.service

import android.content.Context
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
        private const val DATASTORE_NAME = "winery_photos"
        
        private val Context.photoDataStore: DataStore<Preferences> by preferencesDataStore(
            name = DATASTORE_NAME
        )
    }
    
    private val dataStore = context.photoDataStore
    
    /**
     * Save a photo path for a vineyard - immediately persisted
     */
    suspend fun addPhotoPath(wineryId: String, filePath: String) {

        try {
            val key = stringSetPreferencesKey("photos_$wineryId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                currentPaths.add(filePath)
                preferences[key] = currentPaths
            }
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Get all photo paths for a vineyard - returns Flow for reactive UI
     */
    fun getPhotoPaths(wineryId: String): Flow<List<String>> {

        val key = stringSetPreferencesKey("photos_$wineryId")
        return dataStore.data
            .map { preferences ->
                val paths = preferences[key]?.toList() ?: emptyList()
                
                // Filter out paths to files that no longer exist
                val validPaths = paths.filter { path ->
                    val exists = File(path).exists()
                    exists
                }
                
                validPaths.sortedByDescending { File(it).lastModified() } // Most recent first
            }
    }
    
    /**
     * Get photo paths synchronously (for background operations)
     */
    suspend fun getPhotoPathsSync(wineryId: String): List<String> {
        return getPhotoPaths(wineryId).first()
    }
    
    /**
     * Remove a photo path from a vineyard
     */
    suspend fun removePhotoPath(wineryId: String, filePath: String) {

        try {
            val key = stringSetPreferencesKey("photos_$wineryId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                val removed = currentPaths.remove(filePath)
                preferences[key] = currentPaths
            }
            
            // Also delete the actual file
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                }
            } catch (e: Exception) {
            }
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Clear all photos for a vineyard
     */
    suspend fun clearAllPhotos(wineryId: String) {

        try {
            // Get current paths to delete physical files
            val currentPaths = getPhotoPathsSync(wineryId)

            // Clear from DataStore
            val key = stringSetPreferencesKey("photos_$wineryId")
            dataStore.edit { preferences ->
                preferences.remove(key)
            }
            
            // Delete physical files
            currentPaths.forEach { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                }
            }
            
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Get total photo count for a vineyard
     */
    suspend fun getPhotoCount(wineryId: String): Int {
        return getPhotoPathsSync(wineryId).size
    }
    
    // ========== WINE PHOTO METHODS ==========

    /**
     * Save a photo path for a wine - immediately persisted
     */
    suspend fun addWinePhoto(wineId: String, filePath: String) {

        try {
            val key = stringSetPreferencesKey("wine_photos_$wineId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                currentPaths.add(filePath)
                preferences[key] = currentPaths
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Get all photo paths for a wine - returns Flow for reactive UI
     */
    fun getWinePhotos(wineId: String): Flow<List<String>> {

        val key = stringSetPreferencesKey("wine_photos_$wineId")
        return dataStore.data
            .map { preferences ->
                val paths = preferences[key]?.toList() ?: emptyList()

                // Filter out paths to files that no longer exist
                val validPaths = paths.filter { path ->
                    val exists = File(path).exists()
                    exists
                }

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

        try {
            val key = stringSetPreferencesKey("wine_photos_$wineId")
            dataStore.edit { preferences ->
                val currentPaths = preferences[key]?.toMutableSet() ?: mutableSetOf()
                val removed = currentPaths.remove(filePath)
                preferences[key] = currentPaths
            }

            // Also delete the actual file
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                }
            } catch (e: Exception) {
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Clear all photos for a wine
     */
    suspend fun clearWinePhotos(wineId: String) {

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
                }
            }

        } catch (e: Exception) {
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
            allData.asMap().forEach { (key, value) ->
                when {
                    key.name.startsWith("photos_") -> {
                        val wineryId = key.name.removePrefix("photos_")
                        val paths = value as? Set<String> ?: emptySet()
                        paths.forEach { path ->
                            val exists = File(path).exists()
                        }
                    }
                    key.name.startsWith("wine_photos_") -> {
                        val wineId = key.name.removePrefix("wine_photos_")
                        val paths = value as? Set<String> ?: emptySet()
                        paths.forEach { path ->
                            val exists = File(path).exists()
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }
}