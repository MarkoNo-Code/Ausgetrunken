package com.ausgetrunken.domain.service

import android.util.Log
import com.ausgetrunken.data.local.dao.WineryPhotoDao
import com.ausgetrunken.data.local.AusgetrunkenDatabase

class DatabaseInspectionService(
    private val database: AusgetrunkenDatabase,
    private val wineryPhotoDao: WineryPhotoDao
) {
    companion object {
        private const val TAG = "DatabaseInspection"
    }

    suspend fun inspectDatabase() {
        Log.d(TAG, "=== DATABASE INSPECTION START ===")
        
        try {
            // Use DAO to check if photos exist - simpler approach
            val totalPhotoCount = wineryPhotoDao.getPhotoCountForWinery("dummy") 
            Log.d(TAG, "Database connection works, can query photo table")
            
            // Check all photos in database
            try {
                val allPhotos = wineryPhotoDao.getPhotosByUploadStatus(
                    com.ausgetrunken.data.local.entities.PhotoUploadStatus.LOCAL_ONLY
                )
                Log.d(TAG, "Found ${allPhotos.size} LOCAL_ONLY photos")
                allPhotos.forEach { photo ->
                    Log.d(TAG, "Photo: id=${photo.id}, wineryId=${photo.wineryId}, localPath=${photo.localPath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying photos by status", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during database inspection", e)
        }
        
        Log.d(TAG, "=== DATABASE INSPECTION END ===")
    }
    
    suspend fun inspectWineryPhotos(wineryId: String) {
        Log.d(TAG, "=== INSPECTING PHOTOS FOR WINERY: $wineryId ===")
        
        try {
            // Use DAO to get photos
            val photos = wineryPhotoDao.getPhotosByWineryIdSync(wineryId)
            Log.d(TAG, "Found ${photos.size} photos for winery $wineryId")
            
            photos.forEach { photo ->
                Log.d(TAG, "Photo: $photo")
                
                // Check if local file exists
                photo.localPath?.let { path ->
                    val file = java.io.File(path)
                    Log.d(TAG, "  Local file exists: ${file.exists()}, size: ${file.length()}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting winery photos", e)
        }
    }
}