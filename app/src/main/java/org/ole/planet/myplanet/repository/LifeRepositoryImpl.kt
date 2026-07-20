package org.ole.planet.myplanet.repository

import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.RealmDispatcher
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
    @RealmDispatcher private val realmDispatcher: CoroutineDispatcher,
    private val sharedPrefManager: SharedPrefManager,
    private val gson: Gson,
    @ApplicationScope private val appScope: CoroutineScope
) : LifeRepository {

    private val MY_LIFE_CACHE_PREFIX = "myLifeCache_"

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        myLifeDao.updateVisibility(myLifeId, isVisible)
    }

    override suspend fun updateMyLifeListOrder(list: List<MyLife>) {
        val userId = list.firstOrNull()?.userId
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
            if (userId != null) {
                val updatedLives = getMyLifeByUserId(userId, ensureLatest = true)
                cacheMyLifeItems(userId, updatedLives)
            }
        }
    }

    override suspend fun getMyLifeByUserId(userId: String?, ensureLatest: Boolean): List<MyLife> {
        return myLifeDao.getByUserId(userId)
    }

    override suspend fun getVisibleMyLifeByUserId(userId: String?, ensureLatest: Boolean): List<MyLife> {
        return myLifeDao.getVisibleByUserId(userId)
    }

    override suspend fun getMyLifeForDashboard(userId: String, seedBase: List<MyLife>): List<MyLife> {
        val json = sharedPrefManager.rawPreferences.getString("$MY_LIFE_CACHE_PREFIX$userId", null)
        if (json != null) {
            val cached: List<CachedMyLifeItem>? = try {
                val type = object : TypeToken<List<CachedMyLifeItem>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                null
            }
            if (cached != null) {
                appScope.launch(realmDispatcher) {
                    val storedItems = getMyLifeByUserId(userId, ensureLatest = false)
                    if (storedItems.isNotEmpty()) {
                        cacheMyLifeItems(userId, storedItems)
                    }
                }
                return cached.filter { it.isVisible }.map { item ->
                    MyLife(item.imageId, userId, item.title).apply {
                        isVisible = item.isVisible
                        weight = item.weight
                    }
                }
            }
        }

        val allForUser = getMyLifeByUserId(userId, ensureLatest = false)
        val visibleItems = if (allForUser.isEmpty()) {
            seedMyLifeIfEmpty(userId, seedBase)
            val seeded = getMyLifeByUserId(userId, ensureLatest = true)
            cacheMyLifeItems(userId, seeded)
            seeded.filter { it.isVisible }
        } else {
            cacheMyLifeItems(userId, allForUser)
            allForUser.filter { it.isVisible }
        }
        return visibleItems
    }

    private fun cacheMyLifeItems(userId: String, items: List<MyLife>) {
        val cached = items.map { CachedMyLifeItem(it.imageId, it.title, it.isVisible, it.weight) }
        sharedPrefManager.rawPreferences.edit { putString("$MY_LIFE_CACHE_PREFIX$userId", gson.toJson(cached)) }
    }

    override suspend fun seedMyLifeIfEmpty(userId: String?, items: List<MyLife>) {
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
