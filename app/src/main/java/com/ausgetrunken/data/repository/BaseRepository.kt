package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository

/**
 * Base repository class - KISS approach.
 * No per-request session validation - trust the session after app startup.
 * Let Supabase handle auth errors naturally.
 */
abstract class BaseRepository(
    protected val authRepository: SupabaseAuthRepository
) {
    
    /**
     * Simple helper to execute API calls.
     * No session validation - if auth fails, Supabase will tell us.
     */
    protected suspend fun <T> execute(
        apiCall: suspend () -> Result<T>
    ): Result<T> {
        return try {
            apiCall()
        } catch (e: Exception) {
            println("❌ BaseRepository: API call failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Simple helper for list operations.
     * No session validation - if auth fails, return empty list.
     */
    protected suspend fun <T> executeForList(
        apiCall: suspend () -> List<T>
    ): List<T> {
        return try {
            apiCall()
        } catch (e: Exception) {
            println("❌ BaseRepository: List API call failed: ${e.message}")
            emptyList()
        }
    }
}