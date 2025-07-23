package com.ausgetrunken.domain.common

import com.ausgetrunken.domain.error.AppError
import com.ausgetrunken.domain.error.toAppError
import kotlinx.coroutines.CancellationException

/**
 * Robust result wrapper for the application.
 * 
 * Design principles:
 * - Consistent error handling across all layers
 * - Type-safe success/failure handling
 * - Composable operations with map, flatMap, etc.
 * - Automatic exception to AppError conversion
 * - Support for chaining operations safely
 */
sealed class AppResult<out T> {
    
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
    
    /**
     * Returns true if this result represents a successful operation
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this result represents a failed operation
     */
    val isFailure: Boolean get() = this is Failure
    
    /**
     * Get the success data or null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }
    
    /**
     * Get the error or null
     */
    fun errorOrNull(): AppError? = when (this) {
        is Success -> null
        is Failure -> error
    }
    
    /**
     * Get the success data or throw the error
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw RuntimeException(error.message, error.cause)
    }
    
    /**
     * Get the success data or return a default value
     */
    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> defaultValue
    }
    
    /**
     * Transform the success data if present
     */
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> try {
            Success(transform(data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Failure(e.toAppError("map operation"))
        }
        is Failure -> this
    }
    
    /**
     * Transform the success data if present, handling potential failures
     */
    inline fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> try {
            transform(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Failure(e.toAppError("flatMap operation"))
        }
        is Failure -> this
    }
    
    /**
     * Transform the error if present
     */
    inline fun mapError(transform: (AppError) -> AppError): AppResult<T> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }
    
    /**
     * Recover from failure with a default value
     */
    inline fun recover(recovery: (AppError) -> @UnsafeVariance T): AppResult<T> = when (this) {
        is Success -> this
        is Failure -> try {
            Success(recovery(error))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Failure(e.toAppError("recovery operation"))
        }
    }
    
    /**
     * Recover from failure with another AppResult
     */
    inline fun recoverWith(recovery: (AppError) -> AppResult<@UnsafeVariance T>): AppResult<T> = when (this) {
        is Success -> this
        is Failure -> try {
            recovery(error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Failure(e.toAppError("recoverWith operation"))
        }
    }
    
    /**
     * Execute an action on success
     */
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) {
            try {
                action(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Don't convert side effect errors to AppResult failures
                println("Side effect error in onSuccess: ${e.message}")
            }
        }
        return this
    }
    
    /**
     * Execute an action on failure
     */
    inline fun onFailure(action: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) {
            try {
                action(error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Don't convert side effect errors to AppResult failures
                println("Side effect error in onFailure: ${e.message}")
            }
        }
        return this
    }
    
    /**
     * Fold the result into a single value
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (AppError) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }
    
    companion object {
        
        /**
         * Create a success result
         */
        fun <T> success(data: T): AppResult<T> = Success(data)
        
        /**
         * Create a failure result
         */
        fun <T> failure(error: AppError): AppResult<T> = Failure(error)
        
        /**
         * Create a failure result from an exception
         */
        fun <T> failure(exception: Throwable, context: String? = null): AppResult<T> = 
            Failure(exception.toAppError(context))
        
        /**
         * Execute a block and wrap the result safely
         */
        inline fun <T> catching(context: String? = null, block: () -> T): AppResult<T> = try {
            Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Failure(e.toAppError(context))
        }
        
        /**
         * Execute a suspend block and wrap the result safely
         */
        suspend inline fun <T> catchingSuspend(
            context: String? = null, 
            block: suspend () -> T
        ): AppResult<T> = try {
            Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Failure(e.toAppError(context))
        }
        
        /**
         * Combine two results into a pair
         */
        fun <T, U> combine(
            first: AppResult<T>,
            second: AppResult<U>
        ): AppResult<Pair<T, U>> = when {
            first is Success && second is Success -> Success(first.data to second.data)
            first is Failure -> first
            second is Failure -> second
            else -> error("Impossible state in combine")
        }
        
        /**
         * Combine three results into a triple
         */
        fun <T, U, V> combine(
            first: AppResult<T>,
            second: AppResult<U>,
            third: AppResult<V>
        ): AppResult<Triple<T, U, V>> = when {
            first is Success && second is Success && third is Success -> 
                Success(Triple(first.data, second.data, third.data))
            first is Failure -> first
            second is Failure -> second
            third is Failure -> third
            else -> error("Impossible state in combine")
        }
        
        /**
         * Sequence a list of results - succeeds only if all succeed
         */
        fun <T> sequence(results: List<AppResult<T>>): AppResult<List<T>> {
            val values = mutableListOf<T>()
            for (result in results) {
                when (result) {
                    is Success -> values.add(result.data)
                    is Failure -> return result
                }
            }
            return Success(values)
        }
    }
}

/**
 * Convert standard Kotlin Result to AppResult
 */
fun <T> Result<T>.toAppResult(context: String? = null): AppResult<T> = fold(
    onSuccess = { AppResult.success(it) },
    onFailure = { AppResult.failure(it, context) }
)

/**
 * Convert AppResult to standard Kotlin Result
 */
fun <T> AppResult<T>.toResult(): Result<T> = when (this) {
    is AppResult.Success -> Result.success(data)
    is AppResult.Failure -> Result.failure(RuntimeException(error.message, error.cause))
}

/**
 * Extension functions for nullable types
 */
fun <T> T?.toAppResult(errorIfNull: () -> AppError): AppResult<T> = 
    if (this != null) AppResult.success(this) else AppResult.failure(errorIfNull())

fun <T> T?.toAppResultNotFound(resource: String, id: String? = null): AppResult<T> = 
    if (this != null) AppResult.success(this) 
    else AppResult.failure(AppError.DataError.NotFound(resource, id))