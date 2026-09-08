package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.VoicesLabelManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

@HiltViewModel
class VoicesViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val userRepository: UserRepository,
    private val resourcesRepository: ResourcesRepository
) : ViewModel(), LabelManipulator by DefaultLabelManipulator(voicesRepository, dispatcherProvider) {

    private val _searchQuery = MutableStateFlow("")

    private val _selectedLabel = MutableStateFlow("All")
    val selectedLabel: StateFlow<String> = _selectedLabel.asStateFlow()

    private val _baseNewsList = MutableStateFlow<List<News?>>(emptyList())

    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels: StateFlow<List<String>> = _labels.asStateFlow()

    private val _createNewsSuccess = Channel<News?>(Channel.BUFFERED)
    val createNewsSuccess: Flow<News?> = _createNewsSuccess.receiveAsFlow()

    private var observeJob: Job? = null

    private val labelDisplayToValue: Map<String, String> = Constants.LABELS

    val filteredNews: StateFlow<List<News?>> = combine(
        _baseNewsList,
        _searchQuery,
        _selectedLabel
    ) { news, query, label ->
        filterNews(news, query, label)
    }
    .flowOn(dispatcherProvider.default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeCommunityNews(userIdentifier: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            voicesRepository.getCommunityNews(userIdentifier).collect { newsList ->
                val filtered: List<News?> = newsList
                _baseNewsList.value = filtered
                _labels.value = collectLabels(filtered)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedLabel(label: String) {
        _selectedLabel.value = label
    }

    fun createNews(map: HashMap<String?, String>, user: UserEntity, imageList: List<String>) {
        viewModelScope.launch {
            try {
                val news = voicesRepository.createNews(map, user, imageList)
                _createNewsSuccess.send(news)
            } catch (e: Exception) {
                _createNewsSuccess.send(null)
            }
        }
    }

    private fun filterNews(
        list: List<News?>,
        query: String,
        selectedLabel: String
    ): List<News?> {
        val labelFiltered = if (selectedLabel == "All") {
            list
        } else {
            val dynamicLabelDisplayToValue = mutableMapOf<String, String>()
            list.forEach { news ->
                news?.labels?.forEach { label ->
                    if (!labelDisplayToValue.containsValue(label)) {
                        val labelName = Constants.LABEL_VALUE_TO_NAME[label]
                            ?: VoicesLabelManager.formatLabelValue(label)
                        dynamicLabelDisplayToValue.putIfAbsent(labelName, label)
                    }
                }
            }

            val resolvedLabelValue = labelDisplayToValue[selectedLabel]
                ?: dynamicLabelDisplayToValue[selectedLabel]

            list.filter { news ->
                when {
                    selectedLabel == "Shared Chat" -> {
                        news?.chat == true || news?.viewableBy.equals("community", ignoreCase = true)
                    }
                    resolvedLabelValue != null -> {
                        news?.labels?.contains(resolvedLabelValue) == true
                    }
                    else -> {
                        JsonUtils.extractSharedTeamName(news) == selectedLabel
                    }
                }
            }
        }

        if (query.isEmpty()) return labelFiltered

        val lowerQuery = query.trim().lowercase()
        return labelFiltered.filter { news ->
            news?.message?.contains(lowerQuery, ignoreCase = true) == true ||
            news?.userName?.contains(lowerQuery, ignoreCase = true) == true ||
            news?.newsTitle?.contains(lowerQuery, ignoreCase = true) == true
        }
    }

    fun deletePost(newsId: String, teamName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            voicesRepository.deletePost(newsId, teamName)
            onComplete()
        }
    }

    fun shareNewsToCommunity(
        newsId: String,
        userId: String,
        planetCode: String,
        parentCode: String,
        teamName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
            onResult(result)
        }
    }

    // Note: The following are read-only suspend functions designed to be called directly from
    // the UI's lifecycleScope, avoiding intermediate MutableStateFlow caching for point-in-time reads.
    suspend fun getUserById(userId: String): UserEntity? {
        return userRepository.getUserById(userId)
    }

    suspend fun getReplyCount(newsId: String): Int {
        return try {
            voicesRepository.getReplyCount(newsId)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getLibraryResource(resourceId: String): MyLibrary? {
        return resourcesRepository.getLibraryItemByResourceId(resourceId)
    }

    suspend fun isTeamLeader(teamId: String?, userId: String?): Boolean {
        return try {
            if (teamId != null) teamsRepository.isTeamLeader(teamId, userId) else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun collectLabels(newsList: List<News?>): List<String> = withContext(dispatcherProvider.default) {
        val allLabels = mutableSetOf<String>()
        allLabels.add("All")

        Constants.LABELS.forEach { (labelName, _) ->
            allLabels.add(labelName)
        }

        allLabels.add("Shared Chat")

        newsList.forEach { news ->
            val sharedTeamName = JsonUtils.extractSharedTeamName(news)
            if (sharedTeamName.isNotEmpty()) {
                allLabels.add(sharedTeamName)
            }

            news?.labels?.forEach { label ->
                val labelName = Constants.LABEL_VALUE_TO_NAME[label]
                    ?: VoicesLabelManager.formatLabelValue(label)
                allLabels.add(labelName)
            }
        }

        allLabels.sorted()
    }

    fun downloadReferencedResources(list: List<News?>) {
        val resourceIds = mutableSetOf<String>()
        list.forEach { news ->
            val images = news?.imagesArray
            if (images?.isEmpty() == false) {
                val ob = images[0]?.asJsonObject
                val resourceId = JsonUtils.getString("resourceId", ob?.asJsonObject)
                if (!resourceId.isNullOrBlank()) {
                    resourceIds.add(resourceId)
                }
            }
        }
        viewModelScope.launch {
            if (resourceIds.isNotEmpty()) {
                val libraries = resourcesRepository.getLibraryItemsByIds(resourceIds)
                resourcesRepository.downloadResources(libraries)
            }
        }
    }

}
