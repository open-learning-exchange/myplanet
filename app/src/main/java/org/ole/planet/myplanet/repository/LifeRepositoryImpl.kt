package org.ole.planet.myplanet.repository

import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import javax.inject.Inject

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ole.planet.myplanet.data.room.dao.MyLifeDao

import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.services.SharedPrefManager

data class CachedMyLifeItem(
    var imageId: String?,
    var title: String?,
    var isVisible: Boolean,
    var weight: Int
)
class LifeRepositoryImpl @Inject constructor(
    private val myLifeDao: MyLifeDao,
    private val sharedPrefManager: SharedPrefManager,
    private val gson: Gson
) : LifeRepository {

    private val MY_LIFE_CACHE_PREFIX = "myLifeCache_"
    private val seedMutex = Mutex()

    private fun normalizeUserId(userId: String?): String? {
        return userId?.takeIf { it.isNotBlank() && it != "--" }
    }

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        myLifeDao.updateVisibility(myLifeId, isVisible)
        val managedLives = myLifeDao.getByIds(listOf(myLifeId))
        val rawUserId = managedLives.firstOrNull()?.userId ?: sharedPrefManager.getUserId()
        val effectiveUserId = normalizeUserId(rawUserId)
        val updatedLives = getMyLifeByUserId(effectiveUserId, ensureLatest = true)
        cacheMyLifeItems(effectiveUserId ?: "--", updatedLives)
    }

    override suspend fun updateMyLifeListOrder(list: List<MyLife>) {
        if (list.isEmpty()) return
        val rawUserId = list.firstOrNull()?.userId ?: sharedPrefManager.getUserId()
        val effectiveUserId = normalizeUserId(rawUserId)
        val idToIndex = list.mapIndexed { index, item -> item._id to index }.toMap()
        val ids = idToIndex.keys.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return

        val managedLives = myLifeDao.getByIds(ids)
        val changed = managedLives.mapNotNull { managedLife ->
            val index = idToIndex[managedLife._id]
            if (index != null && managedLife.weight != index) {
                managedLife.weight = index
                managedLife
            } else {
                null
            }
        }

        if (changed.isNotEmpty()) {
            myLifeDao.update(changed)
        }
        val updatedLives = getMyLifeByUserId(effectiveUserId, ensureLatest = true)
        cacheMyLifeItems(effectiveUserId ?: "--", updatedLives)
    }

    private fun MyLife.dedupKey(): Any {
        return imageId?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: _id.takeIf { it.isNotBlank() }
            ?: System.identityHashCode(this)
    }

    override suspend fun getMyLifeByUserId(userId: String?, ensureLatest: Boolean): List<MyLife> {
        val effectiveUserId = normalizeUserId(userId)
        return myLifeDao.getByUserId(effectiveUserId).distinctBy { it.dedupKey() }.sortedBy { it.weight }
    }

    override suspend fun getMyLifeForDashboard(userId: String, seedBase: List<MyLife>): List<MyLife> {
        val effectiveUserId = normalizeUserId(userId)
        val allForUser = getMyLifeByUserId(effectiveUserId, ensureLatest = false)
        if (allForUser.isNotEmpty()) {
            return allForUser.filter { it.isVisible }.sortedBy { it.weight }
        }

        val cacheKey = effectiveUserId ?: "--"
        val json = sharedPrefManager.rawPreferences.getString("$MY_LIFE_CACHE_PREFIX$cacheKey", null)
        if (json != null) {
            val cached: List<CachedMyLifeItem>? = try {
                val type = object : TypeToken<List<CachedMyLifeItem>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                null
            }
            if (cached != null) {
                return cached.filter { it.isVisible }.map { item ->
                    MyLife(item.imageId, effectiveUserId, item.title).apply {
                        isVisible = item.isVisible
                        weight = item.weight
                    }
                }.sortedBy { it.weight }
            }
        }

        seedMyLifeIfEmpty(effectiveUserId, seedBase)
        val seeded = getMyLifeByUserId(effectiveUserId, ensureLatest = true)
        cacheMyLifeItems(cacheKey, seeded)
        return seeded.filter { it.isVisible }.sortedBy { it.weight }
    }

    private fun cacheMyLifeItems(userId: String, items: List<MyLife>) {
        val cached = items.map { CachedMyLifeItem(it.imageId, it.title, it.isVisible, it.weight) }
        sharedPrefManager.rawPreferences.edit { putString("$MY_LIFE_CACHE_PREFIX$userId", gson.toJson(cached)) }
    }

    override suspend fun seedMyLifeIfEmpty(userId: String?, items: List<MyLife>) {
        val effectiveUserId = normalizeUserId(userId)
        seedMutex.withLock {
            val existing = myLifeDao.countByUserId(effectiveUserId)
            if (existing == 0) {
                var weight = 1
                val newItems = items.map { item ->
                    MyLife().apply {
                        _id = UUID.randomUUID().toString()
                        title = item.title
                        imageId = item.imageId
                        this.weight = weight++
                        this.userId = effectiveUserId
                        isVisible = true
                    }
                }
                myLifeDao.insertAll(newItems)
            }
        }
    }
}
