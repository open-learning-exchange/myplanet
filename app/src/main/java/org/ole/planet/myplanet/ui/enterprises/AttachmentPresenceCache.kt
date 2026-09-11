package org.ole.planet.myplanet.ui.enterprises

import java.io.File

class AttachmentPresenceCache(
    private val ttlMs: Long = DEFAULT_TTL_MS
) {
    private val cache = HashMap<String, Pair<Boolean, Long>>()

    fun clear() {
        cache.clear()
    }

    fun exists(file: File?, now: Long): Boolean {
        if (file == null) return false
        val path = file.absolutePath
        val cached = cache[path]
        return if (cached != null && now - cached.second < ttlMs) {
            cached.first
        } else {
            val freshExists = file.exists()
            cache[path] = Pair(freshExists, now)
            freshExists
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 5000L
    }
}
