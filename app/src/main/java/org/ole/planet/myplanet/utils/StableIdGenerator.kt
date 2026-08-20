package org.ole.planet.myplanet.utils

import androidx.recyclerview.widget.RecyclerView

object StableIdGenerator {
    /**
     * Generates a collision-resistant stable Long ID from a String key using the FNV-1a 64-bit hash algorithm.
     * This is useful for RecyclerView Adapters when setHasStableIds(true) is used.
     * If the string is null or empty, it returns RecyclerView.NO_ID.
     */
    fun generateStringId(key: String?): Long {
        if (key.isNullOrEmpty()) return RecyclerView.NO_ID
        var hash = -3750763034362895579L // FNV offset basis
        for (i in key.indices) {
            hash = hash xor key[i].code.toLong()
            hash *= 1099511628211L // FNV prime
        }
        return hash
    }
}
