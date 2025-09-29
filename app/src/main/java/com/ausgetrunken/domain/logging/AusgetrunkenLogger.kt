package com.ausgetrunken.domain.logging

import android.util.Log
import com.ausgetrunken.BuildConfig

/**
 * Centralized logging system for Ausgetrunken
 *
 * Features:
 * - Automatic debug/release behavior
 * - Performance optimized (zero overhead in release)
 * - Structured logging with consistent format
 * - Ready for crash reporting integration
 * - Configurable log levels
 *
 * Usage:
 * ```
 * AusgetrunkenLogger.e("Tag", "Error message")
 * ```
 */
object AusgetrunkenLogger {

    /**
     * Log level configuration
     * Controls which logs are output based on build variant and runtime settings
     */
    enum class LogLevel(val priority: Int) {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR),
        NONE(Int.MAX_VALUE)
    }

    /**
     * Current minimum log level
     * - Debug builds: ERROR level only (for performance)
     * - Release builds: ERROR level only
     * - Can be overridden at runtime for debugging
     */
    private var minLogLevel: LogLevel = LogLevel.ERROR

    /**
     * Tag prefix for all Ausgetrunken logs
     * Makes it easy to filter logs in production crash reports
     */
    private const val TAG_PREFIX = "Ausgetrunken"

    /**
     * Set minimum log level at runtime
     * Useful for temporary debugging in specific scenarios
     */
    fun setLogLevel(level: LogLevel) {
        minLogLevel = level
    }

    /**
     * Get current log level configuration
     */
    fun getLogLevel(): LogLevel = minLogLevel

    /**
     * Check if a log level is enabled
     * Used internally for performance optimization
     */
    private fun isLoggable(level: LogLevel): Boolean {
        return level.priority >= minLogLevel.priority
    }

    /**
     * Format tag with consistent prefix
     */
    private fun formatTag(tag: String): String = "$TAG_PREFIX:$tag"

    /**
     * Debug level logging
     * Disabled by default for performance
     */
    fun d(tag: String, message: String) {
        if (isLoggable(LogLevel.DEBUG)) {
            Log.d(formatTag(tag), message)
        }
    }

    /**
     * Info level logging
     * Used for important application flow information
     */
    fun i(tag: String, message: String) {
        if (isLoggable(LogLevel.INFO)) {
            Log.i(formatTag(tag), message)
        }
    }

    /**
     * Warning level logging
     * Used for recoverable errors and potential issues
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isLoggable(LogLevel.WARN)) {
            if (throwable != null) {
                Log.w(formatTag(tag), message, throwable)
            } else {
                Log.w(formatTag(tag), message)
            }
        }
    }

    /**
     * Error level logging
     * Used for critical errors that affect application functionality
     * Always logged in all build variants
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isLoggable(LogLevel.ERROR)) {
            if (throwable != null) {
                Log.e(formatTag(tag), message, throwable)
            } else {
                Log.e(formatTag(tag), message)
            }
        }
    }

    /**
     * Verbose level logging
     * Only used for extremely detailed debugging
     * Disabled by default
     */
    fun v(tag: String, message: String) {
        if (isLoggable(LogLevel.VERBOSE)) {
            Log.v(formatTag(tag), message)
        }
    }


    /**
     * Structured logging for specific domains
     * Provides consistent formatting for different app areas
     */
    object Auth {
        private const val TAG = "Auth"

        fun loginAttempt(email: String) = i(TAG, "Login attempt for: ${email.take(3)}***")
        fun loginSuccess(userType: String) = i(TAG, "Login successful, user type: $userType")
        fun loginError(error: String) = e(TAG, "Login failed: $error")
        fun logoutSuccess() = i(TAG, "User logged out successfully")
    }

    object Database {
        private const val TAG = "Database"

        fun queryError(table: String, operation: String, error: String) =
            e(TAG, "Query failed: $operation on $table - $error")
    }

    object Network {
        private const val TAG = "Network"

        fun requestError(endpoint: String, error: String) =
            e(TAG, "HTTP error: $endpoint - $error")
    }

    object Photo {
        private const val TAG = "Photo"

        fun uploadSuccess(fileName: String, url: String) =
            i(TAG, "Photo upload successful: $fileName -> $url")
        fun uploadError(fileName: String, error: String) =
            e(TAG, "Photo upload failed: $fileName - $error")
    }
}