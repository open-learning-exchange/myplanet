package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.di.LibraryRepository
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import java.text.Normalizer
import java.util.Locale

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _allResources = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val allResources: StateFlow<List<RealmMyLibrary>> = _allResources.asStateFlow()

    private val _filteredResources = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val filteredResources: StateFlow<List<RealmMyLibrary>> = _filteredResources.asStateFlow()

    private var searchQuery: String = ""
    private var tags: MutableList<RealmTag> = mutableListOf()

    var subjects: MutableSet<String> = mutableSetOf()
    var languages: MutableSet<String> = mutableSetOf()
    var mediums: MutableSet<String> = mutableSetOf()
    var levels: MutableSet<String> = mutableSetOf()

    init {
        loadResources()
    }

    fun loadResources() {
        _allResources.value = libraryRepository.getAllLibraryItems()
        applyFilters()
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        applyFilters()
    }

    fun updateTags(newTags: List<RealmTag>) {
        tags = newTags.toMutableList()
        applyFilters()
    }

    fun updateFilters(
        subjects: MutableSet<String>,
        languages: MutableSet<String>,
        mediums: MutableSet<String>,
        levels: MutableSet<String>
    ) {
        this.subjects = subjects
        this.languages = languages
        this.mediums = mediums
        this.levels = levels
        applyFilters()
    }

    private fun applyFilters() {
        var list = _allResources.value
        if (searchQuery.isNotEmpty()) {
            list = filterByQuery(list, searchQuery)
        }
        if (tags.isNotEmpty()) {
            list = filterByTags(list, tags)
        }
        list = list.filter { isValidFilter(it) }
        _filteredResources.value = list
    }

    private fun filterByQuery(list: List<RealmMyLibrary>, query: String): List<RealmMyLibrary> {
        val normalizedQuery = normalizeText(query)
        val queryParts = query.split(" ").filter { it.isNotEmpty() }.map { normalizeText(it) }
        val startsWith = mutableListOf<RealmMyLibrary>()
        val contains = mutableListOf<RealmMyLibrary>()
        list.forEach { item ->
            val title = normalizeText(item.title ?: "")
            if (title.startsWith(normalizedQuery)) {
                startsWith.add(item)
            } else if (queryParts.all { title.contains(it) }) {
                contains.add(item)
            }
        }
        return startsWith + contains
    }

    private fun filterByTags(list: List<RealmMyLibrary>, tags: List<RealmTag>): List<RealmMyLibrary> {
        val tagIds = tags.mapNotNull { it.id }
        return list.filter { lib ->
            lib.tag?.any { tagIds.contains(it) } == true
        }
    }

    private fun isValidFilter(l: RealmMyLibrary): Boolean {
        val sub = subjects.isEmpty() || subjects.let { l.subject?.containsAll(it) } == true
        val lev = levels.isEmpty() || l.level?.containsAll(levels) == true
        val lan = languages.isEmpty() || languages.contains(l.language)
        val med = mediums.isEmpty() || mediums.contains(l.mediaType)
        return sub && lev && lan && med
    }

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}

