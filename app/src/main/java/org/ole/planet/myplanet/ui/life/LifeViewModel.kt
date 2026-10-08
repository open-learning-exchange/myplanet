package org.ole.planet.myplanet.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private var loadJob: Job? = null

    private suspend fun resolveUserId(): String? {
        val raw = userRepository.getCurrentUserId().orEmpty()
            .ifEmpty { userRepository.getUserModel()?.id.orEmpty() }
        return raw.takeIf { it.isNotBlank() && it != "--" }
    }

    fun loadMyLifeList(resolveLabel: (Int) -> String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val userId = resolveUserId()
            val initialList = lifeRepository.getMyLifeByUserId(userId, LifeItemDefaults.forUser(userId, resolveLabel))
            _myLifeList.value = initialList

            lifeRepository.observeMyLifeByUserId(userId)
                .distinctUntilChanged()
                .collect { list ->
                    _myLifeList.value = list
                }
        }
    }

    fun updateVisibility(isVisible: Boolean, id: String) {
        viewModelScope.launch {
            lifeRepository.updateVisibility(isVisible, id, resolveUserId())
        }
    }

    fun updateMyLifeListOrder(list: List<MyLife>) {
        _myLifeList.value = list
        viewModelScope.launch {
            lifeRepository.updateMyLifeListOrder(list, resolveUserId())
        }
    }
}
