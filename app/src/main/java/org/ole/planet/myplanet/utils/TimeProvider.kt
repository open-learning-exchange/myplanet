package org.ole.planet.myplanet.utils

/**
 * Injectable source of wall-clock time so time-of-day / expiry / scheduling decisions are
 * testable and don't read [System.currentTimeMillis] directly. Mirrors [DispatcherProvider].
 *
 * Use [now] for timestamps and time-based decisions persisted across restarts. For measuring
 * elapsed durations within a single process, use android.os.SystemClock.elapsedRealtime()
 * directly instead — it is monotonic and immune to wall-clock jumps.
 */
interface TimeProvider {
    fun now(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}
