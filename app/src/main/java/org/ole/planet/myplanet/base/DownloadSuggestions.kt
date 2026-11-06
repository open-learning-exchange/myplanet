package org.ole.planet.myplanet.base

import androidx.annotation.VisibleForTesting

internal object DownloadSuggestions {
    private val offered = mutableMapOf<String, MutableSet<String>>()
    
    @Synchronized
    fun shouldOffer(userId: String, resourceIds: Collection<String>): Boolean {
        val seen = offered.getOrPut(userId) { mutableSetOf() }
        val hasNew = resourceIds.any { it !in seen }
        seen.addAll(resourceIds)
        return hasNew
    }

    @VisibleForTesting
    @Synchronized
    fun reset() {
        offered.clear()
    }
}
