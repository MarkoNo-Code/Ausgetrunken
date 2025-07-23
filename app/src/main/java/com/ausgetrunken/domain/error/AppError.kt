package com.ausgetrunken.domain.error

import kotlinx.coroutines.CancellationException

/**
 * Comprehensive error handling system for the application.
 * 
 * Design principles:
 * - All repository operations return Result<Data, AppError>
 * - Errors are properly categorized with user-friendly messages
 * - Includes metadata for proper error recovery and analytics
 * - Supports localization and contextual information
 */
sealed class AppError(
    open val message: String,
    open val userMessage: String = message,
    open val errorCode: String? = null,
    open val cause: Throwable? = null,
    open val metadata: Map<String, Any> = emptyMap()
) {
    
    /**
     * Authentication and authorization errors
     */
    sealed class AuthError(
        override val message: String,
        override val userMessage: String = message,
        override val errorCode: String? = null,
        override val cause: Throwable? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : AppError(message, userMessage, errorCode, cause, metadata) {
        
        object NotAuthenticated : AuthError(
            message = "User not authenticated",
            userMessage = "Please sign in to continue",
            errorCode = "AUTH_001"
        )
        
        object SessionExpired : AuthError(
            message = "Session expired",
            userMessage = "Your session has expired. Please sign in again.",
            errorCode = "AUTH_002"
        )
        
        object SessionInvalidated : AuthError(
            message = "Session invalidated by another device",
            userMessage = "You have been signed out because you signed in from another device",
            errorCode = "AUTH_003"
        )
        
        data class AccountFlagged(
            val reason: String,
            val flagType: String = "UNKNOWN"
        ) : AuthError(
            message = "Account flagged: $reason",
            userMessage = "Account access restricted: $reason",
            errorCode = "AUTH_004",
            metadata = mapOf("flagType" to flagType, "reason" to reason)
        )
        
        data class PermissionDenied(
            val action: String,
            val requiredPermission: String? = null
        ) : AuthError(
            message = "Permission denied for action: $action",
            userMessage = "You don't have permission to perform this action",
            errorCode = "AUTH_005",
            metadata = mapOf("action" to action, "requiredPermission" to (requiredPermission ?: "unknown"))
        )
        
        data class InvalidCredentials(
            val field: String? = null
        ) : AuthError(
            message = "Invalid credentials provided",
            userMessage = "Invalid email or password",
            errorCode = "AUTH_006",
            metadata = mapOf("field" to (field ?: "credentials"))
        )
        
        data class TokenError(
            val tokenType: String,
            val reason: String
        ) : AuthError(
            message = "Token error: $reason",
            userMessage = "Authentication failed. Please try signing in again.",
            errorCode = "AUTH_007",
            metadata = mapOf("tokenType" to tokenType, "reason" to reason)
        )
    }
    
    /**
     * Network and connectivity errors
     */
    sealed class NetworkError(
        override val message: String,
        override val userMessage: String = message,
        override val errorCode: String? = null,
        override val cause: Throwable? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : AppError(message, userMessage, errorCode, cause, metadata) {
        
        object NoInternet : NetworkError(
            message = "No internet connection",
            userMessage = "Please check your internet connection and try again",
            errorCode = "NET_001"
        )
        
        object Timeout : NetworkError(
            message = "Request timed out",
            userMessage = "The request is taking too long. Please try again",
            errorCode = "NET_002"
        )
        
        object ServerUnavailable : NetworkError(
            message = "Server temporarily unavailable",
            userMessage = "Our servers are temporarily unavailable. Please try again later",
            errorCode = "NET_003"
        )
        
        data class HttpError(
            val statusCode: Int,
            val error: String,
            val endpoint: String? = null
        ) : NetworkError(
            message = "HTTP $statusCode: $error",
            userMessage = when (statusCode) {
                400 -> "Invalid request. Please check your input"
                401 -> "Authentication required"
                403 -> "Access forbidden"
                404 -> "Requested resource not found"
                409 -> "Conflict with current state"
                422 -> "Invalid data provided"
                429 -> "Too many requests. Please try again later"
                in 500..599 -> "Server error. Please try again later"
                else -> "Network error occurred"
            },
            errorCode = "NET_004",
            metadata = mapOf(
                "statusCode" to statusCode,
                "error" to error,
                "endpoint" to (endpoint ?: "unknown")
            )
        )
        
        data class ParseError(
            val responseBody: String? = null
        ) : NetworkError(
            message = "Failed to parse server response",
            userMessage = "Unexpected server response. Please try again",
            errorCode = "NET_005",
            metadata = mapOf("responseBody" to (responseBody ?: "empty"))
        )
    }
    
    /**
     * Data and business logic errors
     */
    sealed class DataError(
        override val message: String,
        override val userMessage: String = message,
        override val errorCode: String? = null,
        override val cause: Throwable? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : AppError(message, userMessage, errorCode, cause, metadata) {
        
        data class NotFound(
            val resource: String,
            val id: String? = null
        ) : DataError(
            message = "$resource not found${id?.let { " (ID: $it)" } ?: ""}",
            userMessage = "The requested $resource could not be found",
            errorCode = "DATA_001",
            metadata = mapOf("resource" to resource, "id" to (id ?: "unknown"))
        )
        
        data class ValidationError(
            val field: String,
            val reason: String,
            val value: Any? = null
        ) : DataError(
            message = "Validation failed for $field: $reason",
            userMessage = "$field: $reason",
            errorCode = "DATA_002",
            metadata = mapOf("field" to field, "reason" to reason, "value" to (value ?: "null"))
        )
        
        data class ConflictError(
            val resource: String,
            val conflictReason: String
        ) : DataError(
            message = "Conflict with $resource: $conflictReason",
            userMessage = "This action conflicts with existing data: $conflictReason",
            errorCode = "DATA_003",
            metadata = mapOf("resource" to resource, "conflictReason" to conflictReason)
        )
        
        data class SyncError(
            val operation: String,
            val details: String
        ) : DataError(
            message = "Sync failed for $operation: $details",
            userMessage = "Failed to sync data. Some changes may not be saved",
            errorCode = "DATA_004",
            metadata = mapOf("operation" to operation, "details" to details)
        )
        
        data class StorageError(
            val operation: String,
            val storageType: String = "database"
        ) : DataError(
            message = "Storage operation failed: $operation",
            userMessage = "Failed to save data. Please try again",
            errorCode = "DATA_005",
            metadata = mapOf("operation" to operation, "storageType" to storageType)
        )
        
        data class CorruptedData(
            val dataType: String,
            val reason: String
        ) : DataError(
            message = "Corrupted $dataType data: $reason",
            userMessage = "Data appears to be corrupted. Please refresh and try again",
            errorCode = "DATA_006",
            metadata = mapOf("dataType" to dataType, "reason" to reason)
        )
    }
    
    /**
     * Application and system errors
     */
    sealed class SystemError(
        override val message: String,
        override val userMessage: String = message,
        override val errorCode: String? = null,
        override val cause: Throwable? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : AppError(message, userMessage, errorCode, cause, metadata) {
        
        data class ConfigurationError(
            val component: String,
            val issue: String
        ) : SystemError(
            message = "Configuration error in $component: $issue",
            userMessage = "App configuration error. Please restart the app",
            errorCode = "SYS_001",
            metadata = mapOf("component" to component, "issue" to issue)
        )
        
        data class FeatureUnavailable(
            val feature: String,
            val reason: String = "temporarily disabled"
        ) : SystemError(
            message = "Feature '$feature' is unavailable: $reason",
            userMessage = "This feature is currently unavailable",
            errorCode = "SYS_002",
            metadata = mapOf("feature" to feature, "reason" to reason)
        )
        
        data class ResourceExhausted(
            val resource: String,
            val limit: String? = null
        ) : SystemError(
            message = "Resource exhausted: $resource${limit?.let { " (limit: $it)" } ?: ""}",
            userMessage = "Resource limit reached. Please try again later",
            errorCode = "SYS_003",
            metadata = mapOf("resource" to resource, "limit" to (limit ?: "unknown"))
        )
    }
    
    /**
     * Unknown or unexpected errors
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null,
        val context: String? = null
    ) : AppError(
        message = message,
        userMessage = "An unexpected error occurred",
        errorCode = "UNK_001",
        cause = cause,
        metadata = mapOf("context" to (context ?: "unknown"))
    )
    
    /**
     * Check if this error requires user authentication
     */
    val requiresAuthentication: Boolean
        get() = this is AuthError
    
    /**
     * Check if this error can be retried
     */
    val canRetry: Boolean
        get() = when (this) {
            is AuthError.NotAuthenticated,
            is AuthError.SessionExpired,
            is AuthError.SessionInvalidated,
            is AuthError.AccountFlagged,
            is AuthError.PermissionDenied,
            is AuthError.InvalidCredentials -> false
            is NetworkError.NoInternet,
            is NetworkError.Timeout,
            is NetworkError.ServerUnavailable -> true
            is NetworkError.HttpError -> statusCode in 500..599 || statusCode == 429
            is DataError.NotFound,
            is DataError.ValidationError,
            is DataError.ConflictError -> false
            is DataError.SyncError,
            is DataError.StorageError,
            is DataError.CorruptedData -> true
            is SystemError.ConfigurationError -> false
            is SystemError.FeatureUnavailable -> false
            is SystemError.ResourceExhausted -> true
            is UnknownError -> true
            else -> true
        }
    
    /**
     * Get user-friendly error category for analytics
     */
    val category: String
        get() = when (this) {
            is AuthError -> "Authentication"
            is NetworkError -> "Network"
            is DataError -> "Data"
            is SystemError -> "System"
            is UnknownError -> "Unknown"
        }
}

/**
 * Comprehensive exception to AppError mapping
 */
fun Throwable.toAppError(context: String? = null): AppError {
    // Don't convert CancellationException to AppError
    if (this is CancellationException) throw this
    
    return when (this) {
        // Network errors
        is java.net.UnknownHostException -> AppError.NetworkError.NoInternet
        is java.net.SocketTimeoutException -> AppError.NetworkError.Timeout
        is java.net.ConnectException -> AppError.NetworkError.ServerUnavailable
        is java.net.SocketException -> AppError.NetworkError.NoInternet
        
        // Parse existing error messages for authentication errors
        is Exception -> {
            val errorMessage = message ?: ""
            when {
                errorMessage.contains("FLAGGED_ACCOUNT:", ignoreCase = true) -> {
                    val reason = errorMessage.substringAfter("FLAGGED_ACCOUNT:")
                    AppError.AuthError.AccountFlagged(reason)
                }
                errorMessage.contains("SESSION_INVALIDATED:", ignoreCase = true) -> {
                    AppError.AuthError.SessionInvalidated
                }
                errorMessage.contains("SESSION_EXPIRED:", ignoreCase = true) -> {
                    AppError.AuthError.SessionExpired
                }
                errorMessage.contains("not authenticated", ignoreCase = true) -> {
                    AppError.AuthError.NotAuthenticated
                }
                errorMessage.contains("permission denied", ignoreCase = true) -> {
                    AppError.AuthError.PermissionDenied("unknown action")
                }
                else -> AppError.UnknownError(
                    message = errorMessage.ifEmpty { "Unknown error occurred" },
                    cause = this,
                    context = context
                )
            }
        }
        
        else -> AppError.UnknownError(
            message = message ?: "Unknown error occurred",
            cause = this,
            context = context
        )
    }
}