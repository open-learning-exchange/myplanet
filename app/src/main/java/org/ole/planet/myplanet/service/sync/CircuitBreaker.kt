package org.ole.planet.myplanet.service.sync

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
