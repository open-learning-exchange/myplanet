package org.ole.planet.myplanet.ui.mylife

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.MyLifeRepository

@HiltViewModel
class MyLifeViewModel @Inject constructor(private val myLifeRepository: MyLifeRepository) : ViewModel() {
    private val _myLifeData = MutableLiveData<List<RealmMyLife?>>()
    val myLifeData: LiveData<List<RealmMyLife?>> = _myLifeData

    fun getMyLifeData(userId: String) {
        viewModelScope.launch {
            _myLifeData.value = myLifeRepository.getMyLifeByUserId(userId)
        }
    }

    fun updateWeight(weight: Int, id: String, userId: String) {
        viewModelScope.launch {
            myLifeRepository.updateWeight(weight, id, userId)
            getMyLifeData(userId)
        }
    }

    fun updateVisibility(isVisible: Boolean, id: String, userId: String) {
        viewModelScope.launch {
            myLifeRepository.updateVisibility(isVisible, id)
            getMyLifeData(userId)
        }
    }
}
