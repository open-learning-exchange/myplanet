package org.ole.planet.myplanet.ui.surveys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository

@HiltViewModel
class PublicSurveyViewModel @Inject constructor(
    private val surveysRepository: SurveysRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val payloadBuilder: PublicSurveyPayloadBuilder
) : ViewModel() {

    sealed class SurveyLoadState {
        object Idle : SurveyLoadState()
        object Loading : SurveyLoadState()
        data class Success(val surveyDoc: JsonObject) : SurveyLoadState()
        object Error : SurveyLoadState()
    }

    sealed class UploadEvent {
        object NavigateOnward : UploadEvent()
        data class ShowToastAndNavigate(val messageResId: Int) : UploadEvent()
    }

    private val _loadState = MutableStateFlow<SurveyLoadState>(SurveyLoadState.Idle)
    val loadState: StateFlow<SurveyLoadState> = _loadState.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _uploadEvents = MutableSharedFlow<UploadEvent>(extraBufferCapacity = 1)
    val uploadEvents: SharedFlow<UploadEvent> = _uploadEvents.asSharedFlow()

    var launchTime = 0L
        private set

    fun loadSurvey(baseUrl: String, teamId: String, surveyId: String) {
        if (_loadState.value !is SurveyLoadState.Idle) return
        _loadState.value = SurveyLoadState.Loading
        viewModelScope.launch {
            val response = surveysRepository.fetchPublicSurvey(baseUrl, teamId, surveyId)
            val surveyDoc = when {
                response == null -> null
                response.has("survey") && response.get("survey").isJsonObject -> response.getAsJsonObject("survey")
                else -> response
            }
            if (surveyDoc == null) {
                _loadState.value = SurveyLoadState.Error
                return@launch
            }
            surveysRepository.saveSurveyFromPublicApi(surveyDoc)
            launchTime = System.currentTimeMillis()
            _loadState.value = SurveyLoadState.Success(surveyDoc)
        }
    }

    fun uploadCompletedSubmission(baseUrl: String, teamId: String, surveyId: String) {
        if (_uploading.value) return
        _uploading.value = true
        viewModelScope.launch {
            try {
                val submission = submissionsRepository.getLatestSubmissionByParentId(surveyId, "complete")
                if (submission == null || submission.lastUpdateTime < launchTime) {
                    _uploadEvents.emit(UploadEvent.NavigateOnward)
                    return@launch
                }
                val questions = surveysRepository.getExamQuestions(surveyId)
                val answers = payloadBuilder.buildPublicAnswers(questions, submission)
                val respondent = submission.user?.takeIf { it.isNotBlank() && it != "{}" }?.let {
                    try {
                        JsonParser.parseString(it).asJsonObject.let(payloadBuilder::sanitizeRespondent)
                    } catch (e: Exception) {
                        null
                    }
                }
                val success = surveysRepository.submitPublicSurvey(baseUrl, teamId, surveyId, answers, respondent)
                val messageResId = if (success) R.string.survey_submitted else R.string.survey_submit_failed
                _uploadEvents.emit(UploadEvent.ShowToastAndNavigate(messageResId))
            } finally {
                _uploading.value = false
            }
        }
    }
}
