package org.ole.planet.myplanet.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.utils.KeyValueStore
import org.ole.planet.myplanet.utils.concurrentMutableMap

@Serializable
data class CachedMyLifeItem(
    var imageId: String? = null,
    var title: String? = null,
    var isVisible: Boolean,
    var weight: Int
)

class LifeCache(
    private val preferences: KeyValueStore,
    private val json: Json
) {
    private val memoryCache = concurrentMutableMap<String, List<CachedMyLifeItem>>()

    fun read(cacheKey: String): List<CachedMyLifeItem>? {
        memoryCache[cacheKey]?.let { cached ->
            return cached.map { it.copy() }
        }

        val jsonString = preferences.getString("$MY_LIFE_CACHE_PREFIX$cacheKey", null) ?: return null
        return try {
            val parsed: List<CachedMyLifeItem> = json.decodeFromString(jsonString)
            memoryCache[cacheKey] = parsed
            parsed.map { it.copy() }
        } catch (e: Exception) {
            null
        }
    }

    fun write(cacheKey: String, items: List<MyLife>) {
        val cached = items.map { CachedMyLifeItem(it.imageId, it.title, it.isVisible, it.weight) }
        memoryCache[cacheKey] = cached
        preferences.putString("$MY_LIFE_CACHE_PREFIX$cacheKey", json.encodeToString(cached))
    }

    companion object {
        private const val MY_LIFE_CACHE_PREFIX = "myLifeCache_"
    }
}
