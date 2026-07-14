package org.ole.planet.myplanet.repository

import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.di.LegacyRealmDispatcher
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.services.SharedPrefManager

data class CachedMyLifeItem(
    var imageId: String?,
    var title: String?,
    var isVisible: Boolean,
    var weight: Int
)

class LifeRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @LegacyRealmDispatcher legacyRealmDispatcher: CoroutineDispatcher,
    private val sharedPrefManager: SharedPrefManager,
    private val gson: Gson,
    @ApplicationScope private val appScope: CoroutineScope
) : RealmRepository(databaseService, legacyRealmDispatcher), LifeRepository {

    private val MY_LIFE_CACHE_PREFIX = "myLifeCache_"

    override suspend fun updateVisibility(isVisible: Boolean, myLifeId: String) {
        executeTransaction { realm ->
            val myLife = realm.where(RealmMyLife::class.java).equalTo("_id", myLifeId).findFirst()
            myLife?.let {
                it.isVisible = isVisible
            }
        }
    }

    override suspend fun updateMyLifeListOrder(list: List<RealmMyLife>) {
        executeTransaction { realm ->
            val ids = list.mapNotNull { it._id }.toTypedArray()
            if (ids.isEmpty()) return@executeTransaction
            val managedLives = realm.where(RealmMyLife::class.java).`in`("_id", ids).findAll()
            managedLives.forEach { managedLife ->
                val index = list.indexOfFirst { it._id == managedLife._id }
                if (index != -1) {
                    managedLife.weight = index
                }
            }
        }
    }

    override suspend fun getMyLifeByUserId(userId: String?, ensureLatest: Boolean): List<RealmMyLife> {
        return queryList(RealmMyLife::class.java, ensureLatest) {
            equalTo("userId", userId)
        }.sortedBy { it.weight }
    }

    override suspend fun getMyLifeForDashboard(userId: String, seedBase: List<RealmMyLife>): List<RealmMyLife> {
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
                    val realmItems = getMyLifeByUserId(userId, ensureLatest = false)
                    if (realmItems.isNotEmpty()) {
                        cacheMyLifeItems(userId, realmItems)
                    }
                }
                return cached.filter { it.isVisible }.map { item ->
                    RealmMyLife(item.imageId, userId, item.title).apply {
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

    private fun cacheMyLifeItems(userId: String, items: List<RealmMyLife>) {
        val cached = items.map { CachedMyLifeItem(it.imageId, it.title, it.isVisible, it.weight) }
        sharedPrefManager.rawPreferences.edit { putString("$MY_LIFE_CACHE_PREFIX$userId", gson.toJson(cached)) }
    }

    override suspend fun seedMyLifeIfEmpty(userId: String?, items: List<RealmMyLife>) {
        executeTransaction { realm ->
            val existing = realm.where(RealmMyLife::class.java).equalTo("userId", userId).findAll()
            if (existing.isEmpty()) {
                var weight = 1
                val newItems = items.map { item ->
                    RealmMyLife().apply {
                        _id = UUID.randomUUID().toString()
                        title = item.title
                        imageId = item.imageId
                        this.weight = weight++
                        this.userId = item.userId
                        isVisible = true
                    }
                }
                realm.insertOrUpdate(newItems)
            }
        }
    }
}
