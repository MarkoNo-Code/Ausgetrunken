package com.ausgetrunken.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ausgetrunken.data.local.entities.PhotoUploadStatus

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            Gson().fromJson<List<String>>(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    @TypeConverter
    fun fromPhotoUploadStatus(status: PhotoUploadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toPhotoUploadStatus(status: String): PhotoUploadStatus {
        return try {
            PhotoUploadStatus.valueOf(status)
        } catch (e: Exception) {
            PhotoUploadStatus.LOCAL_ONLY
        }
    }
}