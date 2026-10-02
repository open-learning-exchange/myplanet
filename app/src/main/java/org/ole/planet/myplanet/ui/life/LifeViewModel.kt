package org.ole.planet.myplanet.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class LifeViewModel @Inject constructor(
    private val lifeRepository: LifeRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _myLifeList = MutableStateFlow<List<MyLife>>(emptyList())
    val myLifeList: StateFlow<List<MyLife>> = _myLifeList.asStateFlow()

    private suspend fun resolveUserId(): String? {
        val raw = userRepository.getCurrentUserId().orEmpty()
            .ifEmpty { userRepository.getUserModel()?.id.orEmpty() }
        return raw.takeIf { it.isNotBlank() && it != "--" }
    }

    fun loadMyLifeList(resolveLabel: (Int) -> String) {
        viewModelScope.launch {
            val userId = resolveUserId()
            val list = lifeRepository.getMyLifeByUserId(userId, LifeItemDefaults.forUser(userId, resolveLabel))
            _myLifeList.value = list
        }
    }

    fun updateVisibility(isVisible: Boolean, id: String) {
        viewModelScope.launch {
            val updatedList = lifeRepository.updateVisibility(isVisible, id, resolveUserId())
            _myLifeList.value = updatedList
        }
    }

    fun updateMyLifeListOrder(list: List<MyLife>) {
        _myLifeList.value = list
        viewModelScope.launch {
            lifeRepository.updateMyLifeListOrder(list, resolveUserId())
        }
    }
}
