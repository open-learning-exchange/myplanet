package org.ole.planet.myplanet.ui.surveys

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.di.DefaultPreferences
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.SurveyInfo
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.services.UserSessionManager
import java.text.Normalizer
import java.util.Locale
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@HiltViewModel
class SurveysViewModel @Inject constructor(
    private val surveysRepository: SurveysRepository,
    private val syncManager: SyncManager,
    private val userSessionManager: UserSessionManager,
    @DefaultPreferences private val settings: SharedPreferences,
    private val serverUrlMapper: ServerUrlMapper
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
                val currentSurveysList = when {
                    isTeam && isTeamShareAllowed -> surveysRepository.getAdoptableTeamSurveys(teamId)
                    isTeam -> surveysRepository.getTeamOwnedSurveys(teamId)
                    else -> surveysRepository.getIndividualSurveys()
                }

                val userModel = userSessionManager.userModel
                val surveyInfos = surveysRepository.getSurveyInfos(
                    isTeam,
                    teamId,
                    userModel?.id,
                    currentSurveysList
                )
                val bindingData = surveysRepository.getSurveyFormState(currentSurveysList, teamId)

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
        val normalizedQuery = normalizeText(s)
        val startsWithQuery = mutableListOf<RealmStepExam>()
        val containsQuery = mutableListOf<RealmStepExam>()

        for (item in list) {
            val title = item.name?.let { normalizeText(it) } ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (queryParts.all { title.contains(normalizeText(it), ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun startExamSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        val isExamsSynced = settings.getBoolean("isExamsSynced", false)

        if (isFastSync && !isExamsSynced) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val serverUrl = settings.getString("serverURL", "") ?: ""
        val mapping = serverUrlMapper.processUrl(serverUrl)

        viewModelScope.launch {
            serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
                MainApplication.isServerReachable(url)
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
                settings.edit().putBoolean("isExamsSynced", true).apply()
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
                val userModel = userSessionManager.userModel
                surveysRepository.adoptSurvey(surveyId, userModel?.id, teamId, isTeam)
                _userMessage.value = "Survey adopted successfully"
                _isTeamShareAllowed.value = false
                loadSurveys(isTeam, teamId, false)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to adopt survey"
            }
        }
    }
}
