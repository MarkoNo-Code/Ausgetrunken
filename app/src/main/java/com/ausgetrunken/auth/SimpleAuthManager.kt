package com.ausgetrunken.auth

import com.ausgetrunken.data.local.TokenStorage

/**
 * Simple authentication manager - KISS approach.
 * Only validates session at app startup, trusts it afterwards.
 * No per-request validation like social media apps (Instagram, TikTok).
 */
class SimpleAuthManager(
    private val tokenStorage: TokenStorage,
    private val authRepository: SupabaseAuthRepository
) {
    
    /**
     * Check if user has a valid session at app startup.
     * This is the ONLY place we validate authentication.
     */
    suspend fun hasValidSessionOnStartup(): Boolean {
        return try {
            // Simple check: do we have tokens and are they not expired?
            val hasTokens = tokenStorage.isTokenValid()
            
            if (!hasTokens) {
                println("🔐 SimpleAuthManager: No valid tokens found")
                return false
            }
            
            // Try to get current user - if this fails, tokens are expired
            val currentUser = authRepository.currentUser
            val hasUser = currentUser != null
            
            println("🔐 SimpleAuthManager: Startup auth check - tokens: $hasTokens, user: $hasUser")
            return hasTokens && hasUser
            
        } catch (e: Exception) {
            println("❌ SimpleAuthManager: Session check failed: ${e.message}")
            false
        }
    }
    
    /**
     * Check if we need to show login screen.
     * Simple inverse of hasValidSessionOnStartup.
     */
    suspend fun needsLogin(): Boolean = !hasValidSessionOnStartup()
    
    /**
     * Clear session data when user logs out.
     */
    fun clearSession() {
        tokenStorage.clearSession()
        println("🔐 SimpleAuthManager: Session cleared")
    }
    
    /**
     * Check if user is currently logged in (without validation).
     * This is used during app runtime - no authentication checks.
     */
    fun isLoggedIn(): Boolean {
        return tokenStorage.isTokenValid() && authRepository.currentUser != null
    }
}