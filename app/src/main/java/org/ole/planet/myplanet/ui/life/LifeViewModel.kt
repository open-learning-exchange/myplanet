package org.ole.planet.myplanet.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.services.UserSessionManager

@HiltViewModel
class LifeViewModel @Inject constructor(
    private val lifeRepository: LifeRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _myLifeList = MutableStateFlow<List<RealmMyLife>>(emptyList())
    val myLifeList: StateFlow<List<RealmMyLife>> = _myLifeList.asStateFlow()

    fun loadMyLife(userId: String?) {
        viewModelScope.launch {
            _myLifeList.value = lifeRepository.getMyLifeByUserId(userId)
        }
    }

    suspend fun updateVisibility(isVisible: Boolean, id: String) {
        lifeRepository.updateVisibility(isVisible, id)
    }

    suspend fun reorder(list: List<RealmMyLife>) {
        lifeRepository.updateMyLifeListOrder(list)
    }
}
