package com.ausgetrunken.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class TokenStorage(context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME, 
        Context.MODE_PRIVATE
    )
    
    private val _isLoggedIn = MutableStateFlow(isTokenValid())
    val isLoggedIn: Flow<Boolean> = _isLoggedIn.asStateFlow()
    
    fun saveLoginSession(accessToken: String, refreshToken: String, userId: String) {
        val expirationTime = System.currentTimeMillis() + TOKEN_EXPIRATION_DURATION
        
        preferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_USER_ID, userId)
            putLong(KEY_EXPIRATION_TIME, expirationTime)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            apply()
        }
        
        _isLoggedIn.value = true
    }
    
    fun getAccessToken(): String? {
        return if (isTokenValid()) {
            preferences.getString(KEY_ACCESS_TOKEN, null)
        } else {
            clearSession()
            null
        }
    }
    
    fun getRefreshToken(): String? {
        return if (isTokenValid()) {
            preferences.getString(KEY_REFRESH_TOKEN, null)
        } else {
            clearSession()
            null
        }
    }
    
    fun getUserId(): String? {
        return if (isTokenValid()) {
            preferences.getString(KEY_USER_ID, null)
        } else {
            clearSession()
            null
        }
    }
    
    fun isTokenValid(): Boolean {
        val expirationTime = preferences.getLong(KEY_EXPIRATION_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        return currentTime < expirationTime && hasTokens()
    }
    
    private fun hasTokens(): Boolean {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
        val userId = preferences.getString(KEY_USER_ID, null)
        
        return !accessToken.isNullOrEmpty() && 
               !refreshToken.isNullOrEmpty() && 
               !userId.isNullOrEmpty()
    }
    
    fun refreshLoginSession(newAccessToken: String, newRefreshToken: String) {
        val expirationTime = System.currentTimeMillis() + TOKEN_EXPIRATION_DURATION
        
        preferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, newAccessToken)
            putString(KEY_REFRESH_TOKEN, newRefreshToken)
            putLong(KEY_EXPIRATION_TIME, expirationTime)
            apply()
        }
        
        _isLoggedIn.value = true
    }
    
    fun clearSession() {
        preferences.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_EXPIRATION_TIME)
            remove(KEY_LOGIN_TIME)
            apply()
        }
        
        _isLoggedIn.value = false
    }
    
    fun getSessionInfo(): SessionInfo? {
        return if (isTokenValid()) {
            SessionInfo(
                accessToken = getAccessToken() ?: return null,
                refreshToken = getRefreshToken() ?: return null,
                userId = getUserId() ?: return null,
                expirationTime = preferences.getLong(KEY_EXPIRATION_TIME, 0),
                loginTime = preferences.getLong(KEY_LOGIN_TIME, 0)
            )
        } else {
            null
        }
    }
    
    companion object {
        private const val PREFERENCES_NAME = "ausgetrunken_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EXPIRATION_TIME = "expiration_time"
        private const val KEY_LOGIN_TIME = "login_time"
        
        // 30 days in milliseconds
        private val TOKEN_EXPIRATION_DURATION = TimeUnit.DAYS.toMillis(30)
    }
}

data class SessionInfo(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expirationTime: Long,
    val loginTime: Long
)