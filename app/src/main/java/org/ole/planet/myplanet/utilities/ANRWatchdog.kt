package org.ole.planet.myplanet.utilities

import android.os.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ANRWatchdog(private val timeout: Long = DEFAULT_ANR_TIMEOUT, private val listener: ANRListener? = null) {
    companion object {
        private const val DEFAULT_ANR_TIMEOUT = 5000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWatching = false
    private var tick = 0L
    private var watchJob: Job? = null

    private val ticker = Runnable {
        tick = SystemClock.elapsedRealtime()
    }

    interface ANRListener {
        fun onAppNotResponding(message: String, blockedThread: Thread, duration: Long)
    }

    fun start(scope: CoroutineScope) {
        if (isWatching) {
            return
        }

        isWatching = true
        tick = SystemClock.elapsedRealtime()
        mainHandler.post(ticker)

        watchJob = scope.launch(Dispatchers.Default) {
            val threadName = Thread.currentThread().name
            Thread.currentThread().name = "ANRWatchdog"

            try {
                while (isWatching) {
                    val lastTick = tick
                    val currentTime = SystemClock.elapsedRealtime()
                    mainHandler.post(ticker)

                    delay(timeout / 2)

                    if (isWatching && lastTick == tick) {
                        val duration = currentTime - lastTick
                        val mainThread = Looper.getMainLooper().thread

                        val message = StringBuilder("ANR detected on thread ")
                            .append(mainThread.name)
                            .append(" (")
                            .append(mainThread.id)
                            .append(")\n")

                        for (element in mainThread.stackTrace) {
                            message.append("\tat ").append(element.toString()).append('\n')
                        }

                        listener?.onAppNotResponding(message.toString(), mainThread, duration)
                        delay(timeout)
                    }
                }
            } finally {
                Thread.currentThread().name = threadName
            }
        }
    }

    fun stop() {
        isWatching = false
        watchJob?.cancel()
    }
}
