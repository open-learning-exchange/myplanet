package org.ole.planet.myplanet.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository

sealed class UserInformationResult {
    object UpdateProfileSuccess : UserInformationResult()
    data class UpdateProfileError(val message: String) : UserInformationResult()
    object MarkSubmissionSuccess : UserInformationResult()
    data class MarkSubmissionError(val message: String) : UserInformationResult()
}

@HiltViewModel
class UserInformationViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val submissionsRepository: SubmissionsRepository
) : ViewModel() {

    private val _resultEvent = MutableSharedFlow<UserInformationResult>(extraBufferCapacity = 1)
    val resultEvent: SharedFlow<UserInformationResult> = _resultEvent.asSharedFlow()

    fun updateProfile(userId: String?, user: JsonObject) {
        viewModelScope.launch {
            try {
                userRepository.updateProfileFields(userId, user)
                _resultEvent.emit(UserInformationResult.UpdateProfileSuccess)
            } catch (e: Exception) {
                _resultEvent.emit(UserInformationResult.UpdateProfileError(e.message ?: ""))
            }
        }
    }

    fun markSubmissionComplete(submissionId: String?, user: JsonObject) {
        viewModelScope.launch {
            if (submissionId.isNullOrEmpty()) {
                _resultEvent.emit(UserInformationResult.MarkSubmissionError("no ID provided"))
                return@launch
            }
            try {
                submissionsRepository.markSubmissionComplete(submissionId, user)
                _resultEvent.emit(UserInformationResult.MarkSubmissionSuccess)
            } catch (e: Exception) {
                _resultEvent.emit(UserInformationResult.MarkSubmissionError(e.message ?: ""))
            }
        }
    }
}
