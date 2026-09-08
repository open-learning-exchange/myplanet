package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.utils.ResourcesSearchUtils

data class ResourceFilterCriteria(
    val searchQuery: String,
    val searchTags: List<TagEntity>,
    val subjects: Set<String>,
    val levels: Set<String>,
    val languages: Set<String>,
    val mediums: Set<String>,
    val downloadFilterIndex: Int
)

class ResourceLibraryFilter {
    private data class Signature(
        val searchQuery: String,
        val searchTagIds: List<String>,
        val subjects: Set<String>,
        val levels: Set<String>,
        val languages: Set<String>,
        val mediums: Set<String>,
        val downloadFilterIndex: Int
    )

    private var lastSignature: Signature? = null

    fun reset() {
        lastSignature = null
    }

    fun filterIfChanged(models: List<ResourceListModel>, criteria: ResourceFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel>? {
        val signature = criteria.toSignature()
        if (signature == lastSignature) return null
        lastSignature = signature
        return filter(models, criteria, locallyOfflineIds)
    }

    fun apply(models: List<ResourceListModel>, criteria: ResourceFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel> {
        lastSignature = criteria.toSignature()
        return filter(models, criteria, locallyOfflineIds)
    }

    fun countMatching(models: List<ResourceListModel>, criteria: ResourceFilterCriteria, locallyOfflineIds: Set<String>): Int =
        filter(models, criteria, locallyOfflineIds).size

    private fun filter(models: List<ResourceListModel>, criteria: ResourceFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel> {
        val bySearchAndTags = filterBySearchAndTags(models, criteria.searchQuery, criteria.searchTags)
        return filterByFacetsAndDownloadStatus(bySearchAndTags, criteria, locallyOfflineIds)
    }

    private fun filterBySearchAndTags(models: List<ResourceListModel>, searchQuery: String, tags: List<TagEntity>): List<ResourceListModel> {
        var filteredList = ResourcesSearchUtils.searchLocalModels(models, searchQuery)
        if (tags.isNotEmpty()) {
            filteredList = filteredList.filter { model ->
                tags.any { searchTag -> model.tags.any { it.id == searchTag.id } }
            }
        }
        return filteredList
    }

    private fun filterByFacetsAndDownloadStatus(models: List<ResourceListModel>, criteria: ResourceFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel> {
        return models.filter { model ->
            val l = model.library
            val sub = criteria.subjects.isEmpty() || criteria.subjects.let { l.subject?.containsAll(it) } == true
            val lev = criteria.levels.isEmpty() || l.level?.containsAll(criteria.levels) == true
            val lan = criteria.languages.isEmpty() || criteria.languages.contains(l.language)
            val med = criteria.mediums.isEmpty() || criteria.mediums.contains(l.mediaType)

            val isDownloaded = model.item.isOffline || locallyOfflineIds.contains(model.item.id) || model.isLocallyOffline
            val passesDownloadFilter = when (criteria.downloadFilterIndex) {
                1 -> isDownloaded
                2 -> !isDownloaded
                else -> true
            }

            sub && lev && lan && med && passesDownloadFilter
        }
    }

    private fun ResourceFilterCriteria.toSignature() = Signature(
        searchQuery = searchQuery,
        searchTagIds = searchTags.map { it.id }.sorted(),
        subjects = HashSet(subjects),
        levels = HashSet(levels),
        languages = HashSet(languages),
        mediums = HashSet(mediums),
        downloadFilterIndex = downloadFilterIndex
    )
}
