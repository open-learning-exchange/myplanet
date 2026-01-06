package org.ole.planet.myplanet.ui.survey

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SurveyViewModel @Inject constructor(
    private val surveysRepository: SurveysRepository,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val settings: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<SurveyUiState>(SurveyUiState.Idle)
    val uiState: StateFlow<SurveyUiState> = _uiState

    fun adoptSurvey(exam: RealmStepExam, teamId: String?, isTeam: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SurveyUiState.Loading(exam.id)
            try {
                surveysRepository.adoptSurvey(exam, teamId ?: "", isTeam, userProfileDbHandler, settings)
                _uiState.value = SurveyUiState.Success(exam.id)
            } catch (e: Exception) {
                _uiState.value = SurveyUiState.Error(exam.id, e.message ?: "An unknown error occurred")
            }
        }
    }

    fun adoptSurveys(exams: List<RealmStepExam>, teamId: String, isTeam: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SurveyUiState.Loading("batch")
            try {
                surveysRepository.adoptSurveys(exams, teamId, isTeam, userProfileDbHandler, settings)
                _uiState.value = SurveyUiState.Success("batch")
            } catch (e: Exception) {
                _uiState.value = SurveyUiState.Error("batch", e.message ?: "An unknown error occurred")
            }
        }
    }
}
