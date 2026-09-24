package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LibraryType
import org.ole.planet.myplanet.utils.LibraryTypeClassifier
import org.ole.planet.myplanet.utils.MediumUtils
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
        filter(models, criteria, locallyOfflineIds).size

    private fun filter(models: List<ResourceListModel>, criteria: ResourcesFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel> {
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

    private fun filterByFacetsAndDownloadStatus(models: List<ResourceListModel>, criteria: ResourcesFilterCriteria, locallyOfflineIds: Set<String>): List<ResourceListModel> {
        return models.filter { model ->
            matchesFacets(model, criteria) && matchesDownloadFilter(model, criteria.downloadFilterIndex, locallyOfflineIds)
        }
    }

    private fun matchesFacets(model: ResourceListModel, criteria: ResourcesFilterCriteria): Boolean {
        val library = model.library
        val libSubjects = library.subject
        val libLevels = library.level
        val subject = criteria.subjects.isEmpty() || (libSubjects != null && criteria.subjects.all { critSub ->
            libSubjects.any { libSub -> libSub.equals(critSub, ignoreCase = true) }
        })
        val level = criteria.levels.isEmpty() || (libLevels != null && criteria.levels.all { critLvl ->
            libLevels.any { libLvl -> libLvl.equals(critLvl, ignoreCase = true) }
        })
        val language = criteria.languages.isEmpty() || criteria.languages.any { it.equals(library.language, ignoreCase = true) }
        val medium = criteria.mediums.isEmpty() || matchesMedium(library, criteria.mediums)
        return subject && level && language && medium
    }

    private fun matchesMedium(library: MyLibrary, selectedMediums: Set<String>): Boolean {
        val classifiedType = LibraryTypeClassifier.classify(library)
        val isNonBook = LibraryTypeClassifier.isExplicitNonBook(library)

        return selectedMediums.any { selected ->
            val canonicalSel = MediumUtils.getCanonicalMedium(selected)
            val targetType = when (canonicalSel) {
                "audio" -> LibraryType.AUDIO
                "video" -> LibraryType.VIDEO
                "pdf" -> LibraryType.PDF
                "book" -> LibraryType.BOOK
                else -> null
            }

            if (targetType != null) {
                if (targetType == LibraryType.BOOK) {
                    classifiedType == LibraryType.BOOK && !isNonBook
                } else {
                    classifiedType == targetType
                }
            } else {
                val libCanonical = library.mediaType?.let { MediumUtils.getCanonicalMedium(it) }
                libCanonical == canonicalSel
            }
        }
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
