package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository

/**
 * Base repository class that provides session validation for all API calls.
 * All repositories should extend this class to ensure proper session handling.
 */
abstract class BaseRepository(
    protected val authRepository: SupabaseAuthRepository
) {
    
    /**
     * Executes an API call with automatic session validation.
     * If session validation fails, returns the session error instead of executing the call.
     */
    protected suspend fun <T> withSessionValidation(
        skipValidation: Boolean = false,
        apiCall: suspend () -> Result<T>
    ): Result<T> {
        return try {
            // Skip validation if explicitly requested (for non-critical operations)
            if (skipValidation) {
                return apiCall()
            }
            
            // Validate session before making API call
            authRepository.validateCurrentSession().getOrElse { error ->
                // If session validation fails, check if it's a critical error
                val errorMessage = error.message ?: ""
                
                // For some errors, we might want to skip validation and continue
                val shouldSkipValidation = when {
                    // Network errors - allow API call to proceed
                    errorMessage.startsWith("SESSION_CHECK_FAILED:") -> true
                    // Database connection issues - allow API call to proceed  
                    errorMessage.contains("connection") -> true
                    errorMessage.contains("network") -> true
                    // All other session errors should block the API call
                    else -> false
                }
                
                if (shouldSkipValidation) {
                    println("⚠️ BaseRepository: Session validation failed with non-critical error, allowing API call: ${error.message}")
                    return apiCall()
                } else {
                    println("❌ BaseRepository: Session validation failed, blocking API call: ${error.message}")
                    return Result.failure(error)
                }
            }
            
            // Session is valid, proceed with API call
            apiCall()
        } catch (e: Exception) {
            println("❌ BaseRepository: Exception during API call: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Executes an API call without session validation.
     * Use this for operations that don't require authentication.
     */
    protected suspend fun <T> withoutSessionValidation(
        apiCall: suspend () -> Result<T>
    ): Result<T> {
        return withSessionValidation(skipValidation = true, apiCall = apiCall)
    }
    
    /**
     * Executes an API call that returns a list with session validation.
     * If session validation fails, returns empty list for non-critical errors.
     */
    protected suspend fun <T> withSessionValidationForList(
        apiCall: suspend () -> List<T>
    ): List<T> {
        return try {
            // Validate session before making API call
            authRepository.validateCurrentSession().getOrElse { error ->
                val errorMessage = error.message ?: ""
                
                // For some errors, allow API call to proceed
                val shouldSkipValidation = when {
                    errorMessage.startsWith("SESSION_CHECK_FAILED:") -> true
                    errorMessage.contains("connection") -> true
                    errorMessage.contains("network") -> true
                    else -> false
                }
                
                if (shouldSkipValidation) {
                    println("⚠️ BaseRepository: Session validation failed for list operation, allowing API call: ${error.message}")
                    return apiCall()
                } else {
                    println("❌ BaseRepository: Session validation failed for list operation, returning empty list: ${error.message}")
                    return emptyList()
                }
            }
            
            // Session is valid, proceed with API call
            apiCall()
        } catch (e: Exception) {
            println("❌ BaseRepository: Exception during list API call: ${e.message}")
            emptyList()
        }
    }
}