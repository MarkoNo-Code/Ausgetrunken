package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository

/**
 * Base repository class - simplified session validation.
 * Session management is now handled simply at app startup.
 */
abstract class BaseRepository(
    protected val authRepository: SupabaseAuthRepository
) {
    
    /**
     * Executes an API call with simplified session validation.
     * Just checks if we have valid local tokens.
     */
    protected suspend fun <T> withSessionValidation(
        skipValidation: Boolean = false,
        apiCall: suspend () -> Result<T>
    ): Result<T> {
        return try {
            if (skipValidation) {
                return apiCall()
            }
            
            // Simple check: do we have valid local tokens?
            if (!authRepository.hasValidSession()) {
                println("❌ BaseRepository: No valid local session")
                return Result.failure(Exception("SESSION_REQUIRED:Please log in to continue."))
            }
            
            // Session is valid locally, proceed with API call
            apiCall()
        } catch (e: Exception) {
            println("❌ BaseRepository: API call failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Executes an API call without session validation.
     */
    protected suspend fun <T> withoutSessionValidation(
        apiCall: suspend () -> Result<T>
    ): Result<T> {
        return withSessionValidation(skipValidation = true, apiCall = apiCall)
    }
    
    /**
     * Executes an API call that returns a list with simple session validation.
     */
    protected suspend fun <T> withSessionValidationForList(
        apiCall: suspend () -> List<T>
    ): List<T> {
        return try {
            if (!authRepository.hasValidSession()) {
                println("❌ BaseRepository: No valid local session for list operation")
                return emptyList()
            }
            
            apiCall()
        } catch (e: Exception) {
            println("❌ BaseRepository: List API call failed: ${e.message}")
            emptyList()
        }
    }
}