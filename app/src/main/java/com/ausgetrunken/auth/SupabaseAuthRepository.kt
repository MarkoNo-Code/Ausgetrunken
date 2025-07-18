package com.ausgetrunken.auth

import com.ausgetrunken.data.local.TokenStorage
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.remote.model.UserProfile
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseAuthRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val tokenStorage: TokenStorage
) {
    
    val currentUser: UserInfo?
        get() = auth.currentUserOrNull()
    
    fun isUserLoggedIn(): Boolean = tokenStorage.isTokenValid() || currentUser != null
    
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
            
            // Save session tokens for persistent login
            val session = auth.currentSessionOrNull()
            session?.let {
                println("💾 SupabaseAuthRepository: Saving session - AccessToken length: ${it.accessToken.length}")
                println("💾 SupabaseAuthRepository: Saving session - RefreshToken: ${it.refreshToken ?: "NULL"}")
                println("💾 SupabaseAuthRepository: Saving session - RefreshToken length: ${it.refreshToken?.length ?: 0}")
                
                tokenStorage.saveLoginSession(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken ?: "",
                    userId = user.id
                )
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            tokenStorage.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun restoreSession(): Result<UserInfo?> {
        return try {
            println("🔄 SupabaseAuthRepository: Starting session restoration")
            val sessionInfo = tokenStorage.getSessionInfo()
            println("🔄 SupabaseAuthRepository: SessionInfo = ${if (sessionInfo != null) "EXISTS" else "NULL"}")
            
            if (sessionInfo != null) {
                println("🔄 SupabaseAuthRepository: Checking if tokens are valid...")
                // Check if tokens are still valid
                if (tokenStorage.isTokenValid()) {
                    println("✅ SupabaseAuthRepository: Tokens are valid")
                    
                    // Wait a bit for Supabase to finish loading session from storage
                    // Based on logs, Supabase loads sessions automatically but it takes time
                    println("🔄 SupabaseAuthRepository: Waiting for Supabase to load session from storage...")
                    
                    // Give Supabase some time to load the session
                    kotlinx.coroutines.delay(2000) // Wait 2 seconds
                    
                    val currentUser = auth.currentUserOrNull()
                    println("🔄 SupabaseAuthRepository: Current user from auth after delay = ${currentUser?.email ?: "NULL"}")
                    
                    if (currentUser != null) {
                        println("✅ SupabaseAuthRepository: Supabase successfully loaded session from storage!")
                        Result.success(currentUser)
                    } else {
                        println("🔄 SupabaseAuthRepository: No active session in Supabase, but we have valid tokens")
                        println("🔄 SupabaseAuthRepository: Attempting to import session...")
                        
                        try {
                            // Since session import is problematic, let's try a simpler approach
                            // We'll attempt to sign in again silently using the stored tokens
                            println("🔄 SupabaseAuthRepository: Attempting silent re-authentication...")
                            
                            // Instead of importing session, let's try to get user info from stored data
                            // Since we have a valid userId and tokens, we can create a simple authentication state
                            
                            // Create a minimal UserInfo object using reflection or alternative approach
                            // For now, let's use a simpler validation approach
                            
                            // We'll validate by trying to use the access token to make an API call
                            try {
                                println("🔄 SupabaseAuthRepository: Testing token with API call...")
                                println("🔄 SupabaseAuthRepository: Looking for user ID: ${sessionInfo.userId}")
                                
                                // Test if the token works by trying to get user profile
                                val userProfile = postgrest.from("user_profiles")
                                    .select {
                                        filter {
                                            eq("id", sessionInfo.userId)
                                        }
                                    }
                                    .decodeSingleOrNull<UserProfile>()
                                
                                println("🔄 SupabaseAuthRepository: API call completed, profile = ${if (userProfile != null) "FOUND" else "NULL"}")
                                
                                if (userProfile != null) {
                                    println("✅ SupabaseAuthRepository: Token validation successful via API call")
                                    println("🔄 SupabaseAuthRepository: User profile: ${userProfile.email}")
                                    
                                    // Since the token works and we have user data, we can proceed
                                    // We'll need to work around the UserInfo requirement
                                    
                                    // For now, let's try one more session import attempt with better error handling
                                    println("🔄 SupabaseAuthRepository: Attempting session import with valid token...")
                                    val userSession = UserSession(
                                        accessToken = sessionInfo.accessToken,
                                        refreshToken = sessionInfo.refreshToken,
                                        expiresIn = 3600,
                                        tokenType = "Bearer",
                                        user = null
                                    )
                                    
                                    auth.importSession(userSession)
                                    val user = auth.currentUserOrNull()
                                    
                                    if (user != null) {
                                        println("✅ SupabaseAuthRepository: Session import successful on retry")
                                        Result.success(user)
                                    } else {
                                        println("❌ SupabaseAuthRepository: Session import failed on retry, but token is valid")
                                        // Since the token is valid but session import fails, we have a problem
                                        // Let's clear and force re-login for now
                                        tokenStorage.clearSession()
                                        Result.success(null)
                                    }
                                } else {
                                    println("❌ SupabaseAuthRepository: Token validation failed - user profile not found")
                                    println("❌ SupabaseAuthRepository: This could mean:")
                                    println("❌   1. User profile doesn't exist in database")
                                    println("❌   2. Access token is expired/invalid")
                                    println("❌   3. Database connection issue")
                                    tokenStorage.clearSession()
                                    Result.success(null)
                                }
                            } catch (apiError: Exception) {
                                println("❌ SupabaseAuthRepository: Token validation API call failed: ${apiError.message}")
                                println("❌ SupabaseAuthRepository: API Error type: ${apiError.javaClass.simpleName}")
                                apiError.printStackTrace()
                                tokenStorage.clearSession()
                                Result.success(null)
                            }
                        } catch (importError: Exception) {
                            println("❌ SupabaseAuthRepository: Complete session restoration failed: ${importError.message}")
                            tokenStorage.clearSession()
                            Result.success(null)
                        }
                    }
                } else {
                    println("❌ SupabaseAuthRepository: Tokens expired, clearing them")
                    // Tokens expired, clear them
                    tokenStorage.clearSession()
                    Result.success(null)
                }
            } else {
                println("❌ SupabaseAuthRepository: No stored session found")
                // No stored session
                Result.success(null)
            }
        } catch (e: Exception) {
            println("❌ SupabaseAuthRepository: Exception during session restoration: ${e.message}")
            tokenStorage.clearSession()
            Result.failure(e)
        }
    }
    
    fun hasValidSession(): Boolean = tokenStorage.isTokenValid()
    
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