package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.TagItem
import java.text.Normalizer
import java.util.Locale

data class FilterState(
    val allLibraryItems: List<ResourceItem> = emptyList(),
    val searchQuery: String = "",
    val searchTags: List<TagItem> = emptyList(),
    val subjects: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val mediums: Set<String> = emptySet(),
    val levels: Set<String> = emptySet(),
    val tagsMap: Map<String, List<TagItem>> = emptyMap()
)

@HiltViewModel
class ResourcesViewModel @Inject constructor() : ViewModel() {

    private val _filterState = MutableStateFlow(FilterState())

    private val _filteredLibraryItems = MutableStateFlow<List<ResourceItem>>(emptyList())
    val filteredLibraryItems: StateFlow<List<ResourceItem>> = _filteredLibraryItems.asStateFlow()

    init {
        _filterState.map { state ->
            val filteredByTagAndSearch = filterLocalLibraryByTag(
                state.allLibraryItems,
                state.searchQuery,
                state.searchTags,
                state.tagsMap
            )
            applyFilter(
                filteredByTagAndSearch,
                state.subjects,
                state.languages,
                state.mediums,
                state.levels
            )
        }.onEach { result ->
            _filteredLibraryItems.value = result
        }.launchIn(viewModelScope)
    }

    fun setAllLibraryItems(items: List<ResourceItem>, tagsMap: Map<String, List<TagItem>>) {
        _filterState.value = _filterState.value.copy(
            allLibraryItems = items,
            tagsMap = tagsMap
        )
    }

    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun setSearchTags(tags: List<TagItem>) {
        _filterState.value = _filterState.value.copy(searchTags = tags)
    }

    fun setFilters(
        subjects: Set<String>,
        languages: Set<String>,
        mediums: Set<String>,
        levels: Set<String>
    ) {
        _filterState.value = _filterState.value.copy(
            subjects = subjects,
            languages = languages,
            mediums = mediums,
            levels = levels
        )
    }

    fun getSubjects(): Set<String> = _filterState.value.subjects
    fun getLanguages(): Set<String> = _filterState.value.languages
    fun getMediums(): Set<String> = _filterState.value.mediums
    fun getLevels(): Set<String> = _filterState.value.levels
    fun getSearchTags(): List<TagItem> = _filterState.value.searchTags
    fun getSearchQuery(): String = _filterState.value.searchQuery

    private fun applyFilter(
        libraries: List<ResourceItem>,
        subjects: Set<String>,
        languages: Set<String>,
        mediums: Set<String>,
        levels: Set<String>
    ): List<ResourceItem> {
        val newList: MutableList<ResourceItem> = ArrayList()
        for (l in libraries) {
            if (isValidFilter(l, subjects, languages, mediums, levels)) newList.add(l)
        }
        return newList
    }

    private fun isValidFilter(
        l: ResourceItem,
        subjects: Set<String>,
        languages: Set<String>,
        mediums: Set<String>,
        levels: Set<String>
    ): Boolean {
        val sub = subjects.isEmpty() || subjects.let { l.subject?.containsAll(it) } == true
        val lev = levels.isEmpty() || l.level?.containsAll(levels) == true
        val lan = languages.isEmpty() || languages.contains(l.language)
        val med = mediums.isEmpty() || mediums.contains(l.mediaType)
        return sub && lev && lan && med
    }

    private fun filterLocalLibraryByTag(
        allLibraryItems: List<ResourceItem>,
        s: String,
        tags: List<TagItem>,
        tagsMap: Map<String, List<TagItem>>
    ): List<ResourceItem> {
        val normalizedSearchTerm = normalizeText(s)

        var filteredList = if (s.isEmpty()) {
            allLibraryItems
        } else {
            val queryParts = s.split(" ").filterNot { it.isEmpty() }
            val normalizedQueryParts = queryParts.map { normalizeText(it) }
            val startsWithQuery = mutableListOf<ResourceItem>()
            val containsQuery = mutableListOf<ResourceItem>()

            for (item in allLibraryItems) {
                val title = item.title?.let { normalizeText(it) } ?: continue
                if (title.startsWith(normalizedSearchTerm, ignoreCase = true)) {
                    startsWithQuery.add(item)
                } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                    containsQuery.add(item)
                }
            }
            startsWithQuery + containsQuery
        }

        if (tags.isNotEmpty()) {
            filteredList = filteredList.filter { library ->
                val libraryTags = library.id?.let { tagsMap[it] } ?: emptyList()
                tags.any { searchTag -> libraryTags.any { it.id == searchTag.id } }
            }
        }
        return filteredList
    }

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
