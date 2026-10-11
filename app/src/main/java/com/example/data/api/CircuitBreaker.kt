package com.example.data.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val recoveryTimeoutMillis: Long = 60_000L
) {
    private var failureCount = 0
    private var lastFailureTime = 0L
    private val mutex = Mutex()

    enum class State {
        CLOSED, // Healthy, allow requests
        OPEN,   // Broken, fast fail
        HALF_OPEN // Testing recovery with one request
    }

    suspend fun getState(): State = mutex.withLock {
        if (failureCount >= failureThreshold) {
            val now = System.currentTimeMillis()
            if (now - lastFailureTime >= recoveryTimeoutMillis) {
                State.HALF_OPEN
            } else {
                State.OPEN
            }
        } else {
            State.CLOSED
        }
    }

    suspend fun recordSuccess() = mutex.withLock {
        failureCount = 0
        lastFailureTime = 0L
    }

    suspend fun recordFailure() = mutex.withLock {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
    }
    
    suspend fun isOpen(): Boolean {
        return getState() == State.OPEN
    }
}
