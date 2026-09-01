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

    fun loadMyLifeList() {
        viewModelScope.launch {
            val list = withContext(dispatcherProvider.io) {
                val userId = userRepository.getCurrentUserId()
                lifeRepository.getMyLifeByUserId(userId, MyLife.defaultItems(userId, context::getString))
            }
            _myLifeList.value = list
        }
    }

    fun updateVisibility(isVisible: Boolean, id: String) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                lifeRepository.updateVisibility(isVisible, id)
            }
            loadMyLifeList()
        }
    }

    fun updateMyLifeListOrder(list: List<MyLife>) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                lifeRepository.updateMyLifeListOrder(list)
            }
        }
    }
}
