package org.ole.planet.myplanet.ui.community

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import javax.inject.Inject

@HiltViewModel
class LeadersViewModel @Inject constructor(
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _leaders = MutableStateFlow<List<UserEntity>>(emptyList())
    val leaders: StateFlow<List<UserEntity>> = _leaders.asStateFlow()

    init {
        loadLeaders()
    }

    private fun loadLeaders() {
        val leadersString = sharedPrefManager.getCommunityLeaders()
        if (leadersString.isNotEmpty()) {
            _leaders.value = userRepository.parseLeadersJson(leadersString)
        } else {
            _leaders.value = emptyList()
        }
    }
}
