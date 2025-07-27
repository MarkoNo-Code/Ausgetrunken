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
import java.time.Instant

class SupabaseAuthRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val tokenStorage: TokenStorage,
    private val serviceRolePostgrest: Postgrest? = null
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
            // Trim email to remove any whitespace that might cause issues
            val cleanEmail = email.trim().lowercase()
            
            // First, try to sign up with Supabase Auth
            auth.signUpWith(Email) {
                this.email = cleanEmail
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
                // Check if user profile already exists before creating
                try {
                    val existingProfile = postgrest.from("user_profiles")
                        .select {
                            filter {
                                eq("id", user.id)
                            }
                        }
                        .decodeSingleOrNull<UserProfile>()
                    
                    if (existingProfile == null) {
                        // Create user profile in database only if it doesn't exist
                        postgrest.from("user_profiles").insert(
                            buildJsonObject {
                                put("id", user.id)
                                put("email", cleanEmail)
                                put("user_type", userType.name)
                                put("profile_completed", false)
                            }
                        )
                    }
                    // If profile already exists, that's fine - user was already registered
                } catch (dbError: Exception) {
                    // Log error but don't fail registration if profile creation/check fails
                    println("Warning: Could not create/check user profile during registration: ${dbError.message}")
                }
            } else {
                // User needs email confirmation, profile will be created on first login
                throw Exception("Registration successful! Please check your email for confirmation link. Your profile will be created when you first sign in.")
            }
            
            Result.success(user)
        } catch (e: Exception) {
            // Handle specific error cases with better user messages
            val errorMessage = when {
                e.message?.contains("User already registered") == true -> 
                    "Account already exists. Please try signing in instead."
                e.message?.contains("email confirmation") == true -> 
                    e.message // Pass through email confirmation messages as-is
                else -> "Registration failed: ${e.message}"
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    suspend fun signIn(email: String, password: String): Result<UserInfo> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            val user = auth.currentUserOrNull() ?: throw Exception("Sign in failed")
            
            // Generate new session ID for single session enforcement
            val newSessionId = java.util.UUID.randomUUID().toString()
            println("🔐 SupabaseAuthRepository: New session ID generated: $newSessionId")
            
            // Check if user profile exists and if it's flagged for deletion
            try {
                println("🔍 SupabaseAuthRepository.signIn: Checking user profile for flagged status...")
                println("🔍 SupabaseAuthRepository.signIn: User ID: ${user.id}")
                
                val existingProfile = postgrest.from("user_profiles")
                    .select {
                        filter {
                            eq("id", user.id)
                        }
                    }
                    .decodeSingleOrNull<UserProfile>()
                
                println("🔍 SupabaseAuthRepository.signIn: Profile query result: ${if (existingProfile != null) "FOUND" else "NULL"}")
                
                if (existingProfile != null) {
                    println("🔍 SupabaseAuthRepository.signIn: Profile details:")
                    println("  - Email: ${existingProfile.email}")
                    println("  - UserType: ${existingProfile.userType}")
                    println("  - FlaggedForDeletion: ${existingProfile.flaggedForDeletion}")
                    println("  - DeletionType: ${existingProfile.deletionType}")
                    println("  - DeletionFlaggedAt: ${existingProfile.deletionFlaggedAt}")
                    
                    // Check if account is flagged for deletion
                    if (existingProfile.flaggedForDeletion) {
                        println("❌ SupabaseAuthRepository.signIn: Account IS flagged for deletion - blocking login")
                        
                        // Sign out immediately and block login
                        auth.signOut()
                        tokenStorage.clearSession()
                        
                        val deletionType = when (existingProfile.deletionType) {
                            "ADMIN" -> "by an administrator"
                            "USER" -> "at your request"
                            else -> ""
                        }
                        
                        throw Exception("Login not allowed. Your account has been flagged for deletion $deletionType. Please contact support if you believe this is an error.")
                    } else {
                        println("✅ SupabaseAuthRepository.signIn: Account is NOT flagged for deletion - allowing login")
                        
                        // SINGLE SESSION ENFORCEMENT: Update session info to invalidate other sessions
                        println("🔐 SupabaseAuthRepository: Enforcing single session - updating session ID")
                        
                        // Check if there was a previous session to notify user
                        val previousSessionId = existingProfile.currentSessionId
                        val hadPreviousSession = previousSessionId != null && previousSessionId != newSessionId
                        
                        if (hadPreviousSession) {
                            println("🔐 SupabaseAuthRepository: Previous session detected ($previousSessionId) - will be invalidated")
                        } else {
                            println("🔐 SupabaseAuthRepository: No previous session or same device login")
                        }
                        
                        try {
                            postgrest.from("user_profiles")
                                .update(
                                    buildJsonObject {
                                        put("current_session_id", newSessionId)
                                        put("session_created_at", Instant.now().toString())
                                        put("last_session_activity", Instant.now().toString())
                                        // NOTE: Don't clear FCM token here - it will be updated with new token later
                                        put("updated_at", Instant.now().toString())
                                    }
                                ) {
                                    filter {
                                        eq("id", user.id)
                                    }
                                }
                            
                            if (hadPreviousSession) {
                                println("✅ SupabaseAuthRepository: Previous session invalidated - other devices will be logged out")
                            } else {
                                println("✅ SupabaseAuthRepository: Session info updated")
                            }
                        } catch (sessionError: Exception) {
                            println("❌ SupabaseAuthRepository: Failed to update session info: ${sessionError.message}")
                            // Don't fail login if session update fails, but log it
                        }
                    }
                } else {
                    // Create profile for confirmed user with session tracking
                    postgrest.from("user_profiles").insert(
                        buildJsonObject {
                            put("id", user.id)
                            put("email", user.email ?: email)
                            put("user_type", "CUSTOMER") // Default to customer, user can change later
                            put("profile_completed", false)
                            put("flagged_for_deletion", false)
                            put("current_session_id", newSessionId)
                            put("session_created_at", Instant.now().toString())
                            put("last_session_activity", Instant.now().toString())
                        }
                    )
                    println("✅ SupabaseAuthRepository: New user profile created with session tracking")
                }
            } catch (dbError: Exception) {
                // If it's a flagged account error, re-throw it
                if (dbError.message?.contains("flagged for deletion") == true) {
                    throw dbError
                }
                // Log other errors but don't fail login
                println("Warning: Could not create/check user profile: ${dbError.message}")
            }
            
            // Save session tokens for persistent login with session ID
            val session = auth.currentSessionOrNull()
            session?.let {
                println("💾 SupabaseAuthRepository: Saving session - AccessToken length: ${it.accessToken.length}")
                println("💾 SupabaseAuthRepository: Saving session - RefreshToken: ${it.refreshToken ?: "NULL"}")
                println("💾 SupabaseAuthRepository: Saving session - RefreshToken length: ${it.refreshToken?.length ?: 0}")
                println("💾 SupabaseAuthRepository: Saving session ID: $newSessionId")
                
                tokenStorage.saveLoginSession(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken ?: "",
                    userId = user.id,
                    sessionId = newSessionId
                )
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut(): Result<Unit> {
        return try {
            println("🗑️ SupabaseAuthRepository: Starting simple logout")
            
            // Get user info before clearing everything
            val sessionInfo = tokenStorage.getSessionInfo()
            
            if (sessionInfo != null) {
                try {
                    println("🗑️ SupabaseAuthRepository: Clearing session token remotely for user: ${sessionInfo.userId}")
                    // Clear the session ID and FCM token from remote database
                    postgrest.from("user_profiles")
                        .update(
                            buildJsonObject {
                                put("current_session_id", null as String?)
                                put("fcm_token", null as String?)
                                put("updated_at", Instant.now().toString())
                            }
                        ) {
                            filter {
                                eq("id", sessionInfo.userId)
                            }
                        }
                    println("✅ SupabaseAuthRepository: Remote session cleared")
                } catch (remoteError: Exception) {
                    println("⚠️ SupabaseAuthRepository: Failed to clear remote session: ${remoteError.message}")
                    // Don't fail logout if remote cleanup fails
                }
            }
            
            // Clear local session
            auth.signOut()
            tokenStorage.clearSession()
            println("✅ SupabaseAuthRepository: Local session cleared")
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ SupabaseAuthRepository: Logout failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    suspend fun restoreSession(): Result<UserInfo?> {
        return try {
            println("🔄 SupabaseAuthRepository: Starting simple session restoration")
            val sessionInfo = tokenStorage.getSessionInfo()
            
            // Step 1: Check if we have local tokens
            if (sessionInfo == null) {
                println("❌ SupabaseAuthRepository: No local session found")
                return Result.success(null)
            }
            
            if (!tokenStorage.isTokenValid()) {
                println("❌ SupabaseAuthRepository: Local tokens expired")
                tokenStorage.clearSession()
                return Result.success(null)
            }
            
            println("✅ SupabaseAuthRepository: Found valid local tokens for user: ${sessionInfo.userId}")
            
            // Step 2: Check remote database for matching session ID
            try {
                // Use service role postgrest to bypass RLS policies during session restoration
                val profileQuery = (serviceRolePostgrest ?: postgrest)
                val userProfile = profileQuery.from("user_profiles")
                    .select {
                        filter {
                            eq("id", sessionInfo.userId)
                        }
                    }
                    .decodeSingleOrNull<UserProfile>()
                
                if (userProfile == null) {
                    println("❌ SupabaseAuthRepository: User profile not found remotely")
                    tokenStorage.clearSession()
                    return Result.success(null)
                }
                
                // Check if account is flagged for deletion
                if (userProfile.flaggedForDeletion) {
                    println("❌ SupabaseAuthRepository: Account flagged for deletion")
                    tokenStorage.clearSession()
                    return if (userProfile.deletionType == "ADMIN") {
                        Result.failure(Exception("FLAGGED_ACCOUNT:Your account has been flagged for deletion by an administrator."))
                    } else {
                        Result.success(null)
                    }
                }
                
                // Step 3: Compare local and remote session IDs
                val localSessionId = sessionInfo.sessionId
                val remoteSessionId = userProfile.currentSessionId
                
                println("🔐 SupabaseAuthRepository: Local session: $localSessionId, Remote session: $remoteSessionId")
                
                // If session IDs don't match, user logged in elsewhere
                if (localSessionId != null && remoteSessionId != null && localSessionId != remoteSessionId) {
                    println("❌ SupabaseAuthRepository: Session mismatch - logged in elsewhere")
                    tokenStorage.clearSession()
                    return Result.failure(Exception("SESSION_INVALIDATED:You logged in from another device."))
                }
                
                // Step 4: Restore Supabase session
                println("✅ SupabaseAuthRepository: Session IDs match, restoring Supabase session...")
                
                // Try to import session into Supabase
                val userSession = UserSession(
                    accessToken = sessionInfo.accessToken,
                    refreshToken = sessionInfo.refreshToken,
                    expiresIn = 3600,
                    tokenType = "Bearer",
                    user = null
                )
                
                auth.importSession(userSession)
                
                // Give Supabase a moment to process
                kotlinx.coroutines.delay(500)
                
                val currentUser = auth.currentUserOrNull()
                if (currentUser != null) {
                    println("✅ SupabaseAuthRepository: Session restored successfully for: ${currentUser.email}")
                    return Result.success(currentUser)
                } else {
                    println("⚠️ SupabaseAuthRepository: Supabase session import failed, but tokens are valid")
                    println("⚠️ SupabaseAuthRepository: Session is valid based on token/remote comparison, will proceed without Supabase UserInfo")
                    // The session is valid based on token comparison, but we couldn't import into Supabase
                    // We'll return a special result that indicates valid session with minimal user data
                    return Result.failure(Exception("VALID_SESSION_NO_USER:${sessionInfo.userId}:${userProfile.email}"))
                }
                
            } catch (e: Exception) {
                println("❌ SupabaseAuthRepository: Database check failed: ${e.message}")
                
                // Check if this is a JWT expired error - attempt token refresh
                if (e.message?.contains("JWT expired", ignoreCase = true) == true) {
                    println("🔄 SupabaseAuthRepository: JWT expired, attempting token refresh...")
                    
                    try {
                        // Try to refresh the token using Supabase
                        val userSession = UserSession(
                            accessToken = sessionInfo.accessToken,
                            refreshToken = sessionInfo.refreshToken,
                            expiresIn = 3600,
                            tokenType = "Bearer",
                            user = null
                        )
                        
                        // Import the session first (this might trigger a refresh)
                        auth.importSession(userSession)
                        
                        // Give Supabase time to process and potentially refresh
                        kotlinx.coroutines.delay(1000)
                        
                        // Try to refresh the session
                        auth.refreshCurrentSession()
                        
                        // Check if we now have a valid session
                        val currentUser = auth.currentUserOrNull()
                        val currentSession = auth.currentSessionOrNull()
                        
                        if (currentUser != null && currentSession != null) {
                            println("✅ SupabaseAuthRepository: Token refresh successful!")
                            
                            // Update stored tokens with new ones
                            tokenStorage.saveLoginSession(
                                accessToken = currentSession.accessToken,
                                refreshToken = currentSession.refreshToken ?: sessionInfo.refreshToken,
                                userId = currentUser.id,
                                sessionId = sessionInfo.sessionId
                            )
                            
                            return Result.success(currentUser)
                        } else {
                            println("❌ SupabaseAuthRepository: Token refresh failed, clearing session")
                            tokenStorage.clearSession()
                            return Result.success(null)
                        }
                        
                    } catch (refreshError: Exception) {
                        println("❌ SupabaseAuthRepository: Token refresh failed: ${refreshError.message}")
                        tokenStorage.clearSession()
                        return Result.success(null)
                    }
                } else {
                    // On other network errors, don't force logout - let user continue
                    return Result.success(null)
                }
            }
            
        } catch (e: Exception) {
            println("❌ SupabaseAuthRepository: Session restoration failed: ${e.message}")
            return Result.success(null)
        }
    }
    
    fun hasValidSession(): Boolean = tokenStorage.isTokenValid()
    
    suspend fun getUserType(userId: String): Result<UserType> {
        return try {
            println("🔍 SupabaseAuthRepository.getUserType: Getting user type for ID: $userId")
            val response = postgrest.from("user_profiles")
                .select {
                    filter {
                        eq("id", userId)
                        // Removed flagged_for_deletion filter - we need to get user type even for flagged users
                        // The flagged check should happen separately in authentication flow
                    }
                }
                .decodeSingle<UserProfile>()
            
            println("🔍 SupabaseAuthRepository.getUserType: Found user type: ${response.userType}, flagged: ${response.flaggedForDeletion}")
            
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
                        put("updated_at", Instant.now().toString())
                    }
                ) {
                    filter {
                        eq("id", userId)
                        // Removed flagged_for_deletion filter - profile updates should work regardless of flag status
                        // The flagged check should happen at the authentication level
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUserOrNull()
                ?: return Result.failure(Exception("No authenticated user found"))
            
            // Flag the user account for deletion instead of actually deleting data
            // This allows for recovery and proper admin cleanup later
            
            try {
                postgrest.from("user_profiles")
                    .update(
                        buildJsonObject {
                            put("flagged_for_deletion", true)
                            put("deletion_flagged_at", Instant.now().toString())
                            put("deletion_type", "USER")
                            put("updated_at", Instant.now().toString())
                        }
                    ) {
                        filter {
                            eq("id", user.id)
                        }
                    }
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to flag account for deletion: ${e.message}"))
            }
            
            // Sign out the user and clear local data
            // The account is now flagged and login will be blocked
            auth.signOut()
            tokenStorage.clearSession()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to flag account for deletion: ${e.message}"))
        }
    }
    
}