package org.ole.planet.myplanet.utilities

import android.os.*

class ANRWatchdog(private val timeout: Long = DEFAULT_ANR_TIMEOUT, private val listener: ANRListener? = null) {
    companion object {
        private const val DEFAULT_ANR_TIMEOUT = 5000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWatching = false
    private var tick = 0L

    private val ticker = Runnable {
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
        mainHandler.post(ticker)

        Thread({
            val threadName = Thread.currentThread().name
            Thread.currentThread().name = "ANRWatchdog"

            while (isWatching) {
                val lastTick = tick
                val currentTime = SystemClock.elapsedRealtime()
                mainHandler.post(ticker)

                try {
                    Thread.sleep(timeout / 2)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

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
                    try {
                        Thread.sleep(timeout)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }

            Thread.currentThread().name = threadName
        }, "ANRWatchdog").start()
    }

    fun stop() {
        isWatching = false
    }
}