package org.ole.planet.myplanet.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import java.text.Normalizer
import java.util.Locale

@HiltViewModel
class ResourcesViewModel @Inject constructor() : ViewModel() {

    private val _allLibraryItems = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val allLibraryItems: StateFlow<List<RealmMyLibrary>> = _allLibraryItems.asStateFlow()

    private val _filteredLibraryItems = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val filteredLibraryItems: StateFlow<List<RealmMyLibrary>> = _filteredLibraryItems.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _searchTags = MutableStateFlow<List<RealmTag>>(emptyList())

    private val _subjects = MutableStateFlow<Set<String>>(emptySet())
    private val _languages = MutableStateFlow<Set<String>>(emptySet())
    private val _mediums = MutableStateFlow<Set<String>>(emptySet())
    private val _levels = MutableStateFlow<Set<String>>(emptySet())

    private val _tagsMap = MutableStateFlow<Map<String, List<RealmTag>>>(emptyMap())

    init {
        combine(
            _allLibraryItems,
            _searchQuery,
            _searchTags,
            _subjects,
            _languages,
            _mediums,
            _levels,
            _tagsMap
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val items = args[0] as List<RealmMyLibrary>
            val query = args[1] as String
            @Suppress("UNCHECKED_CAST")
            val tags = args[2] as List<RealmTag>
            @Suppress("UNCHECKED_CAST")
            val subjects = args[3] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val languages = args[4] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val mediums = args[5] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val levels = args[6] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val tagsMap = args[7] as Map<String, List<RealmTag>>

            val filteredByTagAndSearch = filterLocalLibraryByTag(items, query, tags, tagsMap)
            applyFilter(filteredByTagAndSearch, subjects, languages, mediums, levels)
        }.onEach { result ->
            _filteredLibraryItems.value = result
        }.launchIn(viewModelScope)
    }

    fun setAllLibraryItems(items: List<RealmMyLibrary>, tagsMap: Map<String, List<RealmTag>>) {
        _allLibraryItems.value = items
        _tagsMap.value = tagsMap
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchTags(tags: List<RealmTag>) {
        _searchTags.value = tags
    }

    fun setFilters(
        subjects: Set<String>,
        languages: Set<String>,
        mediums: Set<String>,
        levels: Set<String>
    ) {
        _subjects.value = subjects
        _languages.value = languages
        _mediums.value = mediums
        _levels.value = levels
    }

    fun getSubjects(): Set<String> = _subjects.value
    fun getLanguages(): Set<String> = _languages.value
    fun getMediums(): Set<String> = _mediums.value
    fun getLevels(): Set<String> = _levels.value
    fun getSearchTags(): List<RealmTag> = _searchTags.value
    fun getSearchQuery(): String = _searchQuery.value

    private fun applyFilter(
        libraries: List<RealmMyLibrary>,
        subjects: Set<String>,
        languages: Set<String>,
        mediums: Set<String>,
        levels: Set<String>
    ): List<RealmMyLibrary> {
        val newList: MutableList<RealmMyLibrary> = ArrayList()
        for (l in libraries) {
            if (isValidFilter(l, subjects, languages, mediums, levels)) newList.add(l)
        }
        return newList
    }

    private fun isValidFilter(
        l: RealmMyLibrary,
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
        allLibraryItems: List<RealmMyLibrary>,
        s: String,
        tags: List<RealmTag>,
        tagsMap: Map<String, List<RealmTag>>
    ): List<RealmMyLibrary> {
        val normalizedSearchTerm = normalizeText(s)

        var filteredList = if (s.isEmpty()) {
            allLibraryItems
        } else {
            val queryParts = s.split(" ").filterNot { it.isEmpty() }
            val normalizedQueryParts = queryParts.map { normalizeText(it) }
            val startsWithQuery = mutableListOf<RealmMyLibrary>()
            val containsQuery = mutableListOf<RealmMyLibrary>()

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
