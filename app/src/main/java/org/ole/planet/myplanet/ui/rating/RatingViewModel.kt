package org.ole.planet.myplanet.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.RatingEntry
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.RatingSummary
import org.ole.planet.myplanet.repository.UserRepository

class RatingViewModel @Inject constructor(
    private val ratingRepository: RatingRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _ratingState = MutableStateFlow<RatingUiState>(RatingUiState.Loading)
    val ratingState: StateFlow<RatingUiState> = _ratingState.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _userState = MutableStateFlow<RealmUserModel?>(null)
    val userState: StateFlow<RealmUserModel?> = _userState.asStateFlow()

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

    fun loadRatingData(type: String, itemId: String, userId: String) {
        viewModelScope.launch {
            try {
                _ratingState.value = RatingUiState.Loading

                val user = userRepository.getUserById(userId)
                val effectiveUserId = resolveUserId(user, userId)

                val summary = ratingRepository.getRatingSummary(type, itemId, effectiveUserId)
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
        userId: String,
        rating: Float,
        comment: String
    ) {
        viewModelScope.launch {
            try {
                _submitState.value = SubmitState.Submitting

                val user = _userState.value ?: userRepository.getUserById(userId)

                if (user == null) {
                    _submitState.value = SubmitState.Error("User not found")
                    return@launch
                }

                val effectiveUserId = resolveUserId(user, userId)

                val summary = ratingRepository.submitRating(
                    type = type,
                    itemId = itemId,
                    title = title,
                    userId = effectiveUserId,
                    rating = rating,
                    comment = comment
                )

                _ratingState.value = summary.toUiState()
                _submitState.value = SubmitState.Success
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error(e.message ?: "Failed to submit rating")
            } finally {
                _submitState.value = SubmitState.Idle
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

    private fun resolveUserId(user: RealmUserModel?, fallbackId: String): String {
        if (user == null) {
            _userState.value = null
            return fallbackId
        }

        if (
            _userState.value == null ||
            _userState.value?.id != user.id ||
            _userState.value?._id != user._id
        ) {
            _userState.value = user
        }

        return user.id?.takeIf { it.isNotBlank() }
            ?: user._id?.takeIf { it.isNotBlank() }
            ?: fallbackId
    }
}
