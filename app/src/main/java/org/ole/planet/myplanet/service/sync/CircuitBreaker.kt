package org.ole.planet.myplanet.service.sync

import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CircuitState {
    CLOSED, OPEN, HALF_OPEN
}

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val recoveryTimeoutMs: Long = 60000, // 1 minute
    val successThreshold: Int = 3, // For half-open to closed transition
    val maxRetryAttempts: Int = 3,
    val baseDelayMs: Long = 1000
)

class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private var state = CircuitState.CLOSED
    private var failureCount = 0
    private var successCount = 0
    private var lastFailureTime = 0L
    private val mutex = Mutex()

    suspend fun <T> execute(operation: suspend () -> T): Result<T> = mutex.withLock {
        when (state) {
            CircuitState.OPEN -> {
                if (shouldAttemptReset()) {
                    state = CircuitState.HALF_OPEN
                    successCount = 0
                } else {
                    return Result.failure(CircuitBreakerOpenException("Circuit breaker $name is OPEN"))
                }
            }
            CircuitState.HALF_OPEN -> {
                // Allow limited requests through
            }
            CircuitState.CLOSED -> {
                // Normal operation
            }
        }

        try {
            val result = operation()
            onSuccess()
            Result.success(result)
        } catch (e: Exception) {
            onFailure(e)
            Result.failure(e)
        }
    }

    private fun shouldAttemptReset(): Boolean {
        return System.currentTimeMillis() - lastFailureTime >= config.recoveryTimeoutMs
    }

    private fun onSuccess() {
        when (state) {
            CircuitState.HALF_OPEN -> {
                successCount++
                if (successCount >= config.successThreshold) {
                    state = CircuitState.CLOSED
                    failureCount = 0
                }
            }
            CircuitState.CLOSED -> {
                failureCount = 0
            }
            CircuitState.OPEN -> {
                // Should not happen
            }
        }
    }

    private fun onFailure(exception: Exception) {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        when (state) {
            CircuitState.CLOSED -> {
                if (failureCount >= config.failureThreshold) {
                    state = CircuitState.OPEN
                }
            }
            CircuitState.HALF_OPEN -> {
                state = CircuitState.OPEN
            }
            CircuitState.OPEN -> {
                // Already open, just update counters
            }
        }
    }

    fun getState(): CircuitState = state
    fun getFailureCount(): Int = failureCount
}

class CircuitBreakerOpenException(message: String) : Exception(message)

class RetryHandler(private val config: CircuitBreakerConfig = CircuitBreakerConfig()) {
    
    suspend fun <T> executeWithRetry(
        circuitBreaker: CircuitBreaker,
        operation: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        
        for (attempt in 1..config.maxRetryAttempts) {
            val result = circuitBreaker.execute(operation)
            
            if (result.isSuccess) {
                return result
            }
            
            lastException = result.exceptionOrNull() as? Exception
            
            if (lastException is CircuitBreakerOpenException) {
                break // Don't retry if circuit breaker is open
            }
            
            if (attempt < config.maxRetryAttempts) {
                val delayMs = calculateBackoffDelay(attempt)
                delay(delayMs)
            }
        }
        
        return Result.failure(lastException ?: Exception("All retry attempts failed"))
    }
    
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = config.baseDelayMs * 2.0.pow(attempt - 1).toLong()
        val jitter = (Math.random() * config.baseDelayMs * 0.1).toLong()
        return min(exponentialDelay + jitter, 30000) // Max 30 seconds
    }
}

class SyncErrorRecovery {
    
    private val circuitBreakers = mutableMapOf<String, CircuitBreaker>()
    private val retryHandler = RetryHandler()
    
    fun getCircuitBreaker(tableName: String): CircuitBreaker {
        return circuitBreakers.getOrPut(tableName) {
            CircuitBreaker("sync_$tableName")
        }
    }
    
    suspend fun <T> executeSyncOperation(
        tableName: String,
        operation: suspend () -> T
    ): Result<T> {
        val circuitBreaker = getCircuitBreaker(tableName)
        return retryHandler.executeWithRetry(circuitBreaker, operation)
    }
    
    fun getCircuitBreakerStatus(): Map<String, CircuitState> {
        return circuitBreakers.mapValues { it.value.getState() }
    }
    
    fun resetCircuitBreaker(tableName: String) {
        circuitBreakers.remove(tableName)
    }
    
    fun resetAllCircuitBreakers() {
        circuitBreakers.clear()
    }
}
