package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUser
import dagger.hilt.android.qualifiers.ApplicationContext
import org.ole.planet.myplanet.utils.JsonUtils.getString
import java.util.UUID
import javax.inject.Inject

class UploadRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository
) : RealmRepository(databaseService), UploadRepository {

    override suspend fun getAchievementsForUpload(): List<RealmAchievement> = withContext(Dispatchers.IO) {
        withRealm { realm ->
            realm.copyFromRealm(realm.where(RealmAchievement::class.java).findAll())
        }
    }

    override suspend fun getPhotosForUpload(photoIds: Array<String>): List<RealmSubmitPhotos> = withContext(Dispatchers.IO) {
        withRealm { realm ->
            val results = realm.where(RealmSubmitPhotos::class.java).`in`("id", photoIds).findAll()
            realm.copyFromRealm(results)
        }
    }

    override suspend fun getResourcesForUpload(user: RealmUser?): List<ResourceData> = withContext(Dispatchers.IO) {
        withRealm(ensureLatest = true) { realm ->
            val data = realm.where(RealmMyLibrary::class.java).isNull("_rev").findAll()
            if (data.isEmpty()) {
                emptyList()
            } else {
                data.map { library ->
                    ResourceData(
                        libraryId = library.id,
                        title = library.title,
                        isPrivate = library.isPrivate,
                        privateFor = library.privateFor,
                        serialized = RealmMyLibrary.serialize(library, user)
                    )
                }
            }
        }
    }

    override suspend fun updateResourceAfterUpload(
        libraryId: String?,
        rev: String?,
        id: String?,
        isPrivate: Boolean,
        privateFor: String?,
        title: String?,
        userPlanetCode: String?,
        sharedPrefPlanetCode: String?
    ) = withContext(Dispatchers.IO) {
        executeTransaction { transactionRealm ->
            transactionRealm.where(RealmMyLibrary::class.java)
                .equalTo("id", libraryId)
                .findFirst()?.let { sub ->
                    sub._rev = rev
                    sub._id = id
                }

            if (isPrivate && !privateFor.isNullOrBlank()) {
                val planetCode = userPlanetCode?.takeIf { it.isNotBlank() } ?: sharedPrefPlanetCode
                val teamResource = transactionRealm.createObject(
                    RealmMyTeam::class.java,
                    UUID.randomUUID().toString()
                )
                teamResource.teamId = privateFor
                teamResource.title = title
                teamResource.resourceId = id
                teamResource.docType = "resourceLink"
                teamResource.updated = true
                teamResource.teamType = "local"
                teamResource.teamPlanetCode = planetCode
                teamResource.sourcePlanet = planetCode
            }
        }
    }

    override suspend fun getLibraryForUpload(libraryId: String?): RealmMyLibrary? = withContext(Dispatchers.IO) {
        withRealm { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("id", libraryId).findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun getActivitiesForUpload(): List<ActivityData> = withContext(Dispatchers.IO) {
        withRealm { realm ->
            val activities = realm.where(RealmOfflineActivity::class.java)
                .isNull("_rev").equalTo("type", "login").findAll()

            activities.mapNotNull { activity ->
                if (activity.userId?.startsWith("guest") == true) {
                    null
                } else {
                    ActivityData(
                        activityId = activity.id,
                        userId = activity.userId,
                        serialized = RealmOfflineActivity.serializeLoginActivities(activity, context)
                    )
                }
            }
        }
    }

    override suspend fun updateActivitiesAfterUpload(successfulUpdates: Map<String, JsonObject?>) = withContext(Dispatchers.IO) {
        if (successfulUpdates.isNotEmpty()) {
            val idsToUpdate = successfulUpdates.keys.toTypedArray()
            executeTransaction { transactionRealm ->
                val activities = transactionRealm.where(RealmOfflineActivity::class.java)
                    .`in`("id", idsToUpdate)
                    .findAll()

                activities.forEach { activity ->
                    val updateData = successfulUpdates[activity.id]
                    activity.changeRev(updateData)
                }
            }
        }
    }

    override suspend fun getTeamLogsForUpload(): List<TeamLogData> = withContext(Dispatchers.IO) {
        withRealm { realm ->
            val results = realm.where(RealmTeamLog::class.java).isNull("_rev").findAll()
            results.map { log ->
                TeamLogData(
                    id = log.id,
                    time = log.time,
                    user = log.user,
                    type = log.type,
                    serialized = RealmTeamLog.serializeTeamActivities(log, context)
                )
            }
        }
    }

    override suspend fun updateTeamLogsAfterUpload(successfulUploads: List<UploadResultDto>) = withContext(Dispatchers.IO) {
        if (successfulUploads.isNotEmpty()) {
            executeTransaction { realm ->
                val ids = successfulUploads.mapNotNull { it.id }
                val managedLogs = mutableMapOf<String, RealmTeamLog>()

                if (ids.isNotEmpty()) {
                    ids.chunked(999).forEach { chunk ->
                        val results = realm.where(RealmTeamLog::class.java)
                            .`in`("id", chunk.toTypedArray())
                            .findAll()
                        results.forEach { log ->
                            log.id?.let { id -> managedLogs[id] = log }
                        }
                    }
                }

                val uploadsWithoutId = successfulUploads.filter { it.id == null }
                val fallbackLogs = mutableMapOf<Triple<Long?, String?, String?>, RealmTeamLog>()

                if (uploadsWithoutId.isNotEmpty()) {
                    uploadsWithoutId.chunked(250).forEach { chunk ->
                        val query = realm.where(RealmTeamLog::class.java)
                        query.beginGroup()
                        chunk.forEachIndexed { index, upload ->
                            if (index > 0) query.or()
                            query.beginGroup()
                                .equalTo("time", upload.time)
                                .equalTo("user", upload.user)
                                .equalTo("type", upload.type)
                            .endGroup()
                        }
                        query.endGroup()

                        val results = query.findAll()
                        results.forEach { log ->
                            val key = Triple(log.time, log.user, log.type)
                            fallbackLogs[key] = log
                        }
                    }
                }

                successfulUploads.forEach { upload ->
                    val managedLog = if (upload.id != null) {
                        managedLogs[upload.id]
                    } else {
                        val key = Triple(upload.time, upload.user, upload.type)
                        fallbackLogs[key]
                    }
                    managedLog?._id = upload._id
                    managedLog?._rev = upload._rev
                }
            }
        }
    }

    override suspend fun getNewsForUpload(): List<NewsUploadData> = withContext(Dispatchers.IO) {
        withRealm { realm ->
            realm.where(RealmNews::class.java)
                .findAll()
                .mapNotNull { news ->
                    if (news.userId?.startsWith("guest") == true) null
                    else NewsUploadData(
                        id = news.id,
                        _id = news._id,
                        message = news.message,
                        imageUrls = news.imageUrls?.toList() ?: emptyList(),
                        newsJson = chatRepository.serializeNews(news)
                    )
                }
        }
    }

    override suspend fun updateNewsAfterUpload(successfulUpdates: List<NewsUpdateData>) = withContext(Dispatchers.IO) {
        if (successfulUpdates.isNotEmpty()) {
            executeTransaction { realm ->
                val ids = successfulUpdates.mapNotNull { it.id }
                val managedNewsMap = mutableMapOf<String, RealmNews>()

                if (ids.isNotEmpty()) {
                    ids.chunked(999).forEach { chunk ->
                        val results = realm.where(RealmNews::class.java)
                            .`in`("id", chunk.toTypedArray())
                            .findAll()
                        results.forEach { n ->
                            n.id?.let { id -> managedNewsMap[id] = n }
                        }
                    }
                }

                val gson = com.google.gson.Gson()
                successfulUpdates.forEach { update ->
                    update.id?.let { id ->
                        managedNewsMap[id]?.let { managedNews ->
                            managedNews.imageUrls?.clear()
                            managedNews._id = getString("id", update.body)
                            managedNews._rev = getString("rev", update.body)
                            managedNews.images = gson.toJson(update.imagesArray)
                        }
                    }
                }
            }
        }
    }
}
