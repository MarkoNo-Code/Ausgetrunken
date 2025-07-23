package com.ausgetrunken.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.common.AppResult
import com.ausgetrunken.domain.error.AppError
import com.ausgetrunken.domain.error.toAppError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive base ViewModel with robust error handling, lifecycle management,
 * and common UI state patterns.
 * 
 * Features:
 * - Centralized error handling with user-friendly messages
 * - Loading state management with operation tracking
 * - Retry mechanism for failed operations
 * - Automatic exception handling and logging
 * - Lifecycle-aware operation management
 * - Analytics and debugging support
 */
abstract class BaseViewModel : ViewModel() {
    
    // Error state management
    private val _errorState = MutableStateFlow<UiErrorState?>(null)
    val errorState: StateFlow<UiErrorState?> = _errorState.asStateFlow()
    
    // Loading state management
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    // Operation tracking for advanced loading states
    private val activeOperations = MutableStateFlow<Set<String>>(emptySet())
    private val operationCounter = AtomicInteger(0)
    
    // Last failed operation for retry functionality
    private var lastFailedOperation: (suspend () -> Unit)? = null
    
    // Exception handler for uncaught coroutine exceptions
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        handleError(exception.toAppError("uncaught exception"), "CoroutineExceptionHandler")
    }
    
    /**
     * Centralized error handling with intelligent error mapping and user messages
     */
    protected fun handleError(error: AppError, context: String = "") {
        val uiErrorState = mapErrorToUiState(error)
        _errorState.value = uiErrorState
        
        // Enhanced logging with context and error details
        logError(error, context)
        
        // Analytics tracking could be added here
        trackErrorForAnalytics(error, context)
    }
    
    /**
     * Map AppError to UI-appropriate error states
     */
    private fun mapErrorToUiState(error: AppError): UiErrorState {
        return when (error) {
            is AppError.AuthError -> when (error) {
                AppError.AuthError.NotAuthenticated -> 
                    UiErrorState.Authentication(error.userMessage, requiresNavigation = true)
                AppError.AuthError.SessionExpired -> 
                    UiErrorState.Authentication(error.userMessage, requiresNavigation = true)
                AppError.AuthError.SessionInvalidated -> 
                    UiErrorState.Authentication(error.userMessage, requiresNavigation = true)
                is AppError.AuthError.AccountFlagged -> 
                    UiErrorState.Authentication(error.userMessage, requiresNavigation = true)
                is AppError.AuthError.PermissionDenied -> 
                    UiErrorState.Permission(error.userMessage, error.action)
                is AppError.AuthError.InvalidCredentials -> 
                    UiErrorState.Validation(error.userMessage, error.field)
                is AppError.AuthError.TokenError -> 
                    UiErrorState.Authentication(error.userMessage, requiresNavigation = true)
            }
            
            is AppError.NetworkError -> when (error) {
                AppError.NetworkError.NoInternet -> 
                    UiErrorState.Network(error.userMessage, canRetry = true, retryDelayMs = 2000)
                AppError.NetworkError.Timeout -> 
                    UiErrorState.Network(error.userMessage, canRetry = true, retryDelayMs = 1000)
                AppError.NetworkError.ServerUnavailable -> 
                    UiErrorState.Network(error.userMessage, canRetry = true, retryDelayMs = 5000)
                is AppError.NetworkError.HttpError -> 
                    UiErrorState.Network(error.userMessage, canRetry = error.canRetry, retryDelayMs = 2000)
                is AppError.NetworkError.ParseError -> 
                    UiErrorState.Data(error.userMessage, canRetry = false)
            }
            
            is AppError.DataError -> when (error) {
                is AppError.DataError.NotFound -> 
                    UiErrorState.Data(error.userMessage, canRetry = false)
                is AppError.DataError.ValidationError -> 
                    UiErrorState.Validation(error.userMessage, error.field)
                is AppError.DataError.ConflictError -> 
                    UiErrorState.Data(error.userMessage, canRetry = false)
                is AppError.DataError.SyncError -> 
                    UiErrorState.Data(error.userMessage, canRetry = true)
                is AppError.DataError.StorageError -> 
                    UiErrorState.Data(error.userMessage, canRetry = true)
                is AppError.DataError.CorruptedData -> 
                    UiErrorState.Data(error.userMessage, canRetry = true)
            }
            
            is AppError.SystemError -> when (error) {
                is AppError.SystemError.ConfigurationError -> 
                    UiErrorState.System(error.userMessage, canRetry = false)
                is AppError.SystemError.FeatureUnavailable -> 
                    UiErrorState.System(error.userMessage, canRetry = false)
                is AppError.SystemError.ResourceExhausted -> 
                    UiErrorState.System(error.userMessage, canRetry = true, retryDelayMs = 10000)
            }
            
            is AppError.UnknownError -> 
                UiErrorState.Unknown(error.userMessage, canRetry = true)
        }
    }
    
    /**
     * Enhanced error logging with structured information
     */
    private fun logError(error: AppError, context: String) {
        val contextInfo = if (context.isNotEmpty()) " [$context]" else ""
        val viewModelName = this::class.simpleName
        
        println("❌ $viewModelName$contextInfo")
        println("   Error Code: ${error.errorCode}")
        println("   Message: ${error.message}")
        println("   User Message: ${error.userMessage}")
        println("   Category: ${error.category}")
        println("   Can Retry: ${error.canRetry}")
        
        if (error.metadata.isNotEmpty()) {
            println("   Metadata: ${error.metadata}")
        }
        
        error.cause?.printStackTrace()
    }
    
    /**
     * Track errors for analytics (placeholder for future implementation)
     */
    private fun trackErrorForAnalytics(error: AppError, context: String) {
        // Future: Send to analytics service
        // Analytics.track("error_occurred", mapOf(
        //     "error_code" to error.errorCode,
        //     "error_category" to error.category,
        //     "context" to context,
        //     "view_model" to this::class.simpleName
        // ))
    }
    
    /**
     * Clear current error state
     */
    fun clearError() {
        _errorState.value = null
        lastFailedOperation = null
    }
    
    /**
     * Retry the last failed operation if available
     */
    fun retryLastOperation() {
        lastFailedOperation?.let { operation ->
            executeOperation("retry") {
                operation()
                AppResult.success(Unit)
            }
        }
    }
    
    /**
     * Check if a specific operation is currently loading
     */
    fun isOperationLoading(operationId: String): Boolean {
        return activeOperations.value.contains(operationId)
    }
    
    /**
     * Execute any operation with comprehensive error handling and state management
     */
    protected fun <T> executeOperation(
        operationId: String = "operation_${operationCounter.incrementAndGet()}",
        showGlobalLoading: Boolean = true,
        onSuccess: suspend (T) -> Unit = {},
        onError: (AppError) -> Unit = {},
        operation: suspend () -> AppResult<T>
    ): Job {
        return viewModelScope.launch(exceptionHandler) {
            try {
                // Start loading state
                startOperation(operationId, showGlobalLoading)
                clearError()
                
                // Execute the operation
                val result = operation()
                
                result.fold(
                    onSuccess = { data ->
                        onSuccess(data)
                        lastFailedOperation = null
                    },
                    onFailure = { error ->
                        handleError(error, operationId)
                        onError(error)
                        
                        // Store operation for retry if it can be retried
                        if (error.canRetry) {
                            lastFailedOperation = { operation() }
                        }
                    }
                )
                
            } catch (e: Exception) {
                val appError = e.toAppError(operationId)
                handleError(appError, operationId)
                onError(appError)
            } finally {
                stopOperation(operationId, showGlobalLoading)
            }
        }
    }
    
    /**
     * Execute operation with AppResult - simplified version for common cases
     */
    protected fun <T> execute(
        context: String = "",
        showLoading: Boolean = true,
        operation: suspend () -> AppResult<T>
    ): Job = executeOperation(
        operationId = context.ifEmpty { "operation_${operationCounter.incrementAndGet()}" },
        showGlobalLoading = showLoading,
        operation = operation
    )
    
    /**
     * Start tracking an operation
     */
    private fun startOperation(operationId: String, showGlobalLoading: Boolean) {
        activeOperations.value = activeOperations.value + operationId
        
        if (showGlobalLoading) {
            _loadingState.value = true
        }
    }
    
    /**
     * Stop tracking an operation
     */
    private fun stopOperation(operationId: String, showGlobalLoading: Boolean) {
        activeOperations.value = activeOperations.value - operationId
        
        if (showGlobalLoading && activeOperations.value.isEmpty()) {
            _loadingState.value = false
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up any remaining operations
        activeOperations.value = emptySet()
        _loadingState.value = false
    }
}

/**
 * Enhanced UI error states with more detailed information for better UX
 */
sealed class UiErrorState(
    val message: String,
    open val canRetry: Boolean = true,
    open val retryDelayMs: Long = 0,
    open val requiresNavigation: Boolean = false
) {
    
    data class Authentication(
        val userMessage: String,
        override val requiresNavigation: Boolean = true
    ) : UiErrorState(userMessage, canRetry = false, requiresNavigation = requiresNavigation)
    
    data class Network(
        val userMessage: String,
        override val canRetry: Boolean = true,
        override val retryDelayMs: Long = 0
    ) : UiErrorState(userMessage, canRetry, retryDelayMs)
    
    data class Data(
        val userMessage: String,
        override val canRetry: Boolean = true
    ) : UiErrorState(userMessage, canRetry)
    
    data class Permission(
        val userMessage: String,
        val action: String
    ) : UiErrorState(userMessage, canRetry = false)
    
    data class Validation(
        val userMessage: String,
        val field: String? = null
    ) : UiErrorState(userMessage, canRetry = false)
    
    data class System(
        val userMessage: String,
        override val canRetry: Boolean = false,
        override val retryDelayMs: Long = 0
    ) : UiErrorState(userMessage, canRetry, retryDelayMs)
    
    data class Unknown(
        val userMessage: String,
        override val canRetry: Boolean = true
    ) : UiErrorState(userMessage, canRetry)
    
    /**
     * Get the error type as a string for UI purposes
     */
    val type: String
        get() = when (this) {
            is Authentication -> "Authentication"
            is Network -> "Network"
            is Data -> "Data"
            is Permission -> "Permission" 
            is Validation -> "Validation"
            is System -> "System"
            is Unknown -> "Unknown"
        }
}