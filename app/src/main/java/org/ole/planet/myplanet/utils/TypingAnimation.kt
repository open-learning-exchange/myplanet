package org.ole.planet.myplanet.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

object TypingAnimation {
    const val TICK_MS = 10L
    const val TARGET_UPDATES = 200
    const val MAX_CHUNK = 8

    fun chunkSize(length: Int): Int = (length / TARGET_UPDATES).coerceIn(1, MAX_CHUNK)

    suspend fun reveal(text: String, onUpdate: (String) -> Unit) {
        val step = chunkSize(text.length)
        var end = 0
        while (end < text.length) {
            currentCoroutineContext().ensureActive()
            end = minOf(end + step, text.length)
            onUpdate(text.substring(0, end))
            delay(TICK_MS * step)
        }
    }
}
