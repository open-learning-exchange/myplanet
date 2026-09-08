package org.ole.planet.myplanet.ui.surveys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.SurveyFormState
import org.ole.planet.myplanet.model.SurveyInfo
import org.ole.planet.myplanet.model.SurveyRow
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.Utilities

@HiltViewModel
class SurveysViewModel @Inject constructor(
    private val surveysRepository: SurveysRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    enum class SortOption {
        DATE_ASC, DATE_DESC, TITLE_ASC, TITLE_DESC
    }

    private var rawSurveys: List<StepExam> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentSortOption: SortOption = SortOption.DATE_DESC
    private var isTeam: Boolean = false
    private var teamId: String? = null
    private var filterSortJob: Job? = null

    private val _surveys = MutableStateFlow<List<StepExam>>(emptyList())

    private val _surveyInfos = MutableStateFlow<Map<String, SurveyInfo>>(emptyMap())
    val surveyInfos: StateFlow<Map<String, SurveyInfo>> = _surveyInfos.asStateFlow()

    private val _bindingData = MutableStateFlow<Map<String, SurveyFormState>>(emptyMap())
    val bindingData: StateFlow<Map<String, SurveyFormState>> = _bindingData.asStateFlow()

    val surveys: StateFlow<List<SurveyRow>> = combine(_surveys, _surveyInfos, _bindingData) { surveys, infos, bindingData ->
        surveys.map { exam ->
            SurveyRow(exam, infos[exam.id], bindingData[exam.id])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isTeamShareAllowed = MutableStateFlow(false)
    val isTeamShareAllowed: StateFlow<Boolean> = _isTeamShareAllowed.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users.asStateFlow()

    private val _surveySent = MutableStateFlow(false)
    val surveySent: StateFlow<Boolean> = _surveySent.asStateFlow()

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

                val userModel = userRepository.getUserModel()
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

    fun toggleTitleSort() {
        currentSortOption = if (currentSortOption == SortOption.TITLE_ASC) {
            SortOption.TITLE_DESC
        } else {
            SortOption.TITLE_ASC
        }
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        filterSortJob?.cancel()
        filterSortJob = viewModelScope.launch {
            val currentRawSurveys = rawSurveys
            val list = withContext(dispatcherProvider.default) {
                var filteredList = if (currentSearchQuery.isNotEmpty()) {
                    filter(currentSearchQuery, currentRawSurveys)
                } else {
                    currentRawSurveys
                }

                when (currentSortOption) {
                    SortOption.DATE_DESC -> filteredList.sortedByDescending { getSortDate(it) }
                    SortOption.DATE_ASC -> filteredList.sortedBy { getSortDate(it) }
                    SortOption.TITLE_ASC -> filteredList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
                    SortOption.TITLE_DESC -> filteredList.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
                }
            }
            if (rawSurveys === currentRawSurveys) {
                _surveys.value = list
            }
        }
    }

    private fun getSortDate(survey: StepExam): Long {
        return if (survey.sourceSurveyId != null) {
            if (survey.adoptionDate > 0) survey.adoptionDate else survey.createdDate
        } else {
            survey.createdDate
        }
    }

    private fun filter(s: String, list: List<StepExam>): List<StepExam> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { Utilities.normalizeText(it) }
        val normalizedQuery = Utilities.normalizeText(s)
        val startsWithQuery = mutableListOf<StepExam>()
        val containsQuery = mutableListOf<StepExam>()

        for (item in list) {
            val title = item.name?.let { Utilities.normalizeText(it) } ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }


    fun adoptSurvey(surveyId: String) {
        viewModelScope.launch {
            try {
                val userModel = userRepository.getUserModel()
                surveysRepository.adoptSurvey(surveyId, userModel?.id, teamId, isTeam)
                _userMessage.value = "Survey adopted successfully"
                _isTeamShareAllowed.value = false
                loadSurveys(isTeam, teamId, false)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to adopt survey"
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            _users.value = userRepository.getAllUsers()
        }
    }

    fun sendSurveyToUsers(surveyId: String, selectedUserIds: List<String>) {
        viewModelScope.launch {
            submissionsRepository.createBulkSurveySubmissions(surveyId, selectedUserIds)
            _surveySent.value = true
        }
    }
}
