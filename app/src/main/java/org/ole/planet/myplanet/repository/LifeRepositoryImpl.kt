package org.ole.planet.myplanet.repository

import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.datasource.MyLifeCacheDataSource
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.services.SharedPrefManager

class LifeRepositoryImpl @Inject constructor(
    private val myLifeDao: MyLifeDao,
    private val sharedPrefManager: SharedPrefManager,
    private val myLifeCacheDataSource: MyLifeCacheDataSource
) : LifeRepository {

    private val seedMutex = Mutex()

    private fun normalizeUserId(userId: String?): String? {
        return userId?.takeIf { it.isNotBlank() && it != "--" }
    }

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String): List<MyLife> {
        myLifeDao.updateVisibility(myLifeId, isVisible)
        val managedLives = myLifeDao.getByIds(listOf(myLifeId))
        val rawUserId = managedLives.firstOrNull()?.userId ?: sharedPrefManager.getUserId()
        val effectiveUserId = normalizeUserId(rawUserId)
        val updatedLives = getMyLifeByUserId(effectiveUserId)
        myLifeCacheDataSource.write(effectiveUserId ?: "--", updatedLives)
        return updatedLives
    }

    override suspend fun updateMyLifeListOrder(list: List<MyLife>) {
        if (list.isEmpty()) return
        val rawUserId = list.firstOrNull()?.userId ?: sharedPrefManager.getUserId()
        val effectiveUserId = normalizeUserId(rawUserId)
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
        }
        val updatedLives = getMyLifeByUserId(effectiveUserId)
        myLifeCacheDataSource.write(effectiveUserId ?: "--", updatedLives)
    }

    private fun MyLife.dedupKey(): Any {
        return imageId?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: _id.takeIf { it.isNotBlank() }
            ?: listOf(userId, isVisible, weight)
    }

    override suspend fun getMyLifeByUserId(userId: String?, defaultItems: List<MyLife>): List<MyLife> {
        val effectiveUserId = normalizeUserId(userId)
        val items = myLifeDao.getByUserId(effectiveUserId).distinctBy { it.dedupKey() }.sortedBy { it.weight }
        if (items.isNotEmpty() || defaultItems.isEmpty()) {
            return items
        }
        val seeded = seedMyLifeIfEmpty(effectiveUserId, defaultItems)
        if (seeded.isNotEmpty()) {
            return seeded
        }
        return myLifeDao.getByUserId(effectiveUserId).distinctBy { it.dedupKey() }.sortedBy { it.weight }
    }

    private suspend fun getVisibleMyLifeByUserId(userId: String?): List<MyLife> {
        val effectiveUserId = normalizeUserId(userId)
        return myLifeDao.getVisibleByUserId(effectiveUserId).distinctBy { it.dedupKey() }.sortedBy { it.weight }
    }

    override suspend fun getMyLifeForDashboard(userId: String, seedBase: List<MyLife>): List<MyLife> {
        val effectiveUserId = normalizeUserId(userId)
        val visibleForUser = getVisibleMyLifeByUserId(effectiveUserId)
        if (visibleForUser.isNotEmpty()) {
            return visibleForUser
        }
        if (myLifeDao.countByUserId(effectiveUserId) > 0) {
            return emptyList()
        }

        val cacheKey = effectiveUserId ?: "--"
        val cached = myLifeCacheDataSource.read(cacheKey)
        if (cached != null) {
            return cached.mapNotNull { item ->
                if (item.isVisible) {
                    MyLife(item.imageId, effectiveUserId, item.title).apply {
                        isVisible = item.isVisible
                        weight = item.weight
                    }
                } else {
                    null
                }
            }.sortedBy { it.weight }
        }

        val seeded = seedMyLifeIfEmpty(effectiveUserId, seedBase).ifEmpty {
            getMyLifeByUserId(effectiveUserId)
        }
        myLifeCacheDataSource.write(cacheKey, seeded)
        return seeded.filter { it.isVisible }.sortedBy { it.weight }
    }

    override suspend fun seedMyLifeIfEmpty(userId: String?, items: List<MyLife>): List<MyLife> {
        val effectiveUserId = normalizeUserId(userId)
        return seedMutex.withLock {
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
                newItems.distinctBy { it.dedupKey() }.sortedBy { it.weight }
            } else {
                emptyList()
            }
        }
    }
}
