package org.ole.planet.myplanet.utils

import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

object StableIdGenerator {
    private val fallbackIds = WeakHashMap<Any, Long>()
    private var nextFallbackId = -2L

    fun generateStringId(key: String?): Long {
        if (key.isNullOrEmpty()) return RecyclerView.NO_ID
        var hash = -3750763034362895579L // FNV offset basis
        for (i in key.indices) {
            hash = hash xor key[i].code.toLong()
            hash *= 1099511628211L // FNV prime
        }
        return hash
    }

    @Synchronized
    fun generateFallbackId(item: Any): Long {
        return fallbackIds.getOrPut(item) {
            nextFallbackId--
        }
    }
}
