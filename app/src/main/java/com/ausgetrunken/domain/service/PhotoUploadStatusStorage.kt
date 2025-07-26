package com.ausgetrunken.domain.service

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ausgetrunken.domain.model.PhotoUploadInfo
import com.ausgetrunken.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Tracks upload status for each photo using DataStore
 */
class PhotoUploadStatusStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "PhotoUploadStatus"
        private const val DATASTORE_NAME = "photo_upload_status"
        
        private val Context.uploadStatusDataStore: DataStore<Preferences> by preferencesDataStore(
            name = DATASTORE_NAME
        )
    }
    
    private val dataStore = context.uploadStatusDataStore
    private val json = Json { ignoreUnknownKeys = true }
    
    @Serializable
    private data class SerializableUploadInfo(
        val localPath: String,
        val status: String,
        val cloudUrl: String? = null,
        val uploadProgress: Float = 0f,
        val lastAttempt: Long = System.currentTimeMillis(),
        val errorMessage: String? = null
    )
    
    /**
     * Update upload status for a photo
     */
    suspend fun updateUploadStatus(
        localPath: String, 
        status: UploadStatus,
        cloudUrl: String? = null,
        progress: Float = 0f,
        errorMessage: String? = null
    ) {
        Log.d(TAG, "Updating upload status for $localPath: $status")
        
        try {
            val key = stringPreferencesKey("upload_${localPath.hashCode()}")
            val uploadInfo = SerializableUploadInfo(
                localPath = localPath,
                status = status.name,
                cloudUrl = cloudUrl,
                uploadProgress = progress,
                lastAttempt = System.currentTimeMillis(),
                errorMessage = errorMessage
            )
            
            dataStore.edit { preferences ->
                preferences[key] = json.encodeToString(uploadInfo)
            }
            
            Log.d(TAG, "✅ Upload status updated: $status")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update upload status", e)
        }
    }
    
    /**
     * Get upload status for a specific photo
     */
    suspend fun getUploadStatus(localPath: String): PhotoUploadInfo {
        return try {
            val key = stringPreferencesKey("upload_${localPath.hashCode()}")
            val data = dataStore.data.first()
            val serialized = data[key]
            
            if (serialized != null) {
                val info = json.decodeFromString<SerializableUploadInfo>(serialized)
                PhotoUploadInfo(
                    localPath = info.localPath,
                    status = UploadStatus.valueOf(info.status),
                    cloudUrl = info.cloudUrl,
                    uploadProgress = info.uploadProgress,
                    lastAttempt = info.lastAttempt,
                    errorMessage = info.errorMessage
                )
            } else {
                // Default status for new photos
                PhotoUploadInfo(
                    localPath = localPath,
                    status = UploadStatus.PENDING
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get upload status for $localPath", e)
            PhotoUploadInfo(localPath = localPath, status = UploadStatus.PENDING)
        }
    }
    
    /**
     * Get upload status for multiple photos as Flow
     */
    fun getUploadStatusesFlow(localPaths: List<String>): Flow<Map<String, PhotoUploadInfo>> {
        return dataStore.data.map { preferences ->
            val statusMap = mutableMapOf<String, PhotoUploadInfo>()
            
            localPaths.forEach { path ->
                try {
                    val key = stringPreferencesKey("upload_${path.hashCode()}")
                    val serialized = preferences[key]
                    
                    val uploadInfo = if (serialized != null) {
                        val info = json.decodeFromString<SerializableUploadInfo>(serialized)
                        PhotoUploadInfo(
                            localPath = info.localPath,
                            status = UploadStatus.valueOf(info.status),
                            cloudUrl = info.cloudUrl,
                            uploadProgress = info.uploadProgress,
                            lastAttempt = info.lastAttempt,
                            errorMessage = info.errorMessage
                        )
                    } else {
                        PhotoUploadInfo(localPath = path, status = UploadStatus.PENDING)
                    }
                    
                    statusMap[path] = uploadInfo
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse upload status for $path", e)
                    statusMap[path] = PhotoUploadInfo(localPath = path, status = UploadStatus.PENDING)
                }
            }
            
            statusMap
        }
    }
    
    /**
     * Get all photos that need to be uploaded
     */
    suspend fun getPendingUploads(): List<PhotoUploadInfo> {
        return try {
            val allData = dataStore.data.first()
            val pendingUploads = mutableListOf<PhotoUploadInfo>()
            
            allData.asMap().forEach { (key, value) ->
                if (key.name.startsWith("upload_")) {
                    try {
                        val serialized = value as String
                        val info = json.decodeFromString<SerializableUploadInfo>(serialized)
                        val uploadInfo = PhotoUploadInfo(
                            localPath = info.localPath,
                            status = UploadStatus.valueOf(info.status),
                            cloudUrl = info.cloudUrl,
                            uploadProgress = info.uploadProgress,
                            lastAttempt = info.lastAttempt,
                            errorMessage = info.errorMessage
                        )
                        
                        if (uploadInfo.status == UploadStatus.PENDING || uploadInfo.status == UploadStatus.FAILED) {
                            pendingUploads.add(uploadInfo)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse upload info", e)
                    }
                }
            }
            
            Log.d(TAG, "Found ${pendingUploads.size} pending uploads")
            pendingUploads.sortedBy { it.lastAttempt } // Oldest first
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get pending uploads", e)
            emptyList()
        }
    }
    
    /**
     * Remove upload status for a photo (when photo is deleted)
     */
    suspend fun removeUploadStatus(localPath: String) {
        try {
            val key = stringPreferencesKey("upload_${localPath.hashCode()}")
            dataStore.edit { preferences ->
                preferences.remove(key)
            }
            Log.d(TAG, "✅ Removed upload status for $localPath")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove upload status", e)
        }
    }
    
    /**
     * Debug: Log all upload statuses
     */
    suspend fun logUploadStatuses() {
        try {
            val allData = dataStore.data.first()
            Log.d(TAG, "=== UPLOAD STATUS STATE ===")
            allData.asMap().forEach { (key, value) ->
                if (key.name.startsWith("upload_")) {
                    try {
                        val info = json.decodeFromString<SerializableUploadInfo>(value as String)
                        Log.d(TAG, "${info.localPath}: ${info.status} (cloud: ${info.cloudUrl})")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse upload status for key ${key.name}")
                    }
                }
            }
            Log.d(TAG, "=== END UPLOAD STATUS STATE ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log upload statuses", e)
        }
    }
}