package org.ole.planet.myplanet.utilities

import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication

class ANRWatchdog(private val timeout: Long = DEFAULT_ANR_TIMEOUT, private val listener: ANRListener? = null) {
    companion object {
        private const val DEFAULT_ANR_TIMEOUT = 5000L
    }

    private var isWatching = false
    private var tick = 0L

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
        MainApplication.applicationScope.launch(Dispatchers.Main) { updateTick() }

        Thread({
            val threadName = Thread.currentThread().name
            Thread.currentThread().name = "ANRWatchdog"

            while (isWatching) {
                val lastTick = tick
                val currentTime = SystemClock.elapsedRealtime()
                MainApplication.applicationScope.launch(Dispatchers.Main) { updateTick() }

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
