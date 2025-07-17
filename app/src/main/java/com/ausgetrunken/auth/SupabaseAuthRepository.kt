package com.ausgetrunken.auth

import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.remote.model.UserProfile
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseAuthRepository(
    private val auth: Auth,
    private val postgrest: Postgrest
) {
    
    val currentUser: UserInfo?
        get() = auth.currentUserOrNull()
    
    fun isUserLoggedIn(): Boolean = currentUser != null
    
    fun getCurrentUserFlow(): Flow<UserInfo?> = flow {
        emit(currentUser)
        // For now, emit the current user without session tracking
        // This can be enhanced later when we need real-time session updates
    }
    
    suspend fun signUp(email: String, password: String, userType: UserType): Result<UserInfo> {
        return try {
            // First, try to sign up with Supabase Auth
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            // Check if user was created successfully
            val user = auth.currentUserOrNull()
            
            if (user == null) {
                // User might need email confirmation
                throw Exception("Registration successful! Please check your email for confirmation link before signing in.")
            }
            
            // Only create user profile if user is confirmed or email confirmation is disabled
            if (user.emailConfirmedAt != null || user.aud == "authenticated") {
                // Create user profile in database
                try {
                    postgrest.from("user_profiles").insert(
                        buildJsonObject {
                            put("id", user.id)
                            put("email", email)
                            put("user_type", userType.name)
                            put("profile_completed", false)
                        }
                    )
                } catch (dbError: Exception) {
                    throw Exception("Database error: ${dbError.message}. Please check if database tables exist.")
                }
            } else {
                // User needs email confirmation, profile will be created on first login
                throw Exception("Registration successful! Please check your email for confirmation link. Your profile will be created when you first sign in.")
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Registration failed: ${e.message}"))
        }
    }
    
    suspend fun signIn(email: String, password: String): Result<UserInfo> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            val user = auth.currentUserOrNull() ?: throw Exception("Sign in failed")
            
            // Check if user profile exists, create if not (for users who confirmed email)
            try {
                val existingProfile = postgrest.from("user_profiles")
                    .select {
                        filter {
                            eq("id", user.id)
                        }
                    }
                    .decodeSingleOrNull<UserProfile>()
                
                if (existingProfile == null) {
                    // Create profile for confirmed user
                    postgrest.from("user_profiles").insert(
                        buildJsonObject {
                            put("id", user.id)
                            put("email", user.email ?: email)
                            put("user_type", "CUSTOMER") // Default to customer, user can change later
                            put("profile_completed", false)
                        }
                    )
                }
            } catch (dbError: Exception) {
                // Log error but don't fail login
                println("Warning: Could not create/check user profile: ${dbError.message}")
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserType(userId: String): Result<UserType> {
        return try {
            val response = postgrest.from("user_profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<UserProfile>()
            
            val userType = UserType.valueOf(response.userType)
            Result.success(userType)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateProfileCompletion(userId: String, completed: Boolean): Result<Unit> {
        return try {
            postgrest.from("user_profiles")
                .update(
                    buildJsonObject {
                        put("profile_completed", completed)
                        put("updated_at", System.currentTimeMillis().toString())
                    }
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}