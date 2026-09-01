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

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        myLifeDao.updateVisibility(myLifeId, isVisible)
        val managedLives = myLifeDao.getByIds(listOf(myLifeId))
        val userId = managedLives.firstOrNull()?.userId ?: sharedPrefManager.getUserId()
        if (userId.isNotEmpty()) {
            val updatedLives = getMyLifeByUserId(userId)
            cacheMyLifeItems(userId, updatedLives)
        }
    }

    override suspend fun updateMyLifeListOrder(list: List<MyLife>) {
        val userId = list.firstOrNull()?.userId
        val idToIndex = buildMap(list.size) {
            list.forEachIndexed { index, item ->
                put(item._id, index)
            }
        }
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
            if (userId != null) {
                val updatedLives = getMyLifeByUserId(userId)
                cacheMyLifeItems(userId, updatedLives)
            }
        }
    }

    private fun MyLife.dedupKey(): Any {
        return imageId?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: _id.takeIf { it.isNotBlank() }
            ?: listOf(userId, isVisible, weight)
    }

    override suspend fun getMyLifeByUserId(userId: String?, defaultItems: List<MyLife>): List<MyLife> {
        val effectiveUserId = userId?.ifEmpty { null }
        val items = myLifeDao.getByUserId(effectiveUserId).distinctBy { it.dedupKey() }
        if (items.isNotEmpty() || defaultItems.isEmpty()) {
            return items
        }
        seedMyLifeIfEmpty(effectiveUserId, defaultItems)
        return myLifeDao.getByUserId(effectiveUserId).distinctBy { it.dedupKey() }
    }

    private suspend fun getVisibleMyLifeByUserId(userId: String?): List<MyLife> {
        val effectiveUserId = userId?.ifEmpty { null }
        return myLifeDao.getVisibleByUserId(effectiveUserId).distinctBy { it.dedupKey() }
    }

    override suspend fun getMyLifeForDashboard(userId: String, seedBase: List<MyLife>): List<MyLife> {
        val effectiveUserId = userId.ifEmpty { null }
        val visibleForUser = getVisibleMyLifeByUserId(effectiveUserId)
        if (visibleForUser.isNotEmpty()) {
            return visibleForUser
        }
        if (myLifeDao.countByUserId(effectiveUserId) > 0) {
            return emptyList()
        }

        val json = sharedPrefManager.rawPreferences.getString("$MY_LIFE_CACHE_PREFIX$userId", null)
        if (json != null) {
            val cached: List<CachedMyLifeItem>? = try {
                val type = object : TypeToken<List<CachedMyLifeItem>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                null
            }
            if (cached != null) {
                return cached.mapNotNull { item ->
                    if (item.isVisible) {
                        MyLife(item.imageId, userId, item.title).apply {
                            isVisible = item.isVisible
                            weight = item.weight
                        }
                    } else {
                        null
                    }
                }
            }
        }

        seedMyLifeIfEmpty(effectiveUserId, seedBase)
        val seeded = getMyLifeByUserId(effectiveUserId)
        if (userId.isNotEmpty()) cacheMyLifeItems(userId, seeded)
        return seeded.filter { it.isVisible }
    }

    private fun cacheMyLifeItems(userId: String, items: List<MyLife>) {
        val cached = items.map { CachedMyLifeItem(it.imageId, it.title, it.isVisible, it.weight) }
        sharedPrefManager.rawPreferences.edit { putString("$MY_LIFE_CACHE_PREFIX$userId", gson.toJson(cached)) }
    }

    override suspend fun seedMyLifeIfEmpty(userId: String?, items: List<MyLife>) {
        seedMutex.withLock {
            val existing = myLifeDao.countByUserId(userId)
            if (existing == 0) {
                var weight = 1
                val newItems = items.map { item ->
                    MyLife().apply {
                        _id = UUID.randomUUID().toString()
                        title = item.title
                        imageId = item.imageId
                        this.weight = weight++
                        this.userId = item.userId
                        isVisible = true
                    }
                }
                myLifeDao.insertAll(newItems)
            }
        }
    }
}
