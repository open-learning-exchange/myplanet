package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.MyLibrary
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

    private data class MediumCriteria(
        val canonicalSel: String,
        val targetType: ResourcesMediaType?
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
        val precomputedMediums = if (criteria.mediums.isEmpty()) {
            emptyList()
        } else {
            criteria.mediums.map { selected ->
                val canonicalSel = ResourcesMediaType.canonicalMedium(selected)
                val targetType = when (canonicalSel) {
                    "audio" -> ResourcesMediaType.AUDIO
                    "video" -> ResourcesMediaType.VIDEO
                    "pdf" -> ResourcesMediaType.PDF
                    "book" -> ResourcesMediaType.BOOK
                    else -> null
                }
                MediumCriteria(canonicalSel, targetType)
            }
        }
        return searched.asSequence()
            .filter { model -> searchTagIds == null || model.tags.any { it.id in searchTagIds } }
            .filter { model -> matchesFacets(model, criteria, precomputedMediums) }
            .filter { model -> matchesDownloadFilter(model, criteria.downloadFilterIndex, locallyOfflineIds) }
    }

    private fun matchesFacets(
        model: ResourceListModel,
        criteria: ResourcesFilterCriteria,
        precomputedMediums: List<MediumCriteria>
    ): Boolean {
        val library = model.library

        if (criteria.subjects.isNotEmpty()) {
            val libSubjects = library.subject
            if (libSubjects == null || !criteria.subjects.all { critSub ->
                    libSubjects.any { libSub -> libSub.equals(critSub, ignoreCase = true) }
                }) {
                return false
            }
        }

        if (criteria.levels.isNotEmpty()) {
            val libLevels = library.level
            if (libLevels == null || !criteria.levels.all { critLvl ->
                    libLevels.any { libLvl -> libLvl.equals(critLvl, ignoreCase = true) }
                }) {
                return false
            }
        }

        if (criteria.languages.isNotEmpty()) {
            if (!criteria.languages.any { it.equals(library.language, ignoreCase = true) }) {
                return false
            }
        }

        if (precomputedMediums.isNotEmpty()) {
            if (!matchesMedium(library, precomputedMediums)) {
                return false
            }
        }

        return true
    }

    private fun matchesMedium(library: MyLibrary, precomputedMediums: List<MediumCriteria>): Boolean {
        var classifiedType: ResourcesMediaType? = null
        var isNonBook: Boolean? = null
        var libCanonical: String? = null
        var libCanonicalSet = false

        fun getClassified(): ResourcesMediaType =
            classifiedType ?: ResourcesMediaType.classify(library).also { classifiedType = it }

        fun getIsNonBook(): Boolean =
            isNonBook ?: ResourcesMediaType.isExplicitNonBook(library).also { isNonBook = it }

        fun getLibCanonical(): String? {
            if (!libCanonicalSet) {
                libCanonical = library.mediaType?.let { ResourcesMediaType.canonicalMedium(it) }
                libCanonicalSet = true
            }
            return libCanonical
        }

        return precomputedMediums.any { crit ->
            if (crit.targetType != null) {
                if (crit.targetType == ResourcesMediaType.BOOK) {
                    getClassified() == ResourcesMediaType.BOOK && !getIsNonBook()
                } else {
                    getClassified() == crit.targetType
                }
            } else {
                getLibCanonical()?.equals(crit.canonicalSel, ignoreCase = true) == true
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
