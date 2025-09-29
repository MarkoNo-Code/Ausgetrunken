package com.ausgetrunken.domain.service

import android.content.Context
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
            
        } catch (e: Exception) {
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
                    }
                }
            }
            
            pendingUploads.sortedBy { it.lastAttempt } // Oldest first
        } catch (e: Exception) {
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
        } catch (e: Exception) {
        }
    }
    
    /**
     * Debug: Log all upload statuses
     */
    suspend fun logUploadStatuses() {
        try {
            val allData = dataStore.data.first()
            allData.asMap().forEach { (key, value) ->
                if (key.name.startsWith("upload_")) {
                    try {
                        val info = json.decodeFromString<SerializableUploadInfo>(value as String)
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }
    }
}