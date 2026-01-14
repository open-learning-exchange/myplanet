package org.ole.planet.myplanet.ui.health

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.HealthRecord
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.HealthRepository
import javax.inject.Inject

@HiltViewModel
class MyHealthViewModel @Inject constructor(
    private val healthRepository: HealthRepository
) : ViewModel() {

    private val _healthData = MutableLiveData<HealthRecord?>()
    val healthData: LiveData<HealthRecord?> = _healthData

    fun fetchHealthData(userId: String, currentUser: RealmUserModel) {
        viewModelScope.launch {
            _healthData.value = healthRepository.getHealthData(userId, currentUser)
        }
    }
}
