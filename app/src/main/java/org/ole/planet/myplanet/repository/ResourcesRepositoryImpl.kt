package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class ResourcesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val activitiesRepository: ActivitiesRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val ratingsRepository: RatingsRepository,
    private val tagsRepository: TagsRepository,
    private val searchActivityDao: SearchActivityDao,
    private val resourceActivityDao: ResourceActivityDao,
    private val removedLogDao: RemovedLogDao,
    private val teamsSyncRepositoryLazy: dagger.Lazy<TeamsSyncRepository>,
    private val myLibraryDao: MyLibraryDao,
    private val userDao: UserDao,
    private val teamDao: TeamDao
) : ResourcesRepository {

    // Shelf membership is stored as a JSON userId list; match a single entry with LIKE %"id"%.
    private fun userIdPattern(userId: String): String {
        val escaped = userId
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%\"$escaped\"%"
    }

    override suspend fun getAllLibraries(): List<MyLibrary> {
        return myLibraryDao.getAll()
    }

    override suspend fun search(query: String, isMyCourseLib: Boolean, userId: String?): List<MyLibrary> {
        val base = when {
            userId != null -> if (isMyCourseLib) {
                myLibraryDao.getPublicForUserPattern(userIdPattern(userId))
            } else {
                myLibraryDao.getPublicNotUserPattern(userIdPattern(userId))
            }
            isMyCourseLib -> return emptyList()
            else -> myLibraryDao.getPublic()
        }

        if (query.isEmpty()) {
            return base
        }

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(query)

        val matching = base.filter { item ->
            val titleNormal = item.titleNormal ?: return@filter false
            normalizedQueryParts.all { titleNormal.contains(it) }
        }

        val startsWithQuery = mutableListOf<MyLibrary>()
        val containsQuery = mutableListOf<MyLibrary>()
        for (item in matching) {
            val titleNormal = item.titleNormal ?: continue
            if (titleNormal.startsWith(normalizedQuery)) {
                startsWithQuery.add(item)
            } else {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    override suspend fun getResourceById(id: String): MyLibrary? {
        return myLibraryDao.getById(id)
    }

    override suspend fun updateLocalResource(
        resourceId: String,
        title: String,
        author: String,
        year: String,
        description: String,
        publisher: String,
        linkToLicense: String,
        subjects: List<String>?,
        levels: List<String>?
    ): Result<Unit> {
        return runCatching {
            val resource = myLibraryDao.getById(resourceId) ?: return@runCatching
            resource.title = title
            resource.titleNormal = Utilities.normalizeText(title)
            resource.author = author
            resource.year = year
            resource.description = description
            resource.publisher = publisher
            resource.linkToLicense = linkToLicense
            resource.subject = subjects?.toList() ?: emptyList()
            resource.level = levels?.toList() ?: emptyList()
            myLibraryDao.upsert(resource)
        }
    }

    override suspend fun getLibraryItemById(id: String): MyLibrary? {
        return myLibraryDao.getById(id)
    }

    override suspend fun getLibraryItemByResourceId(resourceId: String): MyLibrary? {
        return myLibraryDao.getByResourceId(resourceId)
            ?: myLibraryDao.getByUnderscoreId(resourceId)
    }

    override suspend fun getLibraryItemsByIds(ids: Collection<String>): List<MyLibrary> {
        if (ids.isEmpty()) return emptyList()
        return myLibraryDao.getByUnderscoreIds(ids.toList())
    }

    override suspend fun getLibraryItemsByResourceIds(ids: Collection<String>): List<MyLibrary> {
        if (ids.isEmpty()) return emptyList()
        return myLibraryDao.getByResourceIds(ids.toList())
    }

    override suspend fun getTeamPrivateResources(teamId: String): List<MyLibrary> {
        return myLibraryDao.getTeamPrivate(teamId)
    }

    override suspend fun getPublicLibraryItems(): List<MyLibrary> {
        return myLibraryDao.getPublic()
    }

    override suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<MyLibrary> {
        return myLibraryDao.getByLocalAddress(localAddress)
    }

    override suspend fun getLibraryListForUser(userId: String?): List<MyLibrary> {
        if (userId == null) return emptyList()
        return myLibraryDao.getPublicForUserPattern(userIdPattern(userId))
            .filter { it.needToUpdate() }
    }

    override suspend fun getLibraryForSelectedUser(userId: String): List<MyLibrary> {
        return getLibraryListForUser(userId)
    }

    override suspend fun getMyLibrary(userId: String?): List<MyLibrary> {
        if (userId.isNullOrBlank()) return emptyList()
        return myLibraryDao.getForUserPattern(userIdPattern(userId))
    }

    override suspend fun getAllStepResources(stepId: String?): List<MyLibrary> {
        if (stepId == null) return emptyList()
        return myLibraryDao.getByStepId(stepId)
    }

    override suspend fun countLibrariesNeedingUpdate(userId: String?): Int {
        if (userId == null) return 0
        return myLibraryDao.getPublicForUserPattern(userIdPattern(userId))
            .count { it.needToUpdate() }
    }

    override suspend fun resourceTitleExists(title: String): Boolean {
        return myLibraryDao.countByTitle(title) > 0
    }

    private suspend fun saveLibraryItem(item: MyLibrary) {
        myLibraryDao.upsert(item)
    }

    override suspend fun saveLocalResource(
        request: LocalResourceRequest
    ): Result<Unit> {
        val title = request.title ?: return Result.failure(Exception("Title is missing"))

        if (resourceTitleExists(title)) {
            return Result.failure(Exception("Resource title already exists"))
        }

        val _id = UUID.randomUUID().toString()
        val resource = MyLibrary().apply {
            this._id = id
            this.title = title
            this.titleNormal = Utilities.normalizeText(title)
            this.addedBy = request.addedBy
            this.author = request.author
            this.resourceId = id
            this.year = request.year
            this.description = request.description
            this.publisher = request.publisher
            this.linkToLicense = request.linkToLicense
            this.openWith = request.openWith
            this.language = request.language
            this.mediaType = request.mediaType
            this.resourceType = request.resourceType
            this.subject = request.subjects?.toList() ?: emptyList()
            this.userId = emptyList()
            this.level = request.levels?.toList() ?: emptyList()
            this.createdDate = Calendar.getInstance().timeInMillis
            this.resourceFor = request.resourceFor?.toList() ?: emptyList()
            this.resourceLocalAddress = request.resourceUrl
            this.resourceOffline = true
            this.filename = request.resourceUrl?.let { it.substring(it.lastIndexOf("/")) }
            this.isPrivate = request.isPrivateTeamResource
            this.privateFor = if (request.isPrivateTeamResource) request.teamId else null

            if (!request.isPrivateTeamResource) {
                setUserId(request.userId)
            }
        }

        saveLibraryItem(resource)

        if (!request.isPrivateTeamResource) {
            markResourceAdded(request.userId, resource.id)
        }

        if (request.teamId != null) {
            teamsSyncRepositoryLazy.get().syncTeamActivities()
        }

        return Result.success(Unit)
    }

    override suspend fun markResourceAdded(userId: String?, resourceId: String) {
        activitiesRepository.markResourceAdded(userId, resourceId)
    }

    override suspend fun updateUserLibrary(
        resourceId: String,
        userId: String,
        isAdd: Boolean,
    ): MyLibrary? {
        myLibraryDao.getByResourceId(resourceId)?.let { library ->
            if (isAdd) {
                library.setUserId(userId)
            } else {
                library.removeUserId(userId)
            }
            myLibraryDao.upsert(library)
        }
        if (isAdd) {
            activitiesRepository.markResourceAdded(userId, resourceId)
        } else {
            activitiesRepository.markResourceRemoved(userId, resourceId)
        }
        return getLibraryItemByResourceId(resourceId)
            ?: getLibraryItemById(resourceId)
    }

    override suspend fun updateLibraryItem(id: String, updater: (MyLibrary) -> Unit) {
        val item = myLibraryDao.getById(id) ?: return
        updater(item)
        myLibraryDao.upsert(item)
    }

    override suspend fun markResourceOfflineByUrl(url: String) {
        val localAddress = FileUtils.getFileNameFromUrl(url)
        if (localAddress.isNotBlank()) {
            markResourceOfflineByLocalAddress(localAddress)
        }
    }

    private suspend fun markResourceOfflineByLocalAddress(localAddress: String) {
        val results = myLibraryDao.getByLocalAddress(localAddress)
        results.forEach { library ->
            library.resourceOffline = true
            library.downloadedRev = library._rev
        }
        if (results.isNotEmpty()) {
            myLibraryDao.upsertAll(results)
        }
    }

    override fun getRecentResources(userId: String): Flow<List<MyLibrary>> {
        return myLibraryDao.getRecentForUserPatternFlow(userIdPattern(userId))
    }

    override fun getPendingDownloads(userId: String): Flow<List<MyLibrary>> {
        return myLibraryDao.getPendingDownloadsForUserPatternFlow(userIdPattern(userId))
    }

    override suspend fun markAllResourcesOffline(isOffline: Boolean) {
        myLibraryDao.setAllOffline(isOffline)
    }

    override suspend fun saveSearchActivity(
        userName: String,
        searchText: String,
        planetCode: String,
        parentCode: String,
        tags: List<TagEntity>,
        subjects: Set<String>,
        languages: Set<String>,
        levels: Set<String>,
        mediums: Set<String>
    ) {
        val filter = JsonObject().apply {
            add("tags", TagEntity.getTagsArray(tags))
            add("subjects", getJsonArrayFromList(subjects))
            add("language", getJsonArrayFromList(languages))
            add("level", getJsonArrayFromList(levels))
            add("mediaType", getJsonArrayFromList(mediums))
        }
        val filterPayload = JsonUtils.gson.toJson(filter)

        searchActivityDao.insert(
            SearchActivity(
                _id = UUID.randomUUID().toString(),
                user = userName,
                time = Calendar.getInstance().timeInMillis,
                createdOn = planetCode,
                parentCode = parentCode,
                text = searchText,
                type = "resources",
                filter = filterPayload
            )
        )
    }

    private fun getJsonArrayFromList(list: Set<String>): JsonArray {
        val array = JsonArray()
        list.forEach { array.add(it) }
        return array
    }

    override suspend fun downloadResources(resources: List<MyLibrary>): Boolean {
        return try {
            val urls = resources.filter { !it.isResourceOffline() }.mapNotNull { it.resourceRemoteAddress }
            if (urls.isEmpty()) {
                return false
            }
            DownloadUtils.openPriorityDownloadService(context, ArrayList(urls))
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun downloadResourcesPriority(resources: List<MyLibrary>): Boolean {
        return downloadResources(resources)
    }

    override suspend fun getAllLibrariesToSync(): List<MyLibrary> {
        return myLibraryDao.getSyncable().filter { it.needToUpdate() }
    }

    override suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String): Result<Unit> {
        return runCatching {
            if (resourceIds.isEmpty() || userId.isBlank()) return@runCatching

            val libraryItems = myLibraryDao.getByResourceIdsNotUserPattern(resourceIds, userIdPattern(userId))
            libraryItems.forEach { it.setUserId(userId) }
            if (libraryItems.isNotEmpty()) {
                myLibraryDao.upsertAll(libraryItems)
            }
            removedLogDao.deleteByTypeUserAndDocs("resources", userId, resourceIds)
        }
    }

    override suspend fun addAllResourcesToUserLibrary(resources: List<MyLibrary>, userId: String): Result<Unit> {
        val resourceIds = resources.mapNotNull { it.resourceId }
        return addResourcesToUserLibrary(resourceIds, userId)
    }

    override suspend fun observeOpenedResourceIds(userId: String): Flow<Set<String>> {
        val userName = userDao.getById(userId)?.name ?: return flowOf(emptySet())

        return resourceActivityDao.observeByUserAndType(userName, "resource_opened")
            .map { activities -> activities.mapNotNull { it.resourceId }.toSet() }
    }

    override suspend fun getDownloadSuggestionList(userId: String?): List<MyLibrary> {
        val targetUserId = userId ?: sharedPrefManager.getUserId().ifEmpty { null }

        if (!targetUserId.isNullOrBlank()) {
            val userLibrariesNeedingUpdate = myLibraryDao.getPublicForUserPattern(userIdPattern(targetUserId))
                .filter { it.needToUpdate() }
            if (userLibrariesNeedingUpdate.isNotEmpty()) {
                return userLibrariesNeedingUpdate
            }
        }

        return myLibraryDao.getPublic().filter { it.needToUpdate() }
    }

    override suspend fun removeDeletedResources(currentIds: List<String?>) {
        val validCurrentIds = currentIds.filterNotNull().toSet()
        if (validCurrentIds.isNotEmpty()) {
            myLibraryDao.deleteStalePublicNotIn(validCurrentIds.toList())
        } else {
            myLibraryDao.deleteAllStalePublic()
        }
    }

    override suspend fun getMyLibIds(userId: String): JsonArray {
        val libs = myLibraryDao.getForUserPattern(userIdPattern(userId))
        val jsonArray = JsonArray()
        libs.forEach { jsonArray.add(it.id) }
        return jsonArray
    }

    override suspend fun removeResourceFromShelf(resourceId: String, userId: String) {
        updateUserLibrary(resourceId, userId, false)
    }

    override suspend fun getHtmlResourceDownloadUrls(resourceId: String): ResourceUrlsResponse {
        val resource = getLibraryItemByResourceId(resourceId) ?: return ResourceUrlsResponse.ResourceNotFound
        if (resource.attachments.isNullOrEmpty()) return ResourceUrlsResponse.NoAttachments

        val urls = resource.attachments?.mapNotNull { attachment ->
            attachment.name?.let { name ->
                val baseDir = File(context.getExternalFilesDir(null), "ole/$resourceId")
                val lastSlashIndex = name.lastIndexOf('/')
                if (lastSlashIndex > 0) {
                    val dirPath = name.substring(0, lastSlashIndex)
                    File(baseDir, dirPath).mkdirs()
                }
                UrlUtils.getUrl(resourceId, name)
            }
        }

        return if (!urls.isNullOrEmpty()) {
            ResourceUrlsResponse.Success(urls)
        } else {
            ResourceUrlsResponse.Error
        }
    }

    override suspend fun getFilterFacets(libraries: List<MyLibrary>): Map<String, Set<String>> {
        return mapOf(
            "languages" to libraries.mapNotNull { it.language }.filterNot { it.isBlank() }.toSet(),
            "subjects" to libraries.flatMap { it.subject ?: emptyList() }.toSet(),
            "mediums" to libraries.mapNotNull { it.mediaType }.filterNot { it.isBlank() }.toSet(),
            "levels" to libraries.flatMap { it.level ?: emptyList() }.toSet()
        )
    }

    override suspend fun batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int {
        var processedCount = 0
        documents.forEach { doc ->
            try {
                val resourceId = JsonUtils.getString("_id", doc)
                val existing = myLibraryDao.getById(resourceId)
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        userId = shelfId,
                        existing = existing
                    )
                )
                if (library != null) {
                    myLibraryDao.upsert(library)
                    processedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return processedCount
    }

    override suspend fun batchInsertResources(documents: List<JsonObject>): List<String> {
        val savedIds = mutableListOf<String>()
        documents.forEach { doc ->
            try {
                val _id = JsonUtils.getString("_id", doc)
                if (_id.startsWith("_design")) return@forEach
                val existing = myLibraryDao.getById(_id)
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        existing = existing
                    )
                )
                if (library != null) {
                    myLibraryDao.upsert(library)
                    savedIds.add(_id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return savedIds
    }

    private suspend fun getResourceRatingsBulk(ids: List<String>, userId: String?): Map<String?, JsonObject> {
        val allRatings = ratingsRepository.getResourceRatings(userId)
        val filteredRatings = HashMap<String?, JsonObject>(ceil(ids.size / 0.75).toInt())
        for (id in ids) {
            allRatings[id]?.let {
                filteredRatings[id] = it
            }
        }
        return filteredRatings
    }

    private suspend fun getResourceTagsBulk(ids: List<String>): Map<String, List<TagEntity>> {
        return tagsRepository.getTagsForResources(ids)
    }

    override suspend fun getEnrichedLibraries(isMyCourseLib: Boolean, modelId: String?): List<LibraryWithMetadata> {
        val allLibraryItems = if (isMyCourseLib) {
            getMyLibrary(modelId)
        } else if (modelId != null) {
            myLibraryDao.getPublicNotUserPattern(userIdPattern(modelId))
        } else {
            myLibraryDao.getPublic()
        }

        val allResourceIds = allLibraryItems.mapNotNull { it.resourceId ?: it.id }

        val map = HashMap(getResourceRatingsBulk(allResourceIds, modelId))
        val tagsMap = getResourceTagsBulk(allResourceIds)

        return allLibraryItems.map { library ->
            val resourceId = library.resourceId ?: library.id
            val rating = map[resourceId]
            val tags = tagsMap[resourceId] ?: emptyList()
            LibraryWithMetadata(library, rating, tags)
        }
    }

    override suspend fun getResourceTitlesMap(): Map<String, String> {
        return myLibraryDao.getWithResourceId()
            .associate { (it.resourceId ?: "") to (it.title ?: "") }
    }

    override suspend fun getCourseResourcesGroupedByStepId(courseId: String): Map<String?, List<MyLibrary>> {
        return myLibraryDao.getByCourseId(courseId).groupBy { it.stepId }
    }

    override suspend fun markResourcesAsNotOffline(resourceIds: Collection<String>) {
        if (resourceIds.isEmpty()) return
        val results = myLibraryDao.getOfflineByResourceIds(resourceIds.toList())
        results.forEach { it.resourceOffline = false }
        if (results.isNotEmpty()) {
            myLibraryDao.upsertAll(results)
        }
    }

    override suspend fun getPendingResourceUploads(): List<MyLibrary> {
        return myLibraryDao.getPendingUploads()
    }

    override suspend fun markResourceUploaded(
        localId: String,
        remoteId: String,
        remoteRev: String,
        planetCode: String?
    ): Boolean {
        val library = myLibraryDao.getById(localId) ?: return false
        library._id = remoteId
        library._rev = remoteRev
        myLibraryDao.upsert(library)

        // Private resources also create a local team-resource link (still a Realm model).
        if (library.isPrivate && !library.privateFor.isNullOrBlank()) {
            val resolvedPlanetCode = planetCode?.takeIf { it.isNotBlank() }
                ?: sharedPrefManager.getPlanetCode()
            teamDao.upsert(
                MyTeam(
                    _id = UUID.randomUUID().toString(),
                    teamId = library.privateFor,
                    title = library.title,
                    resourceId = remoteId,
                    sourcePlanet = resolvedPlanetCode,
                    teamType = "local",
                    teamPlanetCode = resolvedPlanetCode,
                    docType = "resourceLink",
                    updated = true,
                )
            )
        }
        return true
    }
}
