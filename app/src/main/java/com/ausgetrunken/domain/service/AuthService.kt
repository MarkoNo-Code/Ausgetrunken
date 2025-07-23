package com.ausgetrunken.domain.service

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.repository.UserRepository
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.flow.Flow

class AuthService(
    private val authRepository: SupabaseAuthRepository,
    private val userRepository: UserRepository
) {
    suspend fun signUp(email: String, password: String, userType: UserType): Result<UserInfo> {
        return authRepository.signUp(email, password, userType).also { result ->
            if (result.isSuccess) {
                result.getOrNull()?.let { user ->
                    userRepository.syncUserFromSupabase(user.id)
                }
            }
        }
    }

    suspend fun signIn(email: String, password: String): Result<UserInfo> {
        return authRepository.signIn(email, password).also { result ->
            if (result.isSuccess) {
                result.getOrNull()?.let { user ->
                    userRepository.syncUserFromSupabase(user.id)
                }
            }
        }
    }

    suspend fun signOut(): Result<Unit> {
        return authRepository.signOut()
    }

    fun getCurrentUser(): Flow<UserInfo?> {
        return authRepository.getCurrentUserFlow()
    }

    suspend fun checkUserType(userId: String): Result<UserType> {
        return authRepository.getUserType(userId)
    }

    suspend fun restoreSession(): Result<UserInfo?> {
        return authRepository.restoreSession()
    }

    suspend fun deleteAccount(): Result<Unit> {
        return authRepository.deleteAccount()
    }
    
    fun hasValidSession(): Boolean {
        return authRepository.hasValidSession()
    }
}