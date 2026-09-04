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
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@HiltViewModel
class LeadersViewModel @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _leaders = MutableStateFlow<List<UserEntity>?>(null)
    val leaders: StateFlow<List<UserEntity>?> = _leaders.asStateFlow()

    init {
        loadLeaders()
    }

    private fun loadLeaders() {
        viewModelScope.launch(dispatcherProvider.default) {
            _leaders.value = configurationsRepository.getCommunityLeaders()
        }
    }
}
