package org.ole.planet.myplanet.ui.life

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class LifeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lifeRepository: LifeRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _myLifeList = MutableStateFlow<List<MyLife>>(emptyList())
    val myLifeList: StateFlow<List<MyLife>> = _myLifeList.asStateFlow()

    private suspend fun resolveUserId(): String? {
        val raw = userRepository.getCurrentUserId().orEmpty()
            .ifEmpty { userRepository.getUserModel()?.id.orEmpty() }
        return raw.takeIf { it.isNotBlank() && it != "--" }
    }

    fun loadMyLifeList() {
        viewModelScope.launch {
            val list = withContext(dispatcherProvider.io) {
                val userId = resolveUserId()
                lifeRepository.getMyLifeByUserId(userId, MyLife.defaultItems(userId, context::getString))
            }
            _myLifeList.value = list
        }
    }

    fun updateVisibility(isVisible: Boolean, id: String) {
        viewModelScope.launch {
            val updatedList = withContext(dispatcherProvider.io) {
                lifeRepository.updateVisibility(isVisible, id)
            }
            _myLifeList.value = updatedList
        }
    }

    fun updateMyLifeListOrder(list: List<MyLife>) {
        _myLifeList.value = list
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                lifeRepository.updateMyLifeListOrder(list)
            }
        }
    }
}
