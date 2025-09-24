package com.ausgetrunken.auth

import com.ausgetrunken.data.local.entities.UserType
import io.github.jan.supabase.gotrue.user.UserInfo
import java.time.Instant

/**
 * Centralized logging utility for debugging user type assignment issues
 * This helps track the hybrid solution's behavior and identify any remaining issues
 */
object UserTypeDebugLogger {

    private const val TAG = "🔍 USER_TYPE_DEBUG"

    fun logRegistrationStart(email: String, requestedType: UserType) {
        println("$TAG: ========== REGISTRATION START ==========")
        println("$TAG: Email: $email")
        println("$TAG: Requested UserType: ${requestedType.name}")
        println("$TAG: Timestamp: ${Instant.now()}")
        println("$TAG: Using HYBRID SOLUTION for maximum reliability")
    }

    fun logMetadataStorage(userType: UserType) {
        println("$TAG: Metadata storage:")
        println("$TAG:   - Storing 'user_type': '${userType.name}' in raw_user_meta_data")
        println("$TAG:   - This will be used by both immediate creation and backup correction")
    }

    fun logImmediateCreationAttempt(userId: String, userType: UserType) {
        println("$TAG: ========== IMMEDIATE PROFILE CREATION ==========")
        println("$TAG: HYBRID PART 1: Attempting immediate profile creation")
        println("$TAG: User ID: $userId")
        println("$TAG: Target UserType: ${userType.name}")
        println("$TAG: Goal: Prevent trigger from creating wrong profile")
    }

    fun logImmediateCreationSuccess(userType: UserType) {
        println("$TAG: ✅ IMMEDIATE CREATION SUCCESS!")
        println("$TAG: Profile created with correct type: ${userType.name}")
        println("$TAG: Hybrid solution Part 1 completed successfully")
    }

    fun logImmediateCreationFailure(error: String, attempting_correction: Boolean) {
        println("$TAG: ⚠️ IMMEDIATE CREATION FAILED: $error")
        if (attempting_correction) {
            println("$TAG: Attempting immediate correction of existing profile...")
        } else {
            println("$TAG: Will rely on backup correction during login")
        }
    }

    fun logImmediateCorrection(existingType: String, targetType: String) {
        println("$TAG: 🔄 IMMEDIATE CORRECTION NEEDED")
        println("$TAG: Existing type: $existingType")
        println("$TAG: Target type: $targetType")
        println("$TAG: Correcting immediately to prevent wrong user type")
    }

    fun logBackupSystemStart(userId: String, userInfo: UserInfo) {
        println("$TAG: ========== BACKUP VERIFICATION SYSTEM ==========")
        println("$TAG: HYBRID PART 2: Post-login backup correction")
        println("$TAG: User ID: $userId")
        println("$TAG: Email: ${userInfo.email}")
        println("$TAG: Checking for any user type mismatches...")
    }

    fun logMetadataExtraction(userInfo: UserInfo, extractedType: String?) {
        println("$TAG: Metadata extraction:")
        println("$TAG:   - Full metadata: ${userInfo.userMetadata}")
        println("$TAG:   - Extracted user_type: '$extractedType'")
        println("$TAG:   - Metadata keys available: ${userInfo.userMetadata?.keys}")
    }

    fun logTypeMismatchDetected(profileType: String, metadataType: String) {
        println("$TAG: 🚨 CRITICAL: USER TYPE MISMATCH DETECTED!")
        println("$TAG: Profile has: $profileType")
        println("$TAG: Metadata has: $metadataType")
        println("$TAG: This indicates the immediate creation failed or trigger overwrote")
        println("$TAG: Initiating emergency backup correction...")
    }

    fun logBackupCorrectionSuccess(oldType: String, newType: String) {
        println("$TAG: ✅ BACKUP CORRECTION SUCCESS!")
        println("$TAG: Corrected: $oldType → $newType")
        println("$TAG: 🎉 HYBRID SOLUTION: Self-healing system worked!")
        println("$TAG: User now has correct type despite trigger issues")
    }

    fun logBackupCorrectionFailure(error: String, userEmail: String?) {
        println("$TAG: ❌ BACKUP CORRECTION FAILED!")
        println("$TAG: Error: $error")
        println("$TAG: User email: $userEmail")
        println("$TAG: 🚨 CRITICAL: Manual intervention may be required")
        println("$TAG: Both immediate and backup correction failed")
    }

    fun logNoMismatchFound(profileType: String, metadataType: String?) {
        if (metadataType != null) {
            println("$TAG: ✅ NO MISMATCH: Types are consistent")
            println("$TAG: Profile: $profileType, Metadata: $metadataType")
            println("$TAG: Hybrid solution working correctly")
        } else {
            println("$TAG: ⚠️ METADATA MISSING: Cannot verify user type")
            println("$TAG: Profile type: $profileType")
            println("$TAG: This may indicate an older user or metadata loss")
        }
    }

    fun logLoginComplete(finalUserType: String, wasCorrection: Boolean) {
        println("$TAG: ========== LOGIN VERIFICATION COMPLETE ==========")
        println("$TAG: Final user type: $finalUserType")
        if (wasCorrection) {
            println("$TAG: 🔧 Correction was applied during this login")
        } else {
            println("$TAG: ✅ No correction needed - type was already correct")
        }
        println("$TAG: User can now proceed with correct permissions")
    }

    fun logMissingProfile(userId: String, userEmail: String?) {
        println("$TAG: ⚠️ MISSING PROFILE DETECTED")
        println("$TAG: User ID: $userId")
        println("$TAG: Email: $userEmail")
        println("$TAG: This indicates complete profile creation failure")
        println("$TAG: Will attempt to create profile from metadata")
    }

    fun logTriggerAnalysis(profileCreationTime: String?, userCreationTime: String?) {
        println("$TAG: ========== TRIGGER TIMING ANALYSIS ==========")
        println("$TAG: Profile created: $profileCreationTime")
        println("$TAG: User created: $userCreationTime")
        // Could add logic to compare times and determine if trigger was involved
    }
}