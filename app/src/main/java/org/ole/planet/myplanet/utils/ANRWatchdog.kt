package org.ole.planet.myplanet.utils

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ANRWatchdog(private val timeout: Long = DEFAULT_ANR_TIMEOUT, private val listener: ANRListener? = null) {
    private var job: Job? = null
    companion object {
        private const val DEFAULT_ANR_TIMEOUT = 5000L
    }

    private var isWatching = false
    private var tick = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickUpdater = Runnable { updateTick() }


    private fun updateTick() {
        tick = SystemClock.elapsedRealtime()
    }

    interface ANRListener {
        fun onAppNotResponding(message: String, blockedThread: Thread, duration: Long)
    }

    fun start() {
        if (isWatching) {
            return
        }

        isWatching = true
        tick = SystemClock.elapsedRealtime()
        mainHandler.post(tickUpdater)

        job = CoroutineScope(Dispatchers.Default).launch {
            while (isWatching && isActive) {
                val lastTick = tick
                val currentTime = SystemClock.elapsedRealtime()
                mainHandler.post(tickUpdater)

                delay(timeout / 2)

                if (isWatching && lastTick == tick) {
                    val duration = currentTime - lastTick
                    val mainThread = Looper.getMainLooper().thread

                    val threadId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mainThread.threadId()
                    } else {
                        @Suppress("DEPRECATION")
                        mainThread.id
                    }

                    val message = StringBuilder("ANR detected on thread ")
                        .append(mainThread.name)
                        .append(" (")
                        .append(threadId)
                        .append(")\n")

                    for (element in mainThread.stackTrace) {
                        message.append("\tat ").append(element.toString()).append('\n')
                    }

                    listener?.onAppNotResponding(message.toString(), mainThread, duration)
                    delay(timeout)
                }
            }
        }
    }

    fun stop() {
        isWatching = false
        mainHandler.removeCallbacks(tickUpdater)
        job?.cancel()
        job = null
    }
}
