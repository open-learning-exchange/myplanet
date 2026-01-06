package org.ole.planet.myplanet.ui.survey

sealed class SurveyUiState {
    object Idle : SurveyUiState()
    data class Loading(val surveyId: String) : SurveyUiState()
    data class Success(val surveyId: String) : SurveyUiState()
    data class Error(val surveyId: String, val message: String) : SurveyUiState()
}
