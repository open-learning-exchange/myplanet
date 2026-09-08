package org.ole.planet.myplanet.repository

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.OfflineResourceItem
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.distinctByContent

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
    private val userRepository: UserRepository,
    private val teamsRepositoryLazy: dagger.Lazy<TeamsRepository>,
    private val userSessionManager: UserSessionManager,
    private val configurationsRepository: ConfigurationsRepository,
    private val dispatcherProvider: DispatcherProvider
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
        if (query.isEmpty()) {
            return when {
                userId != null -> if (isMyCourseLib) {
                    myLibraryDao.getPublicForUserPattern(userIdPattern(userId))
                } else {
                    myLibraryDao.getPublicNotUserPattern(userIdPattern(userId))
                }
                isMyCourseLib -> emptyList()
                else -> myLibraryDao.getPublic()
            }
        }

        if (isMyCourseLib && userId == null) return emptyList()

        val queryParts = query.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(query)

        val queryBuilder = StringBuilder("SELECT * FROM my_library WHERE isPrivate = 0")
        val bindArgs = mutableListOf<Any>()

        if (userId != null) {
            if (isMyCourseLib) {
                queryBuilder.append(" AND userId LIKE ? ESCAPE '\\'")
                bindArgs.add(userIdPattern(userId))
            } else {
                queryBuilder.append(" AND (userId IS NULL OR userId NOT LIKE ? ESCAPE '\\')")
                bindArgs.add(userIdPattern(userId))
            }
        }

        normalizedQueryParts.forEach { token ->
            val escapedToken = token
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            queryBuilder.append(" AND titleNormal LIKE ? ESCAPE '\\'")
            bindArgs.add("%${escapedToken}%")
        }

        val matching = myLibraryDao.filterByTitleNormal(SimpleSQLiteQuery(queryBuilder.toString(), bindArgs.toTypedArray()))

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
        return myLibraryDao.getPublicNeedingUpdateForUserPattern(userIdPattern(userId))
    }

    override suspend fun getMyLibrary(userId: String?): List<MyLibrary> {
        if (userId.isNullOrBlank()) return emptyList()
        return myLibraryDao.getForUserPattern(userIdPattern(userId))
    }

    override fun getMyLibraryFlow(userId: String?): Flow<List<MyLibrary>> {
        if (userId.isNullOrBlank()) return flowOf(emptyList())
        return myLibraryDao.getForUserPatternFlow(userIdPattern(userId))
    }

    override suspend fun getAllStepResources(stepId: String?): List<MyLibrary> {
        if (stepId == null) return emptyList()
        return myLibraryDao.getByStepId(stepId)
    }

    override suspend fun countLibrariesNeedingUpdate(userId: String?): Int {
        if (userId == null) return 0
        return myLibraryDao.countPublicNeedingUpdateForUserPattern(userIdPattern(userId))
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

        val sourceFile = request.resourceUrl?.let { File(it) }
        if (sourceFile == null || !sourceFile.exists()) {
            return Result.failure(Exception("Resource file not found"))
        }

        val externalFilesDir = FileUtils.getExternalFilesDir(context)
            ?: return Result.failure(Exception("Storage unavailable"))

        val id = UUID.randomUUID().toString()
        val filename = sourceFile.name
        val destinationFile = FileUtils.getLibraryFile(externalFilesDir, id, filename)

        try {
            withContext(dispatcherProvider.io) {
                destinationFile.parentFile?.mkdirs()
                sourceFile.copyTo(destinationFile, overwrite = true)
            }
        } catch (e: IOException) {
            return Result.failure(e)
        } catch (e: SecurityException) {
            return Result.failure(e)
        }

        val resource = MyLibrary().apply {
            this.id = id
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
            this.resourceLocalAddress = filename
            this.resourceOffline = true
            this.filename = filename
            this.isPrivate = request.isPrivateTeamResource
            this.privateFor = if (request.isPrivateTeamResource) request.teamId else null

            if (!request.isPrivateTeamResource) {
                setUserId(request.userId)
            }
        }

        try {
            saveLibraryItem(resource)
        } catch (e: Exception) {
            destinationFile.delete()
            return Result.failure(e)
        }

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

    override suspend fun setUserLibrary(resourceId: String, add: Boolean): MyLibrary? {
        val userId = userRepository.getUserModel()?.id ?: return null
        val library = getLibraryItemByResourceId(resourceId) ?: getLibraryItemById(resourceId)
        if (library != null) {
            val contains = library.userId?.contains(userId) == true
            if (add && contains) return library
            if (!add && !contains) return library
        }
        val updated = updateUserLibrary(resourceId, userId, add) ?: return null
        return if ((updated.userId?.contains(userId) == true) == add) updated else null
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
        val resourceId = FileUtils.getIdFromUrl(url)
        val relativePath = FileUtils.getResourceRelativePathFromUrl(url)

        if (localAddress.isNotBlank()) {
            markResourceOfflineByLocalAddress(localAddress)
        }

        if (resourceId.isNotBlank()) {
            markResourceOfflineByResourceId(resourceId, relativePath)
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

    override suspend fun reconcileHtmlResourceOffline(resourceId: String) {
        val library = myLibraryDao.getByResourceId(resourceId) ?: return
        if (library.isResourceOffline()) {
            return
        }
        val entryFile = library.openWhichFile?.takeIf { it.isNotBlank() } ?: "index.html"
        val directory = File(MainApplication.context.getExternalFilesDir(null), "ole/$resourceId")
        val entryExists = withContext(dispatcherProvider.io) {
            FileUtils.resolveHtmlEntryFile(directory, entryFile)?.exists() == true
        }
        if (!entryExists) {
            return
        }
        library.resourceOffline = true
        library.downloadedRev = library._rev
        if (library.resourceLocalAddress.isNullOrBlank()) {
            library.resourceLocalAddress = entryFile
        }
        myLibraryDao.upsert(library)
    }

    private suspend fun markResourceOfflineByResourceId(resourceId: String, relativePath: String) {
        val library = myLibraryDao.getByResourceId(resourceId) ?: return
        val entryFile = library.openWhichFile?.takeIf { it.isNotBlank() } ?: "index.html"
        if (relativePath != entryFile) {
            return
        }
        library.resourceOffline = true
        library.downloadedRev = library._rev
        if (library.resourceLocalAddress.isNullOrBlank()) {
            library.resourceLocalAddress = relativePath
        }
        myLibraryDao.upsert(library)
    }

    override fun getRecentResources(userId: String): Flow<List<MyLibrary>> {
        return myLibraryDao.getRecentForUserPatternFlow(userIdPattern(userId)).distinctByContent { a, b ->
            // Compare CouchDB sync markers alongside fields editable/mutable locally (title, description, offline state, local path)
            a.id == b.id && a._rev == b._rev && a.title == b.title && a.description == b.description &&
                a.resourceOffline == b.resourceOffline && a.downloadedRev == b.downloadedRev &&
                a.resourceLocalAddress == b.resourceLocalAddress && a.userId == b.userId
        }
    }

    override fun getPendingDownloads(userId: String): Flow<List<String>> {
        return myLibraryDao.getPendingDownloadsForUserPatternFlow(userIdPattern(userId)).distinctUntilChanged()
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
                id = UUID.randomUUID().toString(),
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
            val urls = resources.mapNotNull { if (!it.isResourceOffline()) it.resourceRemoteAddress else null }
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

    override suspend fun downloadFiles(libraryList: List<MyLibrary>?): List<MyLibrary> {
        val files = libraryList ?: getAllLibrariesToSync()
        val urls = DownloadUtils.downloadAllFiles(files)

        MainApplication.applicationScope.launch {
            if (configurationsRepository.checkServerAvailability()) {
                if (urls.isNotEmpty()) {
                    DownloadUtils.openDownloadService(context, urls, false)
                }
            }
        }
        return files
    }

    override suspend fun getAllLibrariesToSync(): List<MyLibrary> {
        return myLibraryDao.getSyncable()
    }

    override suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String): Result<Unit> {
        return runCatching {
            if (resourceIds.isEmpty() || userId.isBlank()) return@runCatching

            val libraryItems = myLibraryDao.getByResourceIdsNotUserPattern(resourceIds, userIdPattern(userId))
            libraryItems.forEach { it.setUserId(userId) }
            if (libraryItems.isNotEmpty()) {
                myLibraryDao.upsertAll(libraryItems)
            }
            removedLogDao.deleteByTypeUserAndDocsChunked("resources", userId, resourceIds)
        }
    }

    override suspend fun addAllResourcesToUserLibrary(resources: List<MyLibrary>, userId: String): Result<Unit> {
        val resourceIds = resources.mapNotNull { it.resourceId }
        return addResourcesToUserLibrary(resourceIds, userId)
    }

    override suspend fun observeOpenedResourceIds(userId: String): Flow<Set<String>> {
        val userName = userRepository.getUserById(userId)?.name ?: return flowOf(emptySet())

        return resourceActivityDao.observeByUserAndType(userName, "resource_opened")
            .map { activities -> activities.mapNotNull { it.resourceId }.toSet() }
    }

    override suspend fun getDownloadSuggestionList(userId: String?): List<MyLibrary> {
        val targetUserId = userId ?: sharedPrefManager.getUserId().ifEmpty { null }

        if (!targetUserId.isNullOrBlank()) {
            val userLibrariesNeedingUpdate = myLibraryDao.getPublicNeedingUpdateForUserPattern(userIdPattern(targetUserId))
            if (userLibrariesNeedingUpdate.isNotEmpty()) {
                return userLibrariesNeedingUpdate
            }
        }

        return myLibraryDao.getPublicNeedingUpdate()
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
        val ids = myLibraryDao.getIdsForUserPattern(userIdPattern(userId))
        val jsonArray = JsonArray()
        ids.forEach { jsonArray.add(it) }
        return jsonArray
    }

    override suspend fun removeResourceFromShelf(resourceId: String, userId: String) {
        updateUserLibrary(resourceId, userId, false)
    }

    override suspend fun removeResourcesFromShelf(resourceIds: List<String>, userId: String): Result<Unit> {
        return runCatching {
            if (resourceIds.isEmpty() || userId.isBlank()) return@runCatching

            val libraryItems = myLibraryDao.getByResourceIds(resourceIds)
            libraryItems.forEach { it.removeUserId(userId) }
            if (libraryItems.isNotEmpty()) {
                myLibraryDao.upsertAll(libraryItems)
            }
            removedLogDao.insertAll(
                resourceIds.map { resourceId ->
                    RemovedLog().apply {
                        id = UUID.randomUUID().toString()
                        docId = resourceId
                        this.userId = userId
                        type = "resources"
                    }
                }
            )
        }
    }

    override suspend fun getHtmlResourceDownloadUrls(resourceId: String): ResourceUrlsResponse {
        val resource = getLibraryItemByResourceId(resourceId) ?: return ResourceUrlsResponse.ResourceNotFound
        if (resource.attachments.isNullOrEmpty()) return ResourceUrlsResponse.NoAttachments

        val urls = withContext(dispatcherProvider.io) {
            resource.attachments?.mapNotNull { attachment ->
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
        }

        return if (!urls.isNullOrEmpty()) {
            ResourceUrlsResponse.Success(urls)
        } else {
            ResourceUrlsResponse.Error
        }
    }

    override suspend fun getFilterFacets(libraries: List<MyLibrary>): Map<String, Set<String>> {
        val languages = mutableSetOf<String>()
        val subjects = mutableSetOf<String>()
        val mediums = mutableSetOf<String>()
        val levels = mutableSetOf<String>()

        libraries.forEach { library ->
            library.language?.takeIf { it.isNotBlank() }?.let { languages.add(it) }
            library.subject?.let { subjects.addAll(it) }
            library.mediaType?.takeIf { it.isNotBlank() }?.let { mediums.add(it) }
            library.level?.let { levels.addAll(it) }
        }

        return mapOf(
            "languages" to languages,
            "subjects" to subjects,
            "mediums" to mediums,
            "levels" to levels
        )
    }

    override suspend fun batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int {
        var processedCount = 0

        val resourceIds = documents.mapNotNull { JsonUtils.getString("_id", it).takeIf { id -> id.isNotBlank() } }
        val existingItems = mutableMapOf<String, MyLibrary>()
        if (resourceIds.isNotEmpty()) {
            resourceIds.chunked(900).forEach { chunk ->
                existingItems.putAll(myLibraryDao.getByIds(chunk).associateBy { it.id })
            }
        }

        val librariesToUpsert = mutableListOf<MyLibrary>()
        documents.forEach { doc ->
            try {
                val resourceId = JsonUtils.getString("_id", doc)
                val existing = existingItems[resourceId]
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        userId = shelfId,
                        existing = existing
                    )
                )
                if (library != null) {
                    existingItems[resourceId] = library
                    librariesToUpsert.add(library)
                    processedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (librariesToUpsert.isNotEmpty()) {
            myLibraryDao.upsertAll(librariesToUpsert)
            reconcileHtmlLibraries(librariesToUpsert)
        }
        return processedCount
    }

    override suspend fun batchInsertResources(documents: List<JsonObject>): List<String> {
        val savedIds = mutableListOf<String>()

        val validDocs = documents.filter {
            val _id = JsonUtils.getString("_id", it)
            _id.isNotBlank() && !_id.startsWith("_design")
        }
        val resourceIds = validDocs.map { JsonUtils.getString("_id", it) }
        val existingItems = mutableMapOf<String, MyLibrary>()
        if (resourceIds.isNotEmpty()) {
            resourceIds.chunked(900).forEach { chunk ->
                existingItems.putAll(myLibraryDao.getByIds(chunk).associateBy { it.id })
            }
        }

        val librariesToUpsert = mutableListOf<MyLibrary>()
        validDocs.forEach { doc ->
            try {
                val _id = JsonUtils.getString("_id", doc)
                val existing = existingItems[_id]
                val library = MyLibrary.insertMyLibrary(
                    MyLibrary.Companion.InsertParams(
                        doc = doc,
                        spm = sharedPrefManager,
                        existing = existing
                    )
                )
                if (library != null) {
                    existingItems[_id] = library
                    librariesToUpsert.add(library)
                    savedIds.add(_id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (librariesToUpsert.isNotEmpty()) {
            myLibraryDao.upsertAll(librariesToUpsert)
            reconcileHtmlLibraries(librariesToUpsert)
        }
        return savedIds
    }

    // Detects HTML resources already present on disk from a prior install/sync that never got a resourceLocalAddress.
    private suspend fun reconcileHtmlLibraries(libraries: List<MyLibrary>) {
        libraries.forEach { library ->
            if (library.mediaType == "HTML" && library.resourceLocalAddress.isNullOrBlank()) {
                val resourceId = library.resourceId ?: return@forEach
                try {
                    reconcileHtmlResourceOffline(resourceId)
                } catch (e: Exception) {
                    Log.w("ResourcesRepository", "reconcileHtmlResourceOffline failed for $resourceId", e)
                }
            }
        }
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

    override suspend fun getResourceListModels(isMyCourseLib: Boolean, modelId: String?): List<ResourceListModel> {
        val enrichedLibraries = getEnrichedLibraries(isMyCourseLib, modelId)
        return enrichedLibraries
            .sortedByDescending { (library, _, _) -> library.isResourceOffline() }
            .map { (library, rating, libraryTags) ->
                val item = ResourceItem(
                    id = library.id,
                    title = library.title,
                    description = library.description,
                    createdDate = library.createdDate,
                    averageRating = library.averageRating,
                    timesRated = library.timesRated,
                    resourceId = library.resourceId,
                    isOffline = library.isResourceOffline(),
                    _rev = library._rev,
                    uploadDate = library.uploadDate,
                    filename = library.filename,
                    resourceLocalAddress = library.resourceLocalAddress
                )
                val tags = libraryTags.map { tag -> TagItem(tag.id, tag.name) }
                ResourceListModel(library, item, rating, tags)
            }
    }

    private suspend fun getEnrichedLibraries(isMyCourseLib: Boolean, modelId: String?): List<LibraryWithMetadata> {
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
        return myLibraryDao.getResourceTitles()
            .associate { (it.resourceId ?: "") to (it.title ?: "") }
    }

    override suspend fun markResourcesAsNotOffline(resourceIds: Collection<String>) {
        if (resourceIds.isEmpty()) return
        myLibraryDao.markAsNotOfflineByResourceIds(resourceIds.toList())
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

        // Private resources also create a local team-resource link.
        if (library.isPrivate && !library.privateFor.isNullOrBlank()) {
            teamsRepositoryLazy.get().createLocalResourceLink(
                teamId = library.privateFor!!,
                resourceId = remoteId,
                title = library.title,
                planetCode = planetCode
            )
        }
        return true
    }

    override suspend fun trackResourceOpen(item: MyLibrary) {
        userSessionManager.setResourceOpenCount(item, UserSessionManager.KEY_RESOURCE_OPEN)
    }

    override suspend fun getOfflineResourceItems(
        oleDirPath: String,
        extensions: Set<String>,
        allKnownExtensions: Set<String>
    ): List<OfflineResourceItem> = withContext(dispatcherProvider.io) {
        val oleDir = File(oleDirPath)
        if (!oleDir.exists() || !oleDir.isDirectory) return@withContext emptyList()

        val titleMap = getResourceTitlesMap()

        val grouped = mutableMapOf<String, MutableList<File>>()
        oleDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val ext = file.extension.lowercase()
            val matchesCategory = if (extensions.isEmpty()) {
                ext !in allKnownExtensions
            } else {
                ext in extensions
            }
            if (matchesCategory) {
                val resourceId = file.parentFile?.name ?: return@forEach
                grouped.getOrPut(resourceId) { mutableListOf() }.add(file)
            }
        }

        return@withContext grouped.map { (resourceId, files) ->
            val totalSize = files.sumOf { it.length() }
            val title = titleMap[resourceId]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.storage_unknown_resource)
            OfflineResourceItem(resourceId, title, files.map { it.absolutePath }, totalSize)
        }.sortedBy { it.title }
    }

    override suspend fun deleteOfflineResources(oleDirPath: String, items: List<OfflineResourceItem>) = withContext(dispatcherProvider.io) {
        val oleDir = File(oleDirPath)
        items.forEach { item ->
            item.filePaths.forEach { File(it).delete() }
            val parentDir = oleDir.resolve(item.resourceId)
            if (parentDir.exists() && parentDir.list().isNullOrEmpty()) {
                parentDir.delete()
            }
        }
        val deletedIds = items.map { it.resourceId }.toSet()
        markResourcesAsNotOffline(deletedIds)
    }

    override suspend fun getPrivateImageUrlsCreatedAfter(timestamp: Long): List<String> {
        return myLibraryDao.getPrivateImagesCreatedAfter(timestamp)
            .mapNotNull { it.resourceRemoteAddress }
    }
}
