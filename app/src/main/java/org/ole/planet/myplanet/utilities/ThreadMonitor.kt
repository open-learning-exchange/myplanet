package org.ole.planet.myplanet.utilities

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

object ThreadMonitor {
    private const val TAG = "ThreadMonitor"
    private val checkpointCounts = ConcurrentHashMap<String, Int>()
    private val checkpointOrder = mutableListOf<String>()
    private val activeOperations = AtomicInteger(0)
    private var isMonitoring = false

    fun startMonitoring() {
        isMonitoring = true
        logThreadState("MONITORING_STARTED")
    }

    fun stopMonitoring() {
        isMonitoring = false
        logThreadState("MONITORING_STOPPED")
        checkpointCounts.clear()
        checkpointOrder.clear()
    }

    fun logThreadState(checkpoint: String) {
        if (!isMonitoring) return

        val threadCount = Thread.activeCount()
        val threadGroup = Thread.currentThread().threadGroup
        val threads = Array<Thread?>(threadCount + 10) { null }
        val actualCount = threadGroup?.enumerate(threads) ?: 0

        // Count thread states
        var runnable = 0
        var blocked = 0
        var waiting = 0
        var timedWaiting = 0
        var newState = 0
        var terminated = 0

        val threadNames = mutableListOf<String>()

        threads.filterNotNull().take(actualCount).forEach { thread ->
            when (thread.state) {
                Thread.State.RUNNABLE -> runnable++
                Thread.State.BLOCKED -> blocked++
                Thread.State.WAITING -> waiting++
                Thread.State.TIMED_WAITING -> timedWaiting++
                Thread.State.NEW -> newState++
                Thread.State.TERMINATED -> terminated++
            }

            // Log thread names that look suspicious (coroutine related)
            if (thread.name.contains("kotlinx.coroutines") ||
                thread.name.contains("DefaultDispatcher") ||
                thread.name.contains("worker") ||
                thread.name.startsWith("pool-")) {
                threadNames.add("${thread.name}(${thread.state})")
            }
        }

        // Memory info
        val runtime = Runtime.getRuntime()
        val memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024

        // Store count for this checkpoint
        checkpointCounts[checkpoint] = actualCount
        checkpointOrder.add(checkpoint)

        Log.w(TAG, """
========== THREAD STATE: $checkpoint ==========
Total Threads: $actualCount
  - RUNNABLE: $runnable
  - BLOCKED: $blocked  
  - WAITING: $waiting
  - TIMED_WAITING: $timedWaiting
  - NEW: $newState
  - TERMINATED: $terminated
Active Operations: ${activeOperations.get()}
Memory: ${memoryUsed}MB / ${maxMemory}MB
Suspicious Threads: ${threadNames.take(10)}
===============================================
        """.trimIndent())

        // Log thread count changes
        if (checkpointOrder.size >= 2) {
            val currentCheckpoint = checkpointOrder.last()
            val previousCheckpoint = checkpointOrder[checkpointOrder.size - 2]

            val currentCount = checkpointCounts[currentCheckpoint] ?: 0
            val previousCount = checkpointCounts[previousCheckpoint] ?: 0
            val diff = currentCount - previousCount

            if (abs(diff) > 5) {
                Log.e(TAG, "⚠️  THREAD COUNT CHANGED: $previousCheckpoint -> $currentCheckpoint (${if(diff > 0) "+" else ""}$diff threads)")
            }
        }

        // Alert if thread count is too high
        if (actualCount > 60) {
            Log.e(TAG, "🚨 CRITICAL: Thread count ($actualCount) is dangerously high!")
        } else if (actualCount > 40) {
            Log.w(TAG, "⚠️  WARNING: Thread count ($actualCount) is high!")
        }
    }

    fun enterOperation(operationName: String) {
        val count = activeOperations.incrementAndGet()
        if (isMonitoring) {
            Log.d(TAG, "🔵 ENTER: $operationName (Active: $count)")
            if (count % 5 == 0) { // Log thread state every 5 operations
                logThreadState("OPERATION_$operationName")
            }
        }
    }

    fun exitOperation(operationName: String) {
        val count = activeOperations.decrementAndGet()
        if (isMonitoring) {
            Log.d(TAG, "🔴 EXIT: $operationName (Active: $count)")
        }
    }

    fun logCoroutineScope(scopeName: String, scope: CoroutineScope) {
        if (!isMonitoring) return

        val job = scope.coroutineContext[Job]
        val activeChildren = job?.children?.count() ?: 0

        Log.d(TAG, "🔄 COROUTINE SCOPE: $scopeName - Active children: $activeChildren")

        if (activeChildren > 10) {
            Log.w(TAG, "⚠️  High coroutine count in $scopeName: $activeChildren")
        }
    }

    fun logSemaphoreOperation(semaphoreName: String, permits: Int, waiting: Int = -1) {
        if (!isMonitoring) return

        val waitingText = if (waiting >= 0) ", Waiting: $waiting" else ""
        Log.d(TAG, "🔒 SEMAPHORE: $semaphoreName - Available permits: $permits$waitingText")
    }
}