package org.ole.planet.myplanet.ui.community

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager

@HiltViewModel
class LeadersViewModel @Inject constructor(
    private val sharedPrefManager: SharedPrefManager
) : ViewModel() {

    private val _leaders = MutableStateFlow<List<UserEntity>>(emptyList())
    val leaders: StateFlow<List<UserEntity>> = _leaders.asStateFlow()

    init {
        loadLeaders()
    }

    private fun loadLeaders() {
        val leadersString = sharedPrefManager.getCommunityLeaders()
        if (leadersString.isNotEmpty()) {
            _leaders.value = UserEntity.parseLeadersJson(leadersString)
        } else {
            _leaders.value = emptyList()
        }
    }
}
