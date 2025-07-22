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
                        
                        // FORCE SINGLE SESSION: Update session info and clear FCM token from any previous sessions
                        println("🔐 SupabaseAuthRepository: Enforcing single session - invalidating previous sessions")
                        
                        try {
                            postgrest.from("user_profiles")
                                .update(
                                    buildJsonObject {
                                        put("current_session_id", newSessionId)
                                        put("session_created_at", Instant.now().toString())
                                        put("last_session_activity", Instant.now().toString())
                                        put("fcm_token", null as String?) // Clear old FCM token
                                        put("updated_at", Instant.now().toString())
                                    }
                                ) {
                                    filter {
                                        eq("id", user.id)
                                    }
                                }
                            
                            println("✅ SupabaseAuthRepository: Session updated - previous sessions invalidated")
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
            // Important: Clear FCM token from database before signing out
            // This prevents the old token from being associated with a different user
            val currentUser = auth.currentUserOrNull()
            if (currentUser != null) {
                try {
                    println("🗑️ SupabaseAuthRepository: Clearing FCM token on logout for user: ${currentUser.id}")
                    postgrest.from("user_profiles")
                        .update(
                            buildJsonObject {
                                put("fcm_token", null as String?)
                                put("updated_at", Instant.now().toString())
                            }
                        ) {
                            filter {
                                eq("id", currentUser.id)
                            }
                        }
                    println("✅ SupabaseAuthRepository: FCM token cleared from database")
                } catch (tokenError: Exception) {
                    println("⚠️ SupabaseAuthRepository: Failed to clear FCM token on logout: ${tokenError.message}")
                    // Don't fail logout if token cleanup fails
                }
            }
            
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
                        
                        // Check if the user is flagged for deletion before allowing session restoration
                        try {
                            println("🔍 SupabaseAuthRepository.restoreSession: Querying database for user ID: ${currentUser.id}")
                            println("🔍 SupabaseAuthRepository.restoreSession: User email: ${currentUser.email}")
                            
                            val userProfile = postgrest.from("user_profiles")
                                .select {
                                    filter {
                                        eq("id", currentUser.id)
                                    }
                                }
                                .decodeSingleOrNull<UserProfile>()
                            
                            if (userProfile != null && userProfile.flaggedForDeletion) {
                                println("❌ SupabaseAuthRepository.restoreSession: Account IS flagged for deletion - clearing session")
                                
                                tokenStorage.clearSession()
                                auth.signOut()
                                
                                // Only show message for admin-flagged accounts
                                if (userProfile.deletionType == "ADMIN") {
                                    println("❌ SupabaseAuthRepository.restoreSession: Admin-flagged account - will show message to user")
                                    // Return failure with specific error message for admin-flagged accounts
                                    Result.failure(Exception("FLAGGED_ACCOUNT:Your session has been terminated because your account has been flagged for deletion by an administrator. Please contact support if you believe this is an error."))
                                } else {
                                    println("❌ SupabaseAuthRepository.restoreSession: User-flagged account - silent logout without message")
                                    // For user-flagged accounts, silently redirect to login without message
                                    Result.success(null)
                                }
                            } else {
                                println("✅ SupabaseAuthRepository.restoreSession: Account is NOT flagged for deletion - checking session validity")
                                
                                // Check if this session is still valid (single session enforcement)
                                val storedSessionId = sessionInfo.sessionId
                                val dbSessionId = userProfile?.currentSessionId
                                
                                println("🔐 SupabaseAuthRepository: Stored session ID: $storedSessionId")
                                println("🔐 SupabaseAuthRepository: Database session ID: $dbSessionId")
                                
                                when {
                                    // Case 1: Both session IDs exist and match - perfect!
                                    storedSessionId != null && dbSessionId != null && storedSessionId == dbSessionId -> {
                                        println("✅ SupabaseAuthRepository: Session IDs match - session is valid")
                                        Result.success(currentUser)
                                    }
                                    
                                    // Case 2: Session ID mismatch - user logged in elsewhere - FORCE LOGOUT
                                    storedSessionId != null && dbSessionId != null && storedSessionId != dbSessionId -> {
                                        println("❌ SupabaseAuthRepository: Session ID mismatch - session was invalidated by another login")
                                        println("❌ SupabaseAuthRepository: Stored: $storedSessionId, DB: $dbSessionId")
                                        println("❌ SupabaseAuthRepository: This session is no longer valid - clearing local session")
                                        tokenStorage.clearSession()
                                        Result.failure(Exception("SESSION_INVALIDATED:Your session has been terminated because you logged in from another device."))
                                    }
                                    
                                    // Case 3: Database has session but local doesn't - FORCE LOGOUT (user logged in elsewhere)
                                    storedSessionId == null && dbSessionId != null -> {
                                        println("❌ SupabaseAuthRepository: Database has active session but local doesn't - user logged in elsewhere")
                                        println("❌ SupabaseAuthRepository: DB session: $dbSessionId")
                                        tokenStorage.clearSession()
                                        Result.failure(Exception("SESSION_INVALIDATED:Your session has been terminated because you logged in from another device."))
                                    }
                                    
                                    // Case 4: Local has session but database doesn't - CAUTIOUS LOGOUT 
                                    storedSessionId != null && dbSessionId == null -> {
                                        println("❌ SupabaseAuthRepository: Local has session but database doesn't - session likely expired")
                                        tokenStorage.clearSession()
                                        Result.failure(Exception("SESSION_EXPIRED:Your session has expired. Please log in again."))
                                    }
                                    
                                    // Case 5: Both missing - be more strict, only allow if this is truly a legacy session
                                    storedSessionId == null && dbSessionId == null -> {
                                        println("❌ SupabaseAuthRepository: Both session IDs missing - forcing logout for security")
                                        println("❌ SupabaseAuthRepository: This prevents session hijacking from legacy tokens")
                                        tokenStorage.clearSession()
                                        Result.failure(Exception("SESSION_EXPIRED:Your session has expired. Please log in again."))
                                    }
                                    
                                    // Default case - something is wrong, be safe
                                    else -> {
                                        println("❌ SupabaseAuthRepository: Session validation failed - stored: $storedSessionId, db: $dbSessionId")
                                        tokenStorage.clearSession()
                                        Result.failure(Exception("SESSION_INVALID:Session validation failed. Please log in again."))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ SupabaseAuthRepository: Error checking flagged status during session restoration: ${e.message}")
                            // If we can't check the flagged status, allow the session but log the error
                            Result.success(currentUser)
                        }
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
                                    
                                    // Check if account is flagged for deletion
                                    println("🔄 SupabaseAuthRepository.restoreSession: Checking flagged status via API call...")
                                    println("🔄 SupabaseAuthRepository.restoreSession: Profile details from API:")
                                    println("  - Email: ${userProfile.email}")
                                    println("  - UserType: ${userProfile.userType}")
                                    println("  - FlaggedForDeletion: ${userProfile.flaggedForDeletion}")
                                    println("  - DeletionType: ${userProfile.deletionType}")
                                    println("  - DeletionFlaggedAt: ${userProfile.deletionFlaggedAt}")
                                    
                                    if (userProfile.flaggedForDeletion) {
                                        println("❌ SupabaseAuthRepository.restoreSession: Account IS flagged for deletion - clearing session (alternative path)")
                                        tokenStorage.clearSession()
                                        
                                        // Only show message for admin-flagged accounts
                                        if (userProfile.deletionType == "ADMIN") {
                                            println("❌ SupabaseAuthRepository.restoreSession: Admin-flagged account - will show message to user (alternative path)")
                                            // Return failure with specific error message for admin-flagged accounts
                                            return Result.failure(Exception("FLAGGED_ACCOUNT:Your session has been terminated because your account has been flagged for deletion by an administrator. Please contact support if you believe this is an error."))
                                        } else {
                                            println("❌ SupabaseAuthRepository.restoreSession: User-flagged account - silent logout without message (alternative path)")
                                            // For user-flagged accounts, silently redirect to login without message
                                            return Result.success(null)
                                        }
                                    } else {
                                        println("✅ SupabaseAuthRepository.restoreSession: Account is NOT flagged for deletion via API call - checking session validity")
                                        
                                        // Apply same session validation logic as above
                                        val storedSessionId = sessionInfo.sessionId
                                        val dbSessionId = userProfile.currentSessionId
                                        
                                        println("🔐 SupabaseAuthRepository: Alternative path - Stored session ID: $storedSessionId")
                                        println("🔐 SupabaseAuthRepository: Alternative path - Database session ID: $dbSessionId")
                                        
                                        val shouldAllowSession = when {
                                            // Both exist and match - perfect
                                            storedSessionId != null && dbSessionId != null && storedSessionId == dbSessionId -> true
                                            // Session ID mismatch - invalidated
                                            storedSessionId != null && dbSessionId != null && storedSessionId != dbSessionId -> false
                                            // Database has session but local doesn't - user logged in elsewhere
                                            storedSessionId == null && dbSessionId != null -> false
                                            // Local has session but database doesn't - session expired
                                            storedSessionId != null && dbSessionId == null -> false
                                            // Both missing - be strict now
                                            storedSessionId == null && dbSessionId == null -> false
                                            // Default - be strict
                                            else -> false
                                        }
                                        
                                        if (shouldAllowSession) {
                                            println("✅ SupabaseAuthRepository: Session validation passed - attempting session import")
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
                                            println("❌ SupabaseAuthRepository: Session validation failed - session was invalidated")
                                            tokenStorage.clearSession()
                                            Result.success(null)
                                        }
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
    
    suspend fun validateCurrentSession(): Result<Unit> {
        return try {
            println("🔐 SupabaseAuthRepository: Validating current session for API call")
            
            val sessionInfo = tokenStorage.getSessionInfo()
            if (sessionInfo == null) {
                println("❌ SupabaseAuthRepository: No session info found")
                return Result.failure(Exception("SESSION_REQUIRED:Please log in to continue."))
            }
            
            if (!tokenStorage.isTokenValid()) {
                println("❌ SupabaseAuthRepository: Local tokens are expired")
                tokenStorage.clearSession()
                return Result.failure(Exception("SESSION_EXPIRED:Your session has expired. Please log in again."))
            }
            
            // Check session validity against database
            val userProfile = postgrest.from("user_profiles")
                .select {
                    filter {
                        eq("id", sessionInfo.userId)
                    }
                }
                .decodeSingleOrNull<UserProfile>()
            
            if (userProfile == null) {
                println("❌ SupabaseAuthRepository: User profile not found during session validation")
                tokenStorage.clearSession()
                return Result.failure(Exception("SESSION_INVALID:Session validation failed. Please log in again."))
            }
            
            // Check if user is flagged for deletion
            if (userProfile.flaggedForDeletion) {
                println("❌ SupabaseAuthRepository: User account flagged for deletion during API call")
                tokenStorage.clearSession()
                
                val errorMessage = if (userProfile.deletionType == "ADMIN") {
                    "FLAGGED_ACCOUNT:Your account has been flagged for deletion by an administrator. Please contact support."
                } else {
                    "SESSION_TERMINATED:Your session has been terminated."
                }
                return Result.failure(Exception(errorMessage))
            }
            
            // Validate session IDs
            val storedSessionId = sessionInfo.sessionId
            val dbSessionId = userProfile.currentSessionId
            
            println("🔐 SupabaseAuthRepository: API call session validation - Stored: $storedSessionId, DB: $dbSessionId")
            
            when {
                // Perfect match - session is valid
                storedSessionId != null && dbSessionId != null && storedSessionId == dbSessionId -> {
                    println("✅ SupabaseAuthRepository: Session validation passed for API call")
                    Result.success(Unit)
                }
                
                // Session ID mismatch - user logged in elsewhere
                storedSessionId != null && dbSessionId != null && storedSessionId != dbSessionId -> {
                    println("❌ SupabaseAuthRepository: Session ID mismatch during API call - invalidated by another login")
                    tokenStorage.clearSession()
                    Result.failure(Exception("SESSION_INVALIDATED:Your session has been terminated because you logged in from another device."))
                }
                
                // Database has session but local doesn't - user logged in elsewhere
                storedSessionId == null && dbSessionId != null -> {
                    println("❌ SupabaseAuthRepository: Database has session but local doesn't during API call")
                    tokenStorage.clearSession()
                    Result.failure(Exception("SESSION_INVALIDATED:Your session has been terminated because you logged in from another device."))
                }
                
                // Local has session but database doesn't - session expired
                storedSessionId != null && dbSessionId == null -> {
                    println("❌ SupabaseAuthRepository: Local has session but database doesn't during API call")
                    tokenStorage.clearSession()
                    Result.failure(Exception("SESSION_EXPIRED:Your session has expired. Please log in again."))
                }
                
                // Both missing - strict validation
                storedSessionId == null && dbSessionId == null -> {
                    println("❌ SupabaseAuthRepository: Both session IDs missing during API call - forcing logout")
                    tokenStorage.clearSession()
                    Result.failure(Exception("SESSION_EXPIRED:Your session has expired. Please log in again."))
                }
                
                // Default case - be safe
                else -> {
                    println("❌ SupabaseAuthRepository: Session validation failed during API call")
                    tokenStorage.clearSession()
                    Result.failure(Exception("SESSION_INVALID:Session validation failed. Please log in again."))
                }
            }
        } catch (e: Exception) {
            println("❌ SupabaseAuthRepository: Exception during session validation: ${e.message}")
            if (e.message?.startsWith("SESSION_") == true || e.message?.startsWith("FLAGGED_") == true) {
                // Re-throw our custom session errors
                Result.failure(e)
            } else {
                // For network/other errors, don't invalidate the session but fail the validation
                Result.failure(Exception("SESSION_CHECK_FAILED:Unable to verify session. Please check your connection and try again."))
            }
        }
    }
}