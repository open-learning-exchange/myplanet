package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.utils.ResourcesSearchUtils

data class ResourcesFilterCriteria(
    val searchQuery: String,
    val searchTags: List<TagEntity>,
    val subjects: Set<String>,
    val levels: Set<String>,
    val languages: Set<String>,
    val mediums: Set<String>,
    val downloadFilterIndex: Int
)

class ResourcesListFilter {
    private data class Signature(
        val searchQuery: String,
        val searchTagIds: List<String>,
        val subjects: Set<String>,
        val levels: Set<String>,
        val languages: Set<String>,
        val mediums: Set<String>,
        val downloadFilterIndex: Int,
        val locallyOfflineIds: Set<String>
    )

    private var lastSignature: Signature? = null

    fun reset() {
        lastSignature = null
    }

    fun filterIfChanged(models: List<ResourceListModel>, criteria: ResourcesFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel>? {
        val signature = criteria.toSignature(locallyOfflineIds)
        if (signature == lastSignature) return null
        lastSignature = signature
        return filter(models, criteria, locallyOfflineIds)
    }

    fun apply(models: List<ResourceListModel>, criteria: ResourcesFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel> {
        lastSignature = criteria.toSignature(locallyOfflineIds)
        return filter(models, criteria, locallyOfflineIds)
    }

    fun countMatching(models: List<ResourceListModel>, criteria: ResourcesFilterCriteria, locallyOfflineIds: Set<String>): Int =
        matching(models, criteria, locallyOfflineIds).count()

    private fun filter(models: List<ResourceListModel>, criteria: ResourcesFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel> =
        matching(models, criteria, locallyOfflineIds).toList()

    private fun matching(
        models: List<ResourceListModel>,
        criteria: ResourcesFilterCriteria,
        locallyOfflineIds: Set<String>
    ): Sequence<ResourceListModel> {
        val searched = ResourcesSearchUtils.searchLocalModels(models, criteria.searchQuery)
        val searchTagIds = if (criteria.searchTags.isNotEmpty()) {
            criteria.searchTags.mapTo(HashSet()) { it.id }
        } else {
            null
        }
        return searched.asSequence()
            .filter { model -> searchTagIds == null || model.tags.any { it.id in searchTagIds } }
            .filter { model -> matchesFacets(model, criteria) }
            .filter { model -> matchesDownloadFilter(model, criteria.downloadFilterIndex, locallyOfflineIds) }
    }

    private fun matchesFacets(model: ResourceListModel, criteria: ResourcesFilterCriteria): Boolean {
        val library = model.library
        val subject = criteria.subjects.isEmpty() || library.subject?.containsAll(criteria.subjects) == true
        val level = criteria.levels.isEmpty() || library.level?.containsAll(criteria.levels) == true
        val language = criteria.languages.isEmpty() || criteria.languages.contains(library.language)
        val medium = criteria.mediums.isEmpty() || criteria.mediums.contains(library.mediaType)
        return subject && level && language && medium
    }

    private fun matchesDownloadFilter(model: ResourceListModel, downloadFilterIndex: Int, locallyOfflineIds: Set<String>): Boolean {
        return when (downloadFilterIndex) {
            1 -> isDownloaded(model, locallyOfflineIds)
            2 -> !isDownloaded(model, locallyOfflineIds)
            else -> true
        }
    }

    private fun isDownloaded(model: ResourceListModel, locallyOfflineIds: Set<String>): Boolean =
        model.item.isOffline || locallyOfflineIds.contains(model.item.id) || model.isLocallyOffline

    private fun ResourcesFilterCriteria.toSignature(locallyOfflineIds: Set<String>) = Signature(
        searchQuery = searchQuery,
        searchTagIds = searchTags.map { it.id }.sorted(),
        subjects = HashSet(subjects),
        levels = HashSet(levels),
        languages = HashSet(languages),
        mediums = HashSet(mediums),
        downloadFilterIndex = downloadFilterIndex,
        locallyOfflineIds = HashSet(locallyOfflineIds)
    )
}
