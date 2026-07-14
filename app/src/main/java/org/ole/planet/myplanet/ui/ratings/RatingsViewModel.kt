package org.ole.planet.myplanet.ui.ratings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.RatingEntry
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class RatingsViewModel @Inject constructor(
    private val ratingsRepository: RatingsRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _ratingState = MutableStateFlow<RatingUiState>(RatingUiState.Loading)
    val ratingState: StateFlow<RatingUiState> = _ratingState.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _userState = MutableStateFlow<RealmUser?>(null)
    val userState: StateFlow<RealmUser?> = _userState.asStateFlow()

    sealed class RatingUiState {
        object Loading : RatingUiState()
        data class Success(
            val existingRating: RatingEntry?,
            val averageRating: Float,
            val totalRatings: Int,
            val userRating: Int?
        ) : RatingUiState()
        data class Error(val message: String) : RatingUiState()
    }

    sealed class SubmitState {
        object Idle : SubmitState()
        object Submitting : SubmitState()
        object Success : SubmitState()
        data class Error(val message: String) : SubmitState()
    }

    fun loadRatingData(type: String, itemId: String) {
        viewModelScope.launch {
            try {
                _ratingState.value = RatingUiState.Loading

                val user = userRepository.getUserProfile()
                _userState.value = user

                if (user == null) {
                    _ratingState.value = RatingUiState.Error("User not found")
                    return@launch
                }

                val userId = user.id?.takeIf { it.isNotBlank() } ?: user._id ?: ""

                val summary = ratingsRepository.getRatingSummary(type, itemId, userId)
                _ratingState.value = summary.toUiState()
            } catch (e: Exception) {
                _userState.value = null
                _ratingState.value = RatingUiState.Error(e.message ?: "Failed to load rating data")
            }
        }
    }

    fun submitRating(
        type: String,
        itemId: String,
        title: String,
        rating: Float,
        comment: String
    ) {
        viewModelScope.launch {
            try {
                _submitState.value = SubmitState.Submitting

                val user = _userState.value ?: userRepository.getUserProfile()

                if (user == null) {
                    _submitState.value = SubmitState.Error("User not found")
                    return@launch
                }

                _userState.value = user

                val userId = user.id?.takeIf { it.isNotBlank() } ?: user._id ?: ""

                val summary = ratingsRepository.submitRating(
                    type = type,
                    itemId = itemId,
                    title = title,
                    userId = userId,
                    rating = rating,
                    comment = comment
                )

                _ratingState.value = summary.toUiState()
                _submitState.value = SubmitState.Success
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error(e.message ?: "Failed to submit rating")
            }
        }
    }

    private fun RatingSummary.toUiState(): RatingUiState.Success =
        RatingUiState.Success(
            existingRating = existingRating,
            averageRating = averageRating,
            totalRatings = totalRatings,
            userRating = userRating
        )
}
