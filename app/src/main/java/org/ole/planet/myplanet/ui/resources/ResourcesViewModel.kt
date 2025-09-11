package org.ole.planet.myplanet.ui.resources

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val syncManager: SyncManager,
    private val prefManager: SharedPrefManager,
    @AppPreferences private val settings: SharedPreferences
) : ViewModel() {

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        object Success : SyncState()
        data class Error(val message: String?) : SyncState()
    }

    private val _resourceList = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val resourceList: StateFlow<List<RealmMyLibrary>> = _resourceList.asStateFlow()

    private val _ratingMap = MutableStateFlow<HashMap<String?, com.google.gson.JsonObject>>(hashMapOf())
    val ratingMap: StateFlow<HashMap<String?, com.google.gson.JsonObject>> = _ratingMap.asStateFlow()

    private val _tags = MutableStateFlow<MutableList<RealmTag>>(mutableListOf())
    val tags: StateFlow<List<RealmTag>> = _tags.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    private var userId: String? = null
    private var isMyLibrary: Boolean = false

    private var subjects: MutableSet<String> = mutableSetOf()
    private var languages: MutableSet<String> = mutableSetOf()
    private var mediums: MutableSet<String> = mutableSetOf()
    private var levels: MutableSet<String> = mutableSetOf()

    fun initialize(userId: String?, isMyLibrary: Boolean) {
        this.userId = userId
        this.isMyLibrary = isMyLibrary
        loadResources()
    }

    fun startResourcesSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isResourcesSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        viewModelScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                _syncState.value = SyncState.Syncing
            }

            override fun onSyncComplete() {
                _syncState.value = SyncState.Success
                prefManager.setResourcesSynced(true)
                loadResources()
            }

            override fun onSyncFailed(msg: String?) {
                _syncState.value = SyncState.Error(msg)
            }
        }, "full", listOf("resources"))
    }

    fun addTag(tag: RealmTag) {
        if (!_tags.value.contains(tag)) {
            val newTags = _tags.value
            newTags.add(tag)
            _tags.value = newTags
            loadResources()
        }
    }

    fun removeTagAt(index: Int) {
        if (index >= 0 && index < _tags.value.size) {
            val newTags = _tags.value
            newTags.removeAt(index)
            _tags.value = newTags
            loadResources()
        }
    }

    fun setTags(tags: List<RealmTag>) {
        _tags.value = tags.toMutableList()
        loadResources()
    }

    fun clearTags() {
        _tags.value.clear()
        loadResources()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        loadResources()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _tags.value.clear()
        subjects.clear()
        languages.clear()
        mediums.clear()
        levels.clear()
        loadResources()
    }

    fun setFilters(
        subjects: MutableSet<String>,
        languages: MutableSet<String>,
        mediums: MutableSet<String>,
        levels: MutableSet<String>
    ) {
        this.subjects = subjects
        this.languages = languages
        this.mediums = mediums
        this.levels = levels
        loadResources()
    }

    fun clearFilters() {
        subjects.clear()
        languages.clear()
        mediums.clear()
        levels.clear()
        loadResources()
    }

    fun refreshResources() {
        loadResources()
    }

    fun refreshRatings() {
        val uId = userId
        if (uId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                val ratings = getRatings(realm, "resource", uId)
                _ratingMap.value = ratings
                _resourceList.value = _resourceList.value.toList()
            }
        }
    }

    private fun loadResources() {
        val uId = userId
        if (uId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                val ratings = getRatings(realm, "resource", uId)
                var list = realm.queryList(RealmMyLibrary::class.java)
                list = if (isMyLibrary) {
                    RealmMyLibrary.getMyLibraryByUserId(uId, list)
                } else {
                    RealmMyLibrary.getOurLibrary(uId, list.filter { !it.isPrivate })
                }
                list = filterLibraryByTag(realm, list, _searchQuery.value, _tags.value)
                list = applyFilter(list)
                _ratingMap.value = ratings
                _resourceList.value = list
            }
        }
    }

    private fun filterLibraryByTag(
        realm: io.realm.Realm,
        list: List<RealmMyLibrary>,
        s: String,
        tags: List<RealmTag>
    ): List<RealmMyLibrary> {
        val normalizedSearchTerm = normalizeText(s)
        var filtered = if (s.isEmpty()) {
            list
        } else {
            list.filter { normalizeText(it.title ?: "").contains(normalizedSearchTerm) }
        }
        if (tags.isNotEmpty()) {
            val libraries = mutableListOf<RealmMyLibrary>()
            for (library in filtered) {
                for (tg in tags) {
                    val count = realm.where(RealmTag::class.java)
                        .equalTo("db", "resources")
                        .equalTo("tagId", tg.id)
                        .equalTo("linkId", library.id)
                        .count()
                    if (count > 0 && !libraries.contains(library)) {
                        libraries.add(library)
                    }
                }
            }
            filtered = libraries
        }
        return filtered
    }

    private fun normalizeText(str: String): String {
        return java.text.Normalizer.normalize(str.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    private fun applyFilter(libraries: List<RealmMyLibrary>): List<RealmMyLibrary> {
        val newList = mutableListOf<RealmMyLibrary>()
        for (l in libraries) {
            if (isValidFilter(l)) newList.add(l)
        }
        return newList
    }

    private fun isValidFilter(l: RealmMyLibrary): Boolean {
        val sub = subjects.isEmpty() || l.subject?.let { it.containsAll(subjects) } == true
        val lev = levels.isEmpty() || l.level?.let { it.containsAll(levels) } == true
        val lan = languages.isEmpty() || languages.contains(l.language)
        val med = mediums.isEmpty() || mediums.contains(l.mediaType)
        return sub && lev && lan && med
    }
}

