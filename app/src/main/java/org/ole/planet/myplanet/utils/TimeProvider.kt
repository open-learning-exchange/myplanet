package org.ole.planet.myplanet.utils

/**
 * Injectable source of wall-clock and monotonic time so time-of-day / expiry / scheduling
 * decisions and duration measurements are testable. Mirrors [DispatcherProvider].
 *
 * Use [now] for timestamps and time-based decisions persisted across restarts. For measuring
 * elapsed durations within a single process, use [elapsedRealtime] — it is monotonic and immune
 * to wall-clock jumps. The Android implementation is [SystemTimeProvider].
 */
interface TimeProvider {
    fun now(): Long
    fun elapsedRealtime(): Long
    fun sleep(millis: Long)
}
