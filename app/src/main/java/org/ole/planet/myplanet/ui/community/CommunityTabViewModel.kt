package org.ole.planet.myplanet.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.services.UserSessionManager

data class CommunityTabState(
    val planetCode: String,
    val parentCode: String,
    val communityName: String,
    val planetType: String?
)

@HiltViewModel
class CommunityTabViewModel @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _state = MutableStateFlow<CommunityTabState?>(null)
    val state: StateFlow<CommunityTabState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val parentCode = configurationsRepository.getParentCode()
            val communityName = configurationsRepository.getCommunityName()
            val planetType = configurationsRepository.getPlanetType()
            val user = userSessionManager.getUserModel()
            val planetCode = user?.planetCode.orEmpty()

            _state.value = CommunityTabState(
                planetCode = planetCode,
                parentCode = parentCode,
                communityName = communityName,
                planetType = planetType
            )
        }
    }
}
