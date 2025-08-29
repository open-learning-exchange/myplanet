package org.ole.planet.myplanet.ui.rating

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.repository.RatingRepository

@HiltViewModel
class RatingViewModel @Inject constructor(
    private val ratingRepository: RatingRepository
) : ViewModel() {

    private val _userRating = MutableLiveData<RealmRating?>()
    val userRating: LiveData<RealmRating?> = _userRating

    private val _saveStatus = MutableLiveData<Boolean>()
    val saveStatus: LiveData<Boolean> = _saveStatus

    fun loadRating(type: String?, itemId: String?, userId: String?) {
        viewModelScope.launch {
            _userRating.postValue(
                ratingRepository.getUserRating(type, userId, itemId)
            )
        }
    }

    fun saveRating(
        type: String?,
        itemId: String?,
        title: String?,
        userId: String?,
        comment: String,
        rating: Int
    ) {
        viewModelScope.launch {
            ratingRepository.saveRating(type, itemId, title, userId, comment, rating)
            _saveStatus.postValue(true)
        }
    }
}
