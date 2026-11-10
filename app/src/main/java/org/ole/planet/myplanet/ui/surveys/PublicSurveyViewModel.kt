package org.ole.planet.myplanet.ui.surveys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysPublicMapper
import org.ole.planet.myplanet.repository.SurveysRepository

sealed interface PublicSurveyEvent {
    data class SurveyLoaded(val surveyId: String, val teamId: String) : PublicSurveyEvent
    object SurveyLoadFailed : PublicSurveyEvent
    data class UploadFinished(val success: Boolean) : PublicSurveyEvent
    object NavigateOnward : PublicSurveyEvent
}

@HiltViewModel
class PublicSurveyViewModel @Inject constructor(
    private val surveysRepository: SurveysRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val publicMapper: SurveysPublicMapper
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = Channel<PublicSurveyEvent>(Channel.BUFFERED)
    val events: Flow<PublicSurveyEvent> = _events.receiveAsFlow()

    var surveyStarted: Boolean = false
        private set

    var launchTime: Long = 0L
        private set

    private var uploading: Boolean = false

    fun loadSurvey(baseUrl: String, teamId: String, surveyId: String) {
        if (surveyStarted) return
        viewModelScope.launch {
            _isLoading.value = true
            val response = surveysRepository.fetchPublicSurvey(baseUrl, teamId, surveyId)
            val surveyDoc = when {
                response == null -> null
                response.has("survey") && response.get("survey").isJsonObject -> response.getAsJsonObject("survey")
                else -> response
            }
            _isLoading.value = false
            if (surveyDoc == null) {
                _events.send(PublicSurveyEvent.SurveyLoadFailed)
                return@launch
            }
            surveysRepository.saveSurveyFromPublicApi(surveyDoc)
            launchTime = System.currentTimeMillis()
            surveyStarted = true
            _events.send(PublicSurveyEvent.SurveyLoaded(surveyId, teamId))
        }
    }

    fun uploadCompletedSubmission(baseUrl: String, teamId: String, surveyId: String) {
        if (uploading || !surveyStarted) return
        uploading = true
        viewModelScope.launch {
            _isLoading.value = true
            val submission = submissionsRepository.getLatestSubmissionByParentId(surveyId, "complete")
            if (submission == null || submission.lastUpdateTime < launchTime) {
                _isLoading.value = false
                _events.send(PublicSurveyEvent.NavigateOnward)
                return@launch
            }
            val answers = publicMapper.buildPublicAnswers(surveyId, submission)
            val respondent = publicMapper.parseRespondent(submission.user)
            val success = surveysRepository.submitPublicSurvey(baseUrl, teamId, surveyId, answers, respondent)
            _isLoading.value = false
            _events.send(PublicSurveyEvent.UploadFinished(success))
        }
    }
}
