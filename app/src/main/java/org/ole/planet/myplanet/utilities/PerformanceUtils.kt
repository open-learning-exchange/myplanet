package org.ole.planet.myplanet.utilities

import android.os.Debug
import android.util.Log
import org.ole.planet.myplanet.BuildConfig

inline fun <T> trace(tag: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block()
    val startTime = Debug.threadCpuTimeNanos()
    val result = block()
    val elapsedMillis = (Debug.threadCpuTimeNanos() - startTime) / 1_000_000
    if (elapsedMillis > 16) {
        Log.w("Performance", "$tag took ${elapsedMillis}ms")
    }
    return result
}
