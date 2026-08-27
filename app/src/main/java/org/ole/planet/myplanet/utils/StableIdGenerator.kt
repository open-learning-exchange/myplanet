package org.ole.planet.myplanet.utils

import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

object StableIdGenerator {
    private val fallbackIds = WeakHashMap<Any, Long>()
    private var nextFallbackId = -2L

    /**
     * Generates a collision-resistant stable Long ID from a String key using the FNV-1a 64-bit hash algorithm.
     * Note: This implementation hashes UTF-16 code units (Char.code), not standard UTF-8 bytes.
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

    /**
     * Generates a fallback Long ID for an item that lacks a usable string key, so that keyless items
     * never share RecyclerView.NO_ID under setHasStableIds(true).
     *
     * Keys are held in a WeakHashMap, so the ID is stable for as long as an equal key is reachable:
     * items with a value-based equals (data classes) keep their ID across emissions, while items that
     * inherit identity equality (the open Room entities) get a fresh ID on each new instance. Callers
     * that need the stronger guarantee should pass a content-derived key to generateStringId instead.
     */
    @Synchronized
    fun generateFallbackId(item: Any): Long {
        return fallbackIds.getOrPut(item) {
            nextFallbackId--
        }
    }
}
