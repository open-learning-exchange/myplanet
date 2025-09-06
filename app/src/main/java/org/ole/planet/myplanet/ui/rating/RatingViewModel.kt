package org.ole.planet.myplanet.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.repository.RatingRepository

class RatingViewModel @Inject constructor(
    private val ratingRepository: RatingRepository
) : ViewModel() {

    private val _ratingState = MutableStateFlow<RatingUiState>(RatingUiState.Loading)
    val ratingState: StateFlow<RatingUiState> = _ratingState.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    sealed class RatingUiState {
        object Loading : RatingUiState()
        data class Success(
            val existingRating: RealmRating?,
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

                val existingRating = ratingRepository.getRating(type, itemId, userId)
                val (averageRating, totalRatings) =
                    ratingRepository.getRatingSummary(type, itemId)
                val userRating = existingRating?.rate

                _ratingState.value = RatingUiState.Success(
                    existingRating = existingRating,
                    averageRating = averageRating,
                    totalRatings = totalRatings,
                    userRating = userRating
                )
            } catch (e: Exception) {
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
                databaseService.realmInstance.use { realm ->
                    realm.executeTransactionAsync(
                        { backgroundRealm ->
                            var ratingObject = backgroundRealm.where(RealmRating::class.java)
                                .equalTo("type", type)
                                .equalTo("userId", userId)
                                .equalTo("item", itemId)
                                .findFirst()

                            if (ratingObject == null) {
                                ratingObject = backgroundRealm.createObject(
                                    RealmRating::class.java,
                                    UUID.randomUUID().toString()
                                )
                            }

                            val userModelCopy = backgroundRealm.where(RealmUserModel::class.java)
                                .equalTo("id", userId)
                                .findFirst()

                            setRatingData(ratingObject, userModelCopy, type, itemId, title, rating, comment)
                        },
                        {
                            _submitState.value = SubmitState.Success
                            loadRatingData(type, itemId, userId)
                        },
                        { error ->
                            _submitState.value = SubmitState.Error(
                                error.message ?: "Failed to submit rating"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error(e.message ?: "Failed to submit rating")
            }
        }
    }
}
