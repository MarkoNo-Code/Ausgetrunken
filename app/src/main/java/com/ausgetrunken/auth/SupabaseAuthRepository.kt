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
import com.ausgetrunken.domain.logging.AusgetrunkenLogger

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
            UserTypeDebugLogger.logRegistrationStart(email, userType)

            // Trim email to remove any whitespace that might cause issues
            val cleanEmail = email.trim().lowercase()

            // First, try to sign up with Supabase Auth
            auth.signUpWith(Email) {
                this.email = cleanEmail
                this.password = password
                // Store user type in metadata for later profile creation
                this.data = buildJsonObject {
                    put("user_type", userType.name)
                }
                UserTypeDebugLogger.logMetadataStorage(userType)
            }

            // Check if user was created successfully
            val user = auth.currentUserOrNull()

            AusgetrunkenLogger.d("Auth", "SignUp: Post-registration user check")
            // Removed println: "  - User exists: ${user != null}"
            if (user != null) {
                // Removed println: "  - User ID: ${user.id}"
                // Removed println: "  - User email: ${user.email}"
                // Removed println: "  - emailConfirmedAt: ${user.emailConfirmedAt}"
                // Removed println: "  - aud: ${user.aud}"
                // Removed println: "  - Full user object: $user"
            }

            // HYBRID APPROACH PART 1: IMMEDIATE PROFILE CREATION
            // Create profile immediately regardless of email confirmation status
            // This prevents trigger timing issues
            if (user != null) {
                UserTypeDebugLogger.logImmediateCreationAttempt(user.id, userType)
                try {
                    // Force profile creation with correct user type immediately
                    postgrest.from("user_profiles").insert(
                        buildJsonObject {
                            put("id", user.id)
                            put("email", cleanEmail)
                            put("user_type", userType.name)
                            put("profile_completed", false)
                            put("flagged_for_deletion", false)
                        }
                    )
                    UserTypeDebugLogger.logImmediateCreationSuccess(userType)
                } catch (immediateError: Exception) {
                    UserTypeDebugLogger.logImmediateCreationFailure(
                        immediateError.message ?: "Unknown error",
                        immediateError.message?.contains("duplicate key", ignoreCase = true) == true
                    )

                    // Check if profile exists and needs correction
                    if (immediateError.message?.contains("duplicate key", ignoreCase = true) == true) {
                        try {
                            // Get existing profile to check type
                            val existingProfile = postgrest.from("user_profiles")
                                .select {
                                    filter {
                                        eq("id", user.id)
                                    }
                                }
                                .decodeSingleOrNull<UserProfile>()

                            if (existingProfile != null && existingProfile.userType != userType.name) {
                                UserTypeDebugLogger.logImmediateCorrection(existingProfile.userType, userType.name)
                                postgrest.from("user_profiles")
                                    .update(
                                        buildJsonObject {
                                            put("user_type", userType.name)
                                            put("updated_at", Instant.now().toString())
                                        }
                                    ) {
                                        filter {
                                            eq("id", user.id)
                                        }
                                    }
                                // Removed println: "✅ HYBRID SOLUTION: Immediate correction successful - ${existingProfile.userType} → ${userType.name}"
                            }
                        } catch (correctionError: Exception) {
                            // Removed println: "❌ HYBRID SOLUTION: Immediate correction failed: ${correctionError.message}"
                        }
                    }
                }
            }

            if (user == null) {
                // Registration successful but user needs email confirmation
                // HYBRID APPROACH: Even without immediate user object, trigger will run
                // Our post-login backup will catch any issues
                // Removed println: "✅ SupabaseAuthRepository.signUp: User is null - email confirmation required"
                // Removed println: "✅ HYBRID SOLUTION: Post-login backup will ensure correct user type"
                return Result.failure(Exception("EMAIL_CONFIRMATION_REQUIRED:Registration successful! Please check your email for confirmation link before signing in."))
            }
            
            // Only create user profile if user is confirmed
            // Note: user.aud == "authenticated" doesn't mean email is confirmed, it just means they have a valid session
            // We should ONLY create profile if emailConfirmedAt is not null (actually confirmed)
            AusgetrunkenLogger.d("Auth", "SignUp: Email confirmation check")
            // Removed println: "  - emailConfirmedAt: ${user.emailConfirmedAt}"
            // Removed println: "  - aud: ${user.aud}"

            if (user.emailConfirmedAt != null) {
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
                        AusgetrunkenLogger.d("Auth", "SignUp: Creating user profile with type: ${userType.name}")
                        postgrest.from("user_profiles").insert(
                            buildJsonObject {
                                put("id", user.id)
                                put("email", cleanEmail)
                                put("user_type", userType.name)
                                put("profile_completed", false)
                            }
                        )
                        // Removed println: "✅ SupabaseAuthRepository.signUp: User profile created successfully with type: ${userType.name}"
                    } else {
                        // Removed println: "ℹ️ SupabaseAuthRepository.signUp: User profile already exists with type: ${existingProfile.userType}"

                        // PROACTIVE FIX: Always verify and correct user type, even if profile exists
                        if (existingProfile.userType != userType.name) {
                            println("🔄 SupabaseAuthRepository.signUp: Profile exists but has wrong user type, correcting...")
                            println("🔄 SupabaseAuthRepository.signUp: Profile type: ${existingProfile.userType}, Expected: ${userType.name}")

                            try {
                                postgrest.from("user_profiles")
                                    .update(
                                        buildJsonObject {
                                            put("user_type", userType.name)
                                            put("updated_at", Instant.now().toString())
                                        }
                                    ) {
                                        filter {
                                            eq("id", user.id)
                                        }
                                    }
                                // Removed println: "✅ SupabaseAuthRepository.signUp: Corrected user type from ${existingProfile.userType} to ${userType.name}"
                            } catch (correctionError: Exception) {
                                // Removed println: "❌ SupabaseAuthRepository.signUp: Failed to correct user type: ${correctionError.message}"
                            }
                        } else {
                            // Removed println: "✅ SupabaseAuthRepository.signUp: User type is already correct: ${existingProfile.userType}"
                        }
                    }
                } catch (dbError: Exception) {
                    // Check if this is a duplicate key error - means Supabase trigger already created profile
                    if (dbError.message?.contains("duplicate key", ignoreCase = true) == true) {
                        println("🔧 SupabaseAuthRepository.signUp: Profile already exists (likely created by Supabase trigger)")
                        println("🔧 SupabaseAuthRepository.signUp: Attempting to update existing profile with correct user type")

                        try {
                            // Update the existing profile with the correct user type
                            postgrest.from("user_profiles")
                                .update(
                                    buildJsonObject {
                                        put("user_type", userType.name)
                                        put("updated_at", Instant.now().toString())
                                    }
                                ) {
                                    filter {
                                        eq("id", user.id)
                                    }
                                }
                            // Removed println: "✅ SupabaseAuthRepository.signUp: Successfully updated existing profile with correct user type: ${userType.name}"
                        } catch (updateError: Exception) {
                            // Removed println: "❌ SupabaseAuthRepository.signUp: Failed to update existing profile: ${updateError.message}"
                            updateError.printStackTrace()
                        }
                    } else {
                        // ENHANCED: Always try to fix user type regardless of error type
                        AusgetrunkenLogger.e("Auth", "SignUp: Profile creation failed, attempting user type fix. Error: ${dbError.message}")

                        try {
                            // Check if profile exists and fix user type if wrong
                            val existingProfile = postgrest.from("user_profiles")
                                .select {
                                    filter {
                                        eq("id", user.id)
                                    }
                                }
                                .decodeSingleOrNull<UserProfile>()

                            if (existingProfile != null) {
                                println("🔍 SupabaseAuthRepository.signUp: Found existing profile with type: ${existingProfile.userType}")
                                println("🔍 SupabaseAuthRepository.signUp: Expected type: ${userType.name}")

                                if (existingProfile.userType != userType.name) {
                                    println("🔄 SupabaseAuthRepository.signUp: Fixing incorrect user type")
                                    postgrest.from("user_profiles")
                                        .update(
                                            buildJsonObject {
                                                put("user_type", userType.name)
                                                put("updated_at", Instant.now().toString())
                                            }
                                        ) {
                                            filter {
                                                eq("id", user.id)
                                            }
                                        }
                                    // Removed println: "✅ SupabaseAuthRepository.signUp: Fixed user type from ${existingProfile.userType} to ${userType.name}"
                                } else {
                                    // Removed println: "ℹ️ SupabaseAuthRepository.signUp: User type already correct: ${existingProfile.userType}"
                                }
                            }
                        } catch (fixError: Exception) {
                            // Removed println: "❌ SupabaseAuthRepository.signUp: Failed to fix user type: ${fixError.message}"
                        }

                        // Profile creation is critical - if it fails for other reasons, we should know about it
                        // Removed println: "❌ SupabaseAuthRepository.signUp: CRITICAL - Failed to create/check user profile: ${dbError.message}"
                        // Removed println: "❌ SupabaseAuthRepository.signUp: This may cause authentication issues later!"
                        dbError.printStackTrace()
                        // Still don't fail registration completely, but make it very obvious something went wrong
                    }
                }
            } else {
                // User needs email confirmation, profile will be created on first login after email confirmation
                AusgetrunkenLogger.i("Auth", "SignUp: User email not confirmed - profile creation deferred")
                throw Exception("Registration successful! Please check your email for confirmation link. Your profile will be created when you first sign in after confirming your email.")
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
                    // Removed println: "  - Email: ${existingProfile.email}"
                    // Removed println: "  - UserType: ${existingProfile.userType}"
                    // Removed println: "  - FlaggedForDeletion: ${existingProfile.flaggedForDeletion}"
                    // Removed println: "  - DeletionType: ${existingProfile.deletionType}"
                    // Removed println: "  - DeletionFlaggedAt: ${existingProfile.deletionFlaggedAt}"

                    // HYBRID APPROACH PART 2: POST-LOGIN BACKUP CORRECTION
                    // This is the self-healing backup system for the hybrid solution
                    UserTypeDebugLogger.logBackupSystemStart(user.id, user)

                    // ENHANCED: Use same robust extraction method
                    val metadataUserType = try {
                        user.userMetadata?.get("user_type") as? String
                            ?: user.userMetadata?.get("userType") as? String
                            ?: user.userMetadata?.get("type") as? String
                            ?: run {
                                val metadataStr = user.userMetadata.toString()
                                if (metadataStr.contains("user_type")) {
                                    val regex = """"user_type":\s*"([^"]+)"""".toRegex()
                                    regex.find(metadataStr)?.groupValues?.get(1)
                                } else null
                            }
                    } catch (e: Exception) {
                        // Removed println: "❌ HYBRID BACKUP: Metadata extraction error: ${e.message}"
                        null
                    }

                    UserTypeDebugLogger.logMetadataExtraction(user, metadataUserType)

                    // ENHANCED BACKUP LOGIC: More aggressive correction
                    if (metadataUserType != null && metadataUserType != existingProfile.userType) {
                        UserTypeDebugLogger.logTypeMismatchDetected(existingProfile.userType, metadataUserType)

                        try {
                            postgrest.from("user_profiles")
                                .update(
                                    buildJsonObject {
                                        put("user_type", metadataUserType)
                                        put("updated_at", Instant.now().toString())
                                        // Add a marker to track hybrid corrections (optional field)
                                        put("hybrid_corrected", true)
                                    }
                                ) {
                                    filter {
                                        eq("id", user.id)
                                    }
                                }
                            UserTypeDebugLogger.logBackupCorrectionSuccess(existingProfile.userType, metadataUserType)
                        } catch (updateError: Exception) {
                            UserTypeDebugLogger.logBackupCorrectionFailure(updateError.message ?: "Unknown error", user.email)
                        }
                    } else {
                        UserTypeDebugLogger.logNoMismatchFound(existingProfile.userType, metadataUserType)
                    }

                    // Check if account is flagged for deletion
                    if (existingProfile.flaggedForDeletion) {
                        // Removed println: "❌ SupabaseAuthRepository.signIn: Account IS flagged for deletion - blocking login"
                        
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
                        // Removed println: "✅ SupabaseAuthRepository.signIn: Account is NOT flagged for deletion - allowing login"
                        
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
                                // Removed println: "✅ SupabaseAuthRepository: Previous session invalidated - other devices will be logged out"
                            } else {
                                // Removed println: "✅ SupabaseAuthRepository: Session info updated"
                            }
                        } catch (sessionError: Exception) {
                            // Removed println: "❌ SupabaseAuthRepository: Failed to update session info: ${sessionError.message}"
                            // Don't fail login if session update fails, but log it
                        }
                    }
                } else {
                    // This should rarely happen - user signed in but no profile exists
                    // This could occur if registration profile creation failed but user was created in auth
                    UserTypeDebugLogger.logMissingProfile(user.id, user.email)

                    // Create profile for confirmed user with session tracking
                    // Extract user type from registration metadata or default to CUSTOMER
                    // Debug: Print all metadata to troubleshoot the user_type extraction issue
                    println("🔍 SupabaseAuthRepository: Full user metadata: ${user.userMetadata}")
                    println("🔍 SupabaseAuthRepository: Raw metadata keys: ${user.userMetadata?.keys}")

                    // FIXED: Enhanced metadata extraction with multiple approaches
                    val userTypeFromMetadata = try {
                        // Try direct access first
                        user.userMetadata?.get("user_type") as? String
                            ?: user.userMetadata?.get("userType") as? String
                            ?: user.userMetadata?.get("type") as? String
                            ?: run {
                                // Try alternative access methods
                                val metadataStr = user.userMetadata.toString()
                                println("🔍 HYBRID DEBUG: Raw metadata string: $metadataStr")

                                // Parse the metadata if it's in JSON string format
                                if (metadataStr.contains("user_type")) {
                                    val regex = """"user_type":\s*"([^"]+)"""".toRegex()
                                    regex.find(metadataStr)?.groupValues?.get(1)
                                } else null
                            }
                    } catch (e: Exception) {
                        // Removed println: "❌ HYBRID DEBUG: Metadata extraction error: ${e.message}"
                        null
                    }

                    val registeredUserType = userTypeFromMetadata ?: "CUSTOMER"

                    println("🔍 SupabaseAuthRepository: Extracted userType from metadata: '$userTypeFromMetadata'")
                    println("🔍 SupabaseAuthRepository: Final userType to use: '$registeredUserType'")

                    // CRITICAL: If we still couldn't extract the type but metadata exists, log for debugging
                    if (userTypeFromMetadata == null && user.userMetadata?.toString()?.contains("WINERY_OWNER") == true) {
                        println("🚨 HYBRID CRITICAL: Metadata contains WINERY_OWNER but extraction failed!")
                        println("🚨 HYBRID CRITICAL: This is a metadata parsing bug - defaulting to CUSTOMER")
                    }

                    postgrest.from("user_profiles").insert(
                        buildJsonObject {
                            put("id", user.id)
                            put("email", user.email ?: email)
                            put("user_type", registeredUserType)
                            put("profile_completed", false)
                            put("flagged_for_deletion", false)
                            put("current_session_id", newSessionId)
                            put("session_created_at", Instant.now().toString())
                            put("last_session_activity", Instant.now().toString())
                        }
                    )
                    // Removed println: "✅ SupabaseAuthRepository: User profile created with type: $registeredUserType"
                }
            } catch (dbError: Exception) {
                // If it's a flagged account error, re-throw it
                if (dbError.message?.contains("flagged for deletion") == true) {
                    throw dbError
                }
                // Log other errors but don't fail login
                // Removed println: "Warning: Could not create/check user profile: ${dbError.message}"
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
            AusgetrunkenLogger.i("Auth", "Starting logout process")
            
            // Get user info before clearing everything
            val sessionInfo = tokenStorage.getSessionInfo()
            
            // ALWAYS clear local session first to ensure logout succeeds locally
            println("🗑️ SupabaseAuthRepository: Clearing local session first")
            auth.signOut()
            tokenStorage.clearSession()
            // Removed println: "✅ SupabaseAuthRepository: Local session cleared successfully"
            
            // Then try to clear remote session (don't fail logout if this fails)
            if (sessionInfo != null) {
                try {
                    println("🗑️ SupabaseAuthRepository: Clearing remote session for user: ${sessionInfo.userId}")
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
                    // Removed println: "✅ SupabaseAuthRepository: Remote session cleared successfully"
                } catch (remoteError: Exception) {
                    // Removed println: "⚠️ SupabaseAuthRepository: Failed to clear remote session: ${remoteError.message}"
                    // Removed println: "⚠️ SupabaseAuthRepository: This is not critical - user is logged out locally"
                    // Don't fail logout if remote cleanup fails
                }
            } else {
                // Removed println: "⚠️ SupabaseAuthRepository: No session info found for remote cleanup"
            }
            
            // Removed println: "✅ SupabaseAuthRepository: Logout completed successfully"
            Result.success(Unit)
        } catch (e: Exception) {
            // Removed println: "❌ SupabaseAuthRepository: Logout failed: ${e.message}"
            
            // Even if logout fails, try to clear local session as fallback
            try {
                auth.signOut()
                tokenStorage.clearSession()
                // Removed println: "✅ SupabaseAuthRepository: Fallback local session clear completed"
            } catch (fallbackError: Exception) {
                // Removed println: "❌ SupabaseAuthRepository: Even fallback session clear failed: ${fallbackError.message}"
            }
            
            Result.failure(e)
        }
    }
    
    suspend fun restoreSession(): Result<UserInfo?> {
        println("🔄 SupabaseAuthRepository: Starting simple session restoration")
        val sessionInfo = tokenStorage.getSessionInfo()
        
        // Step 1: Check if we have local tokens
        if (sessionInfo == null) {
            // Removed println: "❌ SupabaseAuthRepository: No local session found"
            return Result.success(null)
        }
        
        if (!tokenStorage.isTokenValid()) {
            // Removed println: "❌ SupabaseAuthRepository: Local tokens expired"
            tokenStorage.clearSession()
            return Result.success(null)
        }
        
        // Removed println: "✅ SupabaseAuthRepository: Found valid local tokens for user: ${sessionInfo.userId}"
        
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
                // Removed println: "❌ SupabaseAuthRepository: User profile not found remotely"
                tokenStorage.clearSession()
                return Result.success(null)
            }
            
            // Check if account is flagged for deletion
            if (userProfile.flaggedForDeletion) {
                // Removed println: "❌ SupabaseAuthRepository: Account flagged for deletion"
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
            
            // Handle session ID comparison logic
            when {
                // Both sessions exist and don't match - user logged in elsewhere
                localSessionId != null && remoteSessionId != null && localSessionId != remoteSessionId -> {
                    // Removed println: "❌ SupabaseAuthRepository: Session mismatch - logged in elsewhere"
                    tokenStorage.clearSession()
                    return Result.failure(Exception("SESSION_INVALIDATED:You logged in from another device."))
                }
                // Remote session is null but we have local session - likely after logout, allow restoration
                localSessionId != null && remoteSessionId == null -> {
                    // Removed println: "⚠️ SupabaseAuthRepository: Remote session is null, local session exists - allowing restoration (post-logout case")
                    // This is normal after logout - the remote session was cleared but local tokens are still valid
                    // We'll restore the session and update the remote session ID below
                }
                // Both are null or match - proceed normally
                else -> {
                    // Removed println: "✅ SupabaseAuthRepository: Session comparison passed"
                }
            }
            
            // Step 4: Restore Supabase session
            // Removed println: "✅ SupabaseAuthRepository: Session IDs match, restoring Supabase session..."
            
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
                // Removed println: "✅ SupabaseAuthRepository: Session restored successfully for: ${currentUser.email}"
                
                // If remote session was null, update it with our local session ID
                if (localSessionId != null && remoteSessionId == null) {
                    try {
                        println("🔄 SupabaseAuthRepository: Updating remote session ID after successful restoration")
                        postgrest.from("user_profiles")
                            .update(
                                buildJsonObject {
                                    put("current_session_id", localSessionId)
                                    put("last_session_activity", Instant.now().toString())
                                    put("updated_at", Instant.now().toString())
                                }
                            ) {
                                filter {
                                    eq("id", sessionInfo.userId)
                                }
                            }
                        // Removed println: "✅ SupabaseAuthRepository: Remote session ID updated successfully"
                    } catch (updateError: Exception) {
                        // Removed println: "⚠️ SupabaseAuthRepository: Failed to update remote session ID: ${updateError.message}"
                        // Don't fail restoration if this update fails
                    }
                }
                
                return Result.success(currentUser)
            } else {
                // Removed println: "⚠️ SupabaseAuthRepository: Supabase session import failed, but tokens are valid"
                // Removed println: "⚠️ SupabaseAuthRepository: Session is valid based on token/remote comparison, will proceed without Supabase UserInfo"
                // The session is valid based on token comparison, but we couldn't import into Supabase
                // We'll return a special result that indicates valid session with minimal user data
                return Result.failure(Exception("VALID_SESSION_NO_USER:${sessionInfo.userId}:${userProfile.email}"))
            }
                
        } catch (e: Exception) {
            // Removed println: "❌ SupabaseAuthRepository: Database check failed: ${e.message}"
            
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
                        // Removed println: "✅ SupabaseAuthRepository: Token refresh successful!"
                        
                        // Update stored tokens with new ones
                        tokenStorage.saveLoginSession(
                            accessToken = currentSession.accessToken,
                            refreshToken = currentSession.refreshToken ?: sessionInfo.refreshToken,
                            userId = currentUser.id,
                            sessionId = sessionInfo.sessionId
                        )
                        
                        return Result.success(currentUser)
                    } else {
                        // Removed println: "❌ SupabaseAuthRepository: Token refresh failed, clearing session"
                        tokenStorage.clearSession()
                        return Result.success(null)
                    }
                    
                } catch (refreshError: Exception) {
                    // Removed println: "❌ SupabaseAuthRepository: Token refresh failed: ${refreshError.message}"
                    tokenStorage.clearSession()
                    return Result.success(null)
                }
            } else {
                // On other network errors, don't force logout - let user continue
                return Result.success(null)
            }
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
            
            println("🔍 SupabaseAuthRepository.getUserType: Database response:")
            // Removed println: "  - User ID: ${response.id}"
            // Removed println: "  - Email: ${response.email}"
            // Removed println: "  - User Type (raw: '${response.userType}'")
            // Removed println: "  - Flagged for deletion: ${response.flaggedForDeletion}"
            
            val userType = UserType.valueOf(response.userType)
            println("🔍 SupabaseAuthRepository.getUserType: Final UserType: $userType")
            Result.success(userType)
        } catch (e: Exception) {
            // Removed println: "❌ SupabaseAuthRepository.getUserType: Error getting user type: ${e.message}"
            // Removed println: "❌ SupabaseAuthRepository.getUserType: Error class: ${e.javaClass.simpleName}"
            e.printStackTrace()
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
    
    suspend fun resetPasswordForEmail(email: String): Result<Unit> {
        return try {
            val cleanEmail = email.trim().lowercase()
            
            auth.resetPasswordForEmail(
                email = cleanEmail,
                redirectUrl = "ausgetrunken://reset-password"
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Invalid email") == true -> 
                    "Please enter a valid email address."
                e.message?.contains("Email not confirmed") == true -> 
                    "Please confirm your email address first."
                else -> 
                    "Failed to send password reset email. Please try again."
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    suspend fun confirmPasswordReset(accessToken: String, newPassword: String): Result<Unit> {
        return try {
            // First, restore the session using the recovery token
            auth.retrieveUser(accessToken)

            // Now update the password
            auth.updateUser {
                password = newPassword
            }

            // Sign out after password update so user has to log in with new password
            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Invalid token") == true ->
                    "Reset link has expired. Please request a new password reset."
                e.message?.contains("weak password") == true ->
                    "Password is too weak. Please choose a stronger password."
                else ->
                    "Failed to update password. Please try again."
            }
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun updateUserEmail(newEmail: String): Result<String> {
        return try {
            val user = auth.currentUserOrNull()
                ?: return Result.failure(Exception("No authenticated user found"))

            val cleanEmail = newEmail.trim().lowercase()

            // Validate email format
            if (!isValidEmail(cleanEmail)) {
                return Result.failure(Exception("Please enter a valid email address"))
            }

            // Check if it's the same email
            if (user.email == cleanEmail) {
                return Result.failure(Exception("This is already your current email address"))
            }

            println("📧 SupabaseAuthRepository.updateUserEmail: Attempting to update email from '${user.email}' to '$cleanEmail'")

            // Attempt to update email in Supabase Auth
            auth.updateUser {
                email = cleanEmail
            }

            // Get the updated user to check the status
            val updatedUser = auth.currentUserOrNull()

            if (updatedUser != null) {
                // Removed println: "✅ SupabaseAuthRepository.updateUserEmail: Email update initiated successfully"
                println("📧 SupabaseAuthRepository.updateUserEmail: Current user email: ${updatedUser.email}")
                println("📧 SupabaseAuthRepository.updateUserEmail: Email confirmed at: ${updatedUser.emailConfirmedAt}")

                // If email confirmation is enabled, the email won't change until confirmed
                // But we should update our database optimistically or wait for confirmation
                val confirmationRequired = updatedUser.email == user.email // Email hasn't changed yet

                if (confirmationRequired) {
                    // Email confirmation required - don't update database yet
                    println("📧 SupabaseAuthRepository.updateUserEmail: Email confirmation required - confirmation emails sent")
                    Result.success("Email update initiated! Please check both your current and new email addresses for confirmation links. Your email will be updated once confirmed.")
                } else {
                    // Email updated immediately - update database
                    println("📧 SupabaseAuthRepository.updateUserEmail: Email updated immediately - updating database")
                    try {
                        postgrest.from("user_profiles")
                            .update(
                                buildJsonObject {
                                    put("email", cleanEmail)
                                    put("updated_at", Instant.now().toString())
                                }
                            ) {
                                filter {
                                    eq("id", user.id)
                                }
                            }
                        // Removed println: "✅ SupabaseAuthRepository.updateUserEmail: Database updated successfully"
                        Result.success("Email updated successfully to $cleanEmail")
                    } catch (dbError: Exception) {
                        // Removed println: "❌ SupabaseAuthRepository.updateUserEmail: Failed to update database: ${dbError.message}"
                        // Email was updated in auth but not in database - this is problematic
                        Result.success("Email updated in authentication but failed to update profile. Please contact support.")
                    }
                }
            } else {
                Result.failure(Exception("Failed to update email - user session lost"))
            }

        } catch (e: Exception) {
            // Removed println: "❌ SupabaseAuthRepository.updateUserEmail: Error updating email: ${e.message}"

            val errorMessage = when {
                e.message?.contains("User already registered", ignoreCase = true) == true ||
                e.message?.contains("email already exists", ignoreCase = true) == true ||
                e.message?.contains("duplicate", ignoreCase = true) == true ->
                    "This email address is already in use by another account"
                e.message?.contains("Invalid email", ignoreCase = true) == true ->
                    "Please enter a valid email address"
                e.message?.contains("rate limit", ignoreCase = true) == true ->
                    "Too many attempts. Please wait a moment before trying again"
                e.message?.contains("not authorized", ignoreCase = true) == true ->
                    "You are not authorized to perform this action. Please sign in again"
                else ->
                    "Failed to update email: ${e.message}"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
        return email.matches(emailPattern.toRegex())
    }

    suspend fun handleEmailChangeConfirmation(): Result<Unit> {
        return try {
            // This method can be called when the app receives a deep link from email confirmation
            // or when checking if a pending email change has been confirmed

            val user = auth.currentUserOrNull()
                ?: return Result.failure(Exception("No authenticated user found"))

            // Refresh user data to get latest email status
            auth.refreshCurrentSession()
            val refreshedUser = auth.currentUserOrNull()

            if (refreshedUser?.email != null) {
                // Update database with confirmed email
                try {
                    postgrest.from("user_profiles")
                        .update(
                            buildJsonObject {
                                put("email", refreshedUser.email!!)
                                put("updated_at", Instant.now().toString())
                            }
                        ) {
                            filter {
                                eq("id", refreshedUser.id)
                            }
                        }
                    // Removed println: "✅ SupabaseAuthRepository.handleEmailChangeConfirmation: Email confirmed and database updated"
                    Result.success(Unit)
                } catch (dbError: Exception) {
                    // Removed println: "❌ SupabaseAuthRepository.handleEmailChangeConfirmation: Failed to update database: ${dbError.message}"
                    Result.failure(Exception("Email confirmed but failed to update profile"))
                }
            } else {
                Result.failure(Exception("Email confirmation not yet completed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to handle email confirmation: ${e.message}"))
        }
    }
    
}