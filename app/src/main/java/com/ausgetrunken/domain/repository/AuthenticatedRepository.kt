package com.ausgetrunken.domain.repository

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.domain.common.AppResult
import com.ausgetrunken.domain.error.AppError
import com.ausgetrunken.domain.error.toAppError
import io.github.jan.supabase.gotrue.user.UserInfo

/**
 * Repository wrapper that transparently handles authentication for all operations.
 * 
 * This is the core of our clean architecture - ViewModels never need to check
 * authentication, handle session restoration, or deal with auth edge cases.
 * All authentication complexity is abstracted away here.
 * 
 * Features:
 * - Transparent session validation and restoration
 * - Handles VALID_SESSION_NO_USER edge case
 * - Converts legacy Result<T> to AppResult<T>
 * - Provides simplified AuthenticatedUser abstraction
 * - Comprehensive error mapping for auth failures
 * - Future-proof for additional auth mechanisms
 */
class AuthenticatedRepository(
    private val authRepository: SupabaseAuthRepository
) {
    
    /**
     * Execute any repository operation with automatic authentication handling.
     * 
     * This is the main method that abstracts away all authentication complexity.
     * ViewModels call this instead of checking auth manually.
     * 
     * Process:
     * 1. Validates session exists and is not expired
     * 2. Attempts session restoration if needed  
     * 3. Handles special VALID_SESSION_NO_USER case
     * 4. Provides guaranteed authenticated user context
     * 5. Maps all auth errors to proper AppError types
     */
    suspend fun <T> executeAuthenticated(
        operation: suspend (AuthenticatedUser) -> T
    ): AppResult<T> {
        return AppResult.catchingSuspend("authentication") {
            
            // Step 1: Validate session exists
            if (!authRepository.hasValidSession()) {
                throw AuthenticationException("No valid session found")
            }
            
            // Step 2: Get authenticated user with fallback logic
            val authenticatedUser = getAuthenticatedUserInternal()
            
            // Step 3: Execute operation with guaranteed auth context
            operation(authenticatedUser)
        }
    }
    
    /**
     * Get current authenticated user with all auth complexity handled internally
     */
    suspend fun getCurrentAuthenticatedUser(): AppResult<AuthenticatedUser> {
        return executeAuthenticated { user -> user }
    }
    
    /**
     * Check if user has a specific permission (future-proof for role-based access)
     */
    suspend fun hasPermission(permission: String): AppResult<Boolean> {
        return executeAuthenticated { user ->
            // Future: implement role-based permission checking
            // For now, just return true for authenticated users
            true
        }
    }
    
    /**
     * Internal method to get authenticated user with all auth logic
     */
    private suspend fun getAuthenticatedUserInternal(): AuthenticatedUser {
        // Try to get current user from active session
        var currentUser = authRepository.currentUser
        var userIdFromSession: String? = null
        var userEmailFromSession: String? = null
        
        // If no active user, attempt session restoration
        if (currentUser == null) {
            val restorationResult = authRepository.restoreSession()
            
            restorationResult.fold(
                onSuccess = { user ->
                    currentUser = user
                },
                onFailure = { error ->
                    // Parse special session restoration errors
                    val errorMessage = error.message ?: ""
                    when {
                        errorMessage.startsWith("VALID_SESSION_NO_USER:") -> {
                            // Handle the edge case where tokens are valid but UserInfo is null
                            val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                            if (parts.size >= 2) {
                                userIdFromSession = parts[0]
                                userEmailFromSession = parts[1]
                            } else {
                                throw AuthenticationException("Invalid session data format")
                            }
                        }
                        errorMessage.startsWith("FLAGGED_ACCOUNT:") -> {
                            val reason = errorMessage.removePrefix("FLAGGED_ACCOUNT:")
                            throw AuthenticationException("Account flagged: $reason", 
                                AppError.AuthError.AccountFlagged(reason))
                        }
                        errorMessage.startsWith("SESSION_INVALIDATED:") -> {
                            throw AuthenticationException("Session invalidated by another device", 
                                AppError.AuthError.SessionInvalidated)
                        }
                        errorMessage.startsWith("SESSION_EXPIRED:") -> {
                            throw AuthenticationException("Session expired", 
                                AppError.AuthError.SessionExpired)
                        }
                        errorMessage.startsWith("SESSION_INVALID:") -> {
                            throw AuthenticationException("Invalid session", 
                                AppError.AuthError.NotAuthenticated)
                        }
                        errorMessage.startsWith("SESSION_TERMINATED:") -> {
                            throw AuthenticationException("Session terminated", 
                                AppError.AuthError.NotAuthenticated)
                        }
                        else -> {
                            throw AuthenticationException("Session restoration failed: $errorMessage")
                        }
                    }
                }
            )
        }
        
        // Determine final user ID and create AuthenticatedUser
        val userId = currentUser?.id ?: userIdFromSession
        val userEmail = currentUser?.email ?: userEmailFromSession ?: "Unknown"
        
        if (userId == null) {
            throw AuthenticationException("Unable to determine user ID")
        }
        
        return AuthenticatedUser(
            id = userId,
            email = userEmail,
            userInfo = currentUser,
            hasFullUserInfo = currentUser != null
        )
    }
}

/**
 * Enhanced user abstraction that works regardless of UserInfo availability
 */
data class AuthenticatedUser(
    val id: String,
    val email: String,
    val userInfo: UserInfo? = null,
    val hasFullUserInfo: Boolean = false
) {
    
    /**
     * Get user display name with fallback logic
     */
    val displayName: String
        get() = userInfo?.userMetadata?.get("full_name")?.toString() 
            ?: userInfo?.userMetadata?.get("name")?.toString()
            ?: email.substringBefore("@")
    
    /**
     * Get profile picture URL if available
     */
    val profilePictureUrl: String?
        get() = userInfo?.userMetadata?.get("avatar_url")?.toString()
            ?: userInfo?.userMetadata?.get("picture")?.toString()
    
    /**
     * Check if this user has complete profile information
     */
    val hasCompleteProfile: Boolean
        get() = hasFullUserInfo && userInfo?.userMetadata?.isNotEmpty() == true
}

/**
 * Internal exception for authentication failures that get converted to AppError
 */
private class AuthenticationException(
    message: String,
    val appError: AppError? = null
) : Exception(message) {
    
    fun toAppError(): AppError = appError ?: when {
        message?.contains("flagged", ignoreCase = true) == true -> {
            val reason = message?.substringAfter("flagged: ", "Unknown reason") ?: "Unknown reason"
            AppError.AuthError.AccountFlagged(reason)
        }
        message?.contains("invalidated", ignoreCase = true) == true -> 
            AppError.AuthError.SessionInvalidated
        message?.contains("expired", ignoreCase = true) == true -> 
            AppError.AuthError.SessionExpired
        message?.contains("no valid session", ignoreCase = true) == true || 
        message?.contains("unable to determine user", ignoreCase = true) == true ->
            AppError.AuthError.NotAuthenticated
        else -> AppError.AuthError.NotAuthenticated
    }
}