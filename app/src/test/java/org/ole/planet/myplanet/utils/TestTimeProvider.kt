package org.ole.planet.myplanet.utils

/** Deterministic [TimeProvider] for tests. Advance time via [currentTime] or [advanceBy]. */
class TestTimeProvider(var currentTime: Long = 0L) : TimeProvider {
    override fun now(): Long = currentTime

    override fun sleep(millis: Long) {
        advanceBy(millis)
    }

    fun advanceBy(millis: Long) {
        currentTime += millis
    }
}
