package org.ole.planet.myplanet.utils

import android.os.SystemClock

/** Android [TimeProvider] backed by [System.currentTimeMillis] and [SystemClock.elapsedRealtime]. */
class SystemTimeProvider : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun sleep(millis: Long) {
        Thread.sleep(millis)
    }
}
