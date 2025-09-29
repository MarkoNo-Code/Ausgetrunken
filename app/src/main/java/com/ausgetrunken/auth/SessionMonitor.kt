package com.ausgetrunken.auth

import com.ausgetrunken.data.local.TokenStorage
import com.ausgetrunken.data.remote.model.UserProfile
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionMonitor(
    private val tokenStorage: TokenStorage,
    private val postgrest: Postgrest
) {
    private val _sessionInvalidated = MutableStateFlow(false)
    val sessionInvalidated: StateFlow<Boolean> = _sessionInvalidated.asStateFlow()
    
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val CHECK_INTERVAL_MS = 30_000L // Check every 30 seconds
    }
    
    fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            println("🔍 SessionMonitor: Already monitoring sessions")
            return
        }
        
        println("🔍 SessionMonitor: Starting session monitoring")
        
        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    checkSessionValidity()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: CancellationException) {
                    println("🔍 SessionMonitor: Monitoring cancelled")
                    break
                } catch (e: Exception) {
                    // Removed println: "❌ SessionMonitor: Error during session check: ${e.message}"
                    // Continue monitoring even if one check fails
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }
    
    fun stopMonitoring() {
        println("🔍 SessionMonitor: Stopping session monitoring")
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    private suspend fun checkSessionValidity() {
        val sessionInfo = tokenStorage.getSessionInfo()
        
        if (sessionInfo == null) {
            println("🔍 SessionMonitor: No session to monitor")
            return
        }
        
        if (!tokenStorage.isTokenValid()) {
            println("🔍 SessionMonitor: Local token expired")
            return
        }
        
        try {
            println("🔍 SessionMonitor: Checking session validity for user: ${sessionInfo.userId}")
            
            // Query user profile to get current session ID
            val userProfile = postgrest.from("user_profiles")
                .select {
                    filter {
                        eq("id", sessionInfo.userId)
                    }
                }
                .decodeSingleOrNull<UserProfile>()
            
            if (userProfile == null) {
                // Removed println: "❌ SessionMonitor: User profile not found - session invalid"
                triggerSessionInvalidation()
                return
            }
            
            // Check if user is flagged for deletion
            if (userProfile.flaggedForDeletion) {
                // Removed println: "❌ SessionMonitor: User account flagged for deletion - invalidating session"
                triggerSessionInvalidation()
                return
            }
            
            // Check session ID match
            val storedSessionId = sessionInfo.sessionId
            val dbSessionId = userProfile.currentSessionId
            
            when {
                // Perfect match - session is valid
                storedSessionId != null && dbSessionId != null && storedSessionId == dbSessionId -> {
                    // Removed println: "✅ SessionMonitor: Session is valid (IDs match")
                }
                
                // Database doesn't have session tracking - allow (legacy)
                dbSessionId == null -> {
                    println("🔄 SessionMonitor: No session tracking in database - allowing session")
                }
                
                // Both missing - allow (legacy)
                storedSessionId == null && dbSessionId == null -> {
                    println("🔄 SessionMonitor: Both session IDs missing - allowing session")
                }
                
                // Session ID mismatch - user logged in elsewhere
                storedSessionId != null && dbSessionId != null && storedSessionId != dbSessionId -> {
                    // Removed println: "❌ SessionMonitor: Session ID mismatch - user logged in elsewhere"
                    // Removed println: "❌ SessionMonitor: Stored: $storedSessionId, DB: $dbSessionId"
                    triggerSessionInvalidation()
                    return
                }
                
                // Default case - allow but log
                else -> {
                    // Removed println: "⚠️ SessionMonitor: Session validation unclear - allowing session"
                }
            }
            
        } catch (e: Exception) {
            // Removed println: "❌ SessionMonitor: Failed to check session validity: ${e.message}"
            // Don't invalidate session on network errors - continue monitoring
        }
    }
    
    private fun triggerSessionInvalidation() {
        println("🚨 SessionMonitor: Triggering session invalidation")
        _sessionInvalidated.value = true
        
        // Clear local session
        scope.launch {
            try {
                tokenStorage.clearSession()
                // Removed println: "✅ SessionMonitor: Local session cleared"
            } catch (e: Exception) {
                // Removed println: "❌ SessionMonitor: Failed to clear local session: ${e.message}"
            }
        }
        
        // Stop monitoring
        stopMonitoring()
    }
    
    fun resetInvalidationFlag() {
        _sessionInvalidated.value = false
    }
}