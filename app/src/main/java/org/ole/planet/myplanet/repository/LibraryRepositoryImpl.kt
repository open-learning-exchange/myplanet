package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.dto.LibraryItem
import org.ole.planet.myplanet.model.dto.TagItem

class LibraryRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), LibraryRepository {

    override suspend fun getAllLibraryItems(): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java)
    }

    override suspend fun getLibraryItems(): List<LibraryItem> {
        return withRealm { realm ->
            val libs = realm.where(RealmMyLibrary::class.java).findAll()
            val linkTags = realm.where(RealmTag::class.java).equalTo("db", "resources").isNotNull("linkId").findAll()
            val allTags = realm.where(RealmTag::class.java).isNotNull("name").findAll()

            val tagMap = allTags.map { tag ->
                tag.id to TagItem(tag.id, tag._id, tag.name)
            }.toMap()

            val resourceTagMap = linkTags.groupBy { it.linkId }
                .mapValues { entry ->
                    entry.value.mapNotNull { linkTag -> tagMap[linkTag.tagId] }
                }

            libs.map { item ->
                LibraryItem(
                    id = item.id,
                    _id = item._id,
                    _rev = item._rev,
                    title = item.title,
                    description = item.description,
                    timesRated = item.timesRated,
                    averageRating = item.averageRating,
                    createdDate = item.createdDate,
                    uploadDate = item.uploadDate,
                    resourceOffline = item.isResourceOffline(),
                    resourceId = item.resourceId,
                    resourceLocalAddress = item.resourceLocalAddress,
                    filename = item.filename,
                    mediaType = item.mediaType,
                    language = item.language,
                    subject = item.subject?.toList(),
                    level = item.level?.toList(),
                    userId = item.userId?.toList(),
                    tags = resourceTagMap[item.resourceId] ?: emptyList()
                )
            }
        }
    }

    override suspend fun getLibraryItemById(id: String): RealmMyLibrary? {
        return findByField(RealmMyLibrary::class.java, "id", id)
    }

    override suspend fun getLibraryItemByResourceId(resourceId: String): RealmMyLibrary? {
        return findByField(RealmMyLibrary::class.java, "resourceId", resourceId)
            ?: findByField(RealmMyLibrary::class.java, "_id", resourceId)
    }

    override suspend fun getLibraryItemsByIds(ids: Collection<String>): List<RealmMyLibrary> {
        if (ids.isEmpty()) return emptyList()

        return queryList(RealmMyLibrary::class.java) {
            this.`in`("_id", ids.toTypedArray())
        }
    }

    override suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("resourceLocalAddress", localAddress)
        }
    }

    override suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> {
        if (userId == null) return emptyList()

        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        return filterLibrariesNeedingUpdate(results)
            .filter { it.userId?.contains(userId) == true }
    }

    override suspend fun getMyLibrary(userId: String?): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getStepResources(stepId: String?, resourceOffline: Boolean): List<RealmMyLibrary> {
        if (stepId == null) return emptyList()

        return queryList(RealmMyLibrary::class.java) {
            equalTo("stepId", stepId)
            equalTo("resourceOffline", resourceOffline)
            isNotNull("resourceLocalAddress")
        }
    }

    override suspend fun countLibrariesNeedingUpdate(userId: String?): Int {
        if (userId == null) return 0

        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        return filterLibrariesNeedingUpdate(results)
            .count { it.userId?.contains(userId) == true }
    }

    override suspend fun saveLibraryItem(item: RealmMyLibrary) {
        save(item)
    }

    override suspend fun markResourceAdded(userId: String?, resourceId: String) {
        withRealmAsync { realm ->
            RealmRemovedLog.onAdd(realm, "resources", userId, resourceId)
        }
    }

    override suspend fun updateUserLibrary(
        resourceId: String,
        userId: String,
        isAdd: Boolean,
    ): RealmMyLibrary? {
        executeTransaction { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceId", resourceId)
                .findFirst()?.let { library ->
                    if (isAdd) {
                        library.setUserId(userId)
                    } else {
                        library.removeUserId(userId)
                    }
                }
        }
        withRealmAsync { realm ->
            if (isAdd) {
                onAdd(realm, "resources", userId, resourceId)
            } else {
                onRemove(realm, "resources", userId, resourceId)
            }
        }
        return getLibraryItemByResourceId(resourceId)
            ?: getLibraryItemById(resourceId)
    }

    override suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit) {
        update(RealmMyLibrary::class.java, "id", id, updater)
    }

    override suspend fun markResourceOfflineByLocalAddress(localAddress: String) {
        executeTransaction { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceLocalAddress", localAddress)
                .findAll()
                ?.forEach { library ->
                    library.resourceOffline = true
                    library.downloadedRev = library._rev
                }
        }
    }

    private fun filterLibrariesNeedingUpdate(results: Collection<RealmMyLibrary>): List<RealmMyLibrary> {
        return results.filter { it.needToUpdate() }
    }
}
