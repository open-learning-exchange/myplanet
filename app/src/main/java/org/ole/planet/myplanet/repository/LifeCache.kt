package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.MyLife

data class CachedMyLifeItem(
    var imageId: String?,
    var title: String?,
    var isVisible: Boolean,
    var weight: Int
)

@Singleton
class LifeCache @Inject constructor(
    @AppPreferences private val preferences: SharedPreferences,
    private val gson: Gson
) {
    private val memoryCache = ConcurrentHashMap<String, List<CachedMyLifeItem>>()

    fun read(cacheKey: String): List<CachedMyLifeItem>? {
        memoryCache[cacheKey]?.let { cached ->
            return cached.map { it.copy() }
        }

        val json = preferences.getString("$MY_LIFE_CACHE_PREFIX$cacheKey", null) ?: return null
        return try {
            val parsed: List<CachedMyLifeItem>? = gson.fromJson(json, cachedListType)
            if (parsed != null) {
                memoryCache[cacheKey] = parsed
                parsed.map { it.copy() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun write(cacheKey: String, items: List<MyLife>) {
        val cached = items.map { CachedMyLifeItem(it.imageId, it.title, it.isVisible, it.weight) }
        memoryCache[cacheKey] = cached
        preferences.edit { putString("$MY_LIFE_CACHE_PREFIX$cacheKey", gson.toJson(cached)) }
    }

    companion object {
        private const val MY_LIFE_CACHE_PREFIX = "myLifeCache_"
        private val cachedListType = object : TypeToken<List<CachedMyLifeItem>>() {}.type
    }
}
