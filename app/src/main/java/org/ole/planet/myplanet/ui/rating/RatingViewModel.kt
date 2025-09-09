package org.ole.planet.myplanet.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel

class RatingViewModel @Inject constructor(
    private val databaseService: DatabaseService
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
                
                databaseService.withRealm { realm ->
                    val existingRating = realm.where(RealmRating::class.java)
                        .equalTo("type", type)
                        .equalTo("userId", userId)
                        .equalTo("item", itemId)
                        .findFirst()

                    val allRatings = realm.where(RealmRating::class.java)
                        .equalTo("type", type)
                        .equalTo("item", itemId)
                        .findAll()

                    val totalRatings = allRatings.size
                    val averageRating = if (totalRatings > 0) {
                        allRatings.sumOf { it.rate }.toFloat() / totalRatings
                    } else {
                        0f
                    }

                    val userRating = existingRating?.rate

                    _ratingState.value = RatingUiState.Success(
                        existingRating = existingRating,
                        averageRating = averageRating,
                        totalRatings = totalRatings,
                        userRating = userRating
                    )
                }
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

                databaseService.executeTransactionAsync { realm ->
                    var ratingObject = realm.where(RealmRating::class.java)
                        .equalTo("type", type)
                        .equalTo("userId", userId)
                        .equalTo("item", itemId)
                        .findFirst()

                    if (ratingObject == null) {
                        ratingObject = realm.createObject(
                            RealmRating::class.java,
                            UUID.randomUUID().toString()
                        )
                    }

                    val userModelCopy = realm.where(RealmUserModel::class.java)
                        .equalTo("id", userId)
                        .findFirst()

                    setRatingData(ratingObject, userModelCopy, type, itemId, title, rating, comment)
                }

                _submitState.value = SubmitState.Success
                loadRatingData(type, itemId, userId)
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error(e.message ?: "Failed to submit rating")
            }
        }
    }

    private fun setRatingData(
        ratingObject: RealmRating?,
        userModel: RealmUserModel?,
        type: String,
        itemId: String,
        title: String,
        rating: Float,
        comment: String
    ) {
        ratingObject?.apply {
            isUpdated = true
            this.comment = comment
            rate = rating.toInt()
            time = Date().time
            userId = userModel?.id
            createdOn = userModel?.parentCode
            parentCode = userModel?.parentCode
            planetCode = userModel?.planetCode
            user = Gson().toJson(userModel?.serialize())
            this.type = type
            item = itemId
            this.title = title
        }
    }
}
