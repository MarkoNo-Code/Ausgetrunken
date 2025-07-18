package com.ausgetrunken.domain.usecase

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.repository.UserRepository
import io.github.jan.supabase.gotrue.user.UserInfo

class SignUpUseCase(
    private val authRepository: SupabaseAuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String, userType: UserType): Result<UserInfo> {
        return authRepository.signUp(email, password, userType).also { result ->
            if (result.isSuccess) {
                result.getOrNull()?.let { user ->
                    userRepository.syncUserFromSupabase(user.id)
                }
            }
        }
    }
}

class SignInUseCase(
    private val authRepository: SupabaseAuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<UserInfo> {
        return authRepository.signIn(email, password).also { result ->
            if (result.isSuccess) {
                result.getOrNull()?.let { user ->
                    userRepository.syncUserFromSupabase(user.id)
                }
            }
        }
    }
}

class SignOutUseCase(
    private val authRepository: SupabaseAuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return authRepository.signOut()
    }
}

class GetCurrentUserUseCase(
    private val authRepository: SupabaseAuthRepository
) {
    operator fun invoke() = authRepository.getCurrentUserFlow()
}

class CheckUserTypeUseCase(
    private val authRepository: SupabaseAuthRepository
) {
    suspend operator fun invoke(userId: String): Result<UserType> {
        return authRepository.getUserType(userId)
    }
}

class RestoreSessionUseCase(
    private val authRepository: SupabaseAuthRepository
) {
    suspend operator fun invoke(): Result<UserInfo?> {
        return authRepository.restoreSession()
    }
}