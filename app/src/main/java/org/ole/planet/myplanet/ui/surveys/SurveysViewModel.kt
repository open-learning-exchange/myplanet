package org.ole.planet.myplanet.ui.surveys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.SurveyFormState
import org.ole.planet.myplanet.model.SurveyInfo
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider

private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

@HiltViewModel
class SurveysViewModel @Inject constructor(
    private val surveysRepository: SurveysRepository,
    private val syncManager: SyncManager,
    private val userSessionManager: UserSessionManager,
    private val sharedPrefManager: SharedPrefManager,
    private val serverUrlMapper: ServerUrlMapper,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    enum class SortOption {
        DATE_ASC, DATE_DESC, TITLE_ASC, TITLE_DESC
    }

    private var rawSurveys: List<RealmStepExam> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentSortOption: SortOption = SortOption.DATE_DESC
    private var isTeam: Boolean = false
    private var teamId: String? = null

    private val _surveys = MutableStateFlow<List<RealmStepExam>>(emptyList())
    val surveys: StateFlow<List<RealmStepExam>> = _surveys.asStateFlow()

    private val _surveyInfos = MutableStateFlow<Map<String, SurveyInfo>>(emptyMap())
    val surveyInfos: StateFlow<Map<String, SurveyInfo>> = _surveyInfos.asStateFlow()

    private val _bindingData = MutableStateFlow<Map<String, SurveyFormState>>(emptyMap())
    val bindingData: StateFlow<Map<String, SurveyFormState>> = _bindingData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isTeamShareAllowed = MutableStateFlow(false)
    val isTeamShareAllowed: StateFlow<Boolean> = _isTeamShareAllowed.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun loadSurveys(isTeam: Boolean, teamId: String?, isTeamShareAllowed: Boolean) {
        this.isTeam = isTeam
        this.teamId = teamId
        _isLoading.value = true
        _isTeamShareAllowed.value = isTeamShareAllowed
        viewModelScope.launch {
            try {
                val currentSurveysList = withContext(dispatcherProvider.io) {
                    when {
                        isTeam && isTeamShareAllowed -> surveysRepository.getAdoptableTeamSurveys(teamId)
                        isTeam -> surveysRepository.getTeamOwnedSurveys(teamId)
                        else -> surveysRepository.getIndividualSurveys()
                    }
                }

                val userModel = withContext(dispatcherProvider.io) { userSessionManager.getUserModel() }
                val surveyInfos = withContext(dispatcherProvider.io) {
                    surveysRepository.getSurveyInfos(
                        isTeam,
                        teamId,
                        userModel?.id,
                        currentSurveysList
                    )
                }
                val bindingData = withContext(dispatcherProvider.io) { surveysRepository.getSurveyFormState(currentSurveysList, teamId) }

                _surveyInfos.value = surveyInfos
                _bindingData.value = bindingData

                rawSurveys = currentSurveysList
                applyFilterAndSort()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load surveys: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        currentSearchQuery = query
        applyFilterAndSort()
    }

    fun sort(sortOption: SortOption) {
        currentSortOption = sortOption
        applyFilterAndSort()
    }

    fun toggleTitleSort() {
        currentSortOption = if (currentSortOption == SortOption.TITLE_ASC) {
            SortOption.TITLE_DESC
        } else {
            SortOption.TITLE_ASC
        }
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        var list = if (currentSearchQuery.isNotEmpty()) {
            filter(currentSearchQuery, rawSurveys)
        } else {
            rawSurveys
        }

        list = when (currentSortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { getSortDate(it) }
            SortOption.DATE_ASC -> list.sortedBy { getSortDate(it) }
            SortOption.TITLE_ASC -> list.sortedBy { it.name?.lowercase(Locale.getDefault()) }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.name?.lowercase(Locale.getDefault()) }
        }

        _surveys.value = list
    }

    private fun getSortDate(survey: RealmStepExam): Long {
        return if (survey.sourceSurveyId != null) {
            if (survey.adoptionDate > 0) survey.adoptionDate else survey.createdDate
        } else {
            survey.createdDate
        }
    }

    private fun filter(s: String, list: List<RealmStepExam>): List<RealmStepExam> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { normalizeText(it) }
        val normalizedQuery = normalizeText(s)
        val startsWithQuery = mutableListOf<RealmStepExam>()
        val containsQuery = mutableListOf<RealmStepExam>()

        for (item in list) {
            val title = item.name?.let { normalizeText(it) } ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
    }

    fun startExamSync() {
        val isFastSync = sharedPrefManager.getFastSync()
        val isExamsSynced = sharedPrefManager.isExamsSynced()

        if (isFastSync && !isExamsSynced) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val serverUrl = sharedPrefManager.getServerUrl()
        val mapping = serverUrlMapper.processUrl(serverUrl)

        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                    MainApplication.isServerReachable(url)
                }
            }
            startSyncManager()
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : OnSyncListener {
            override fun onSyncStarted() {
                _isLoading.value = true
            }

            override fun onSyncComplete() {
                sharedPrefManager.setExamsSynced(true)
                _isLoading.value = false
                loadSurveys(isTeam, teamId, _isTeamShareAllowed.value)
            }

            override fun onSyncFailed(msg: String?) {
                _isLoading.value = false
                _errorMessage.value = "Sync failed: $msg"
            }
        }, "full", listOf("exams"))
    }

    fun adoptSurvey(surveyId: String) {
        viewModelScope.launch {
            try {
                withContext(dispatcherProvider.io) {
                    val userModel = userSessionManager.getUserModel()
                    surveysRepository.adoptSurvey(surveyId, userModel?.id, teamId, isTeam)
                }
                _userMessage.value = "Survey adopted successfully"
                _isTeamShareAllowed.value = false
                loadSurveys(isTeam, teamId, false)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to adopt survey"
            }
        }
    }
}
