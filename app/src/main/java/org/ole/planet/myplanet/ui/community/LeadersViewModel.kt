package org.ole.planet.myplanet.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class LeadersViewModel @Inject constructor(
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _leaders = MutableStateFlow<List<UserEntity>>(emptyList())
    val leaders: StateFlow<List<UserEntity>> = _leaders.asStateFlow()

    init {
        loadLeaders()
    }

    private fun loadLeaders() {
        viewModelScope.launch(dispatcherProvider.default) {
            val leadersString = sharedPrefManager.getCommunityLeaders()
            if (leadersString.isNotEmpty()) {
                _leaders.value = UserEntity.parseLeadersJson(leadersString)
            } else {
                _leaders.value = emptyList()
            }
        }
    }
}
