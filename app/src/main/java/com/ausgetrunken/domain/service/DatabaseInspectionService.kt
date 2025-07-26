package com.ausgetrunken.domain.service

import android.util.Log
import com.ausgetrunken.data.local.dao.WineyardPhotoDao
import com.ausgetrunken.data.local.AusgetrunkenDatabase

class DatabaseInspectionService(
    private val database: AusgetrunkenDatabase,
    private val wineyardPhotoDao: WineyardPhotoDao
) {
    companion object {
        private const val TAG = "DatabaseInspection"
    }

    suspend fun inspectDatabase() {
        Log.d(TAG, "=== DATABASE INSPECTION START ===")
        
        try {
            // Use DAO to check if photos exist - simpler approach
            val totalPhotoCount = wineyardPhotoDao.getPhotoCountForWineyard("dummy") 
            Log.d(TAG, "Database connection works, can query photo table")
            
            // Check all photos in database
            try {
                val allPhotos = wineyardPhotoDao.getPhotosByUploadStatus(
                    com.ausgetrunken.data.local.entities.PhotoUploadStatus.LOCAL_ONLY
                )
                Log.d(TAG, "Found ${allPhotos.size} LOCAL_ONLY photos")
                allPhotos.forEach { photo ->
                    Log.d(TAG, "Photo: id=${photo.id}, wineyardId=${photo.wineyardId}, localPath=${photo.localPath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying photos by status", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during database inspection", e)
        }
        
        Log.d(TAG, "=== DATABASE INSPECTION END ===")
    }
    
    suspend fun inspectWineyardPhotos(wineyardId: String) {
        Log.d(TAG, "=== INSPECTING PHOTOS FOR WINEYARD: $wineyardId ===")
        
        try {
            // Use DAO to get photos
            val photos = wineyardPhotoDao.getPhotosByWineyardIdSync(wineyardId)
            Log.d(TAG, "Found ${photos.size} photos for wineyard $wineyardId")
            
            photos.forEach { photo ->
                Log.d(TAG, "Photo: $photo")
                
                // Check if local file exists
                photo.localPath?.let { path ->
                    val file = java.io.File(path)
                    Log.d(TAG, "  Local file exists: ${file.exists()}, size: ${file.length()}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting wineyard photos", e)
        }
    }
}