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
import org.ole.planet.myplanet.repository.UserReadRepository

data class CommunityTabState(
    val planetCode: String,
    val parentCode: String,
    val communityName: String,
    val planetType: String?
) {
    val planetViewInId: String get() = "$planetCode@$parentCode"
    val communityViewInId: String get() = "$communityName@$parentCode"
    val title: String get() = planetCode.ifEmpty { communityName }
}

@HiltViewModel
class CommunityTabViewModel @Inject constructor(
    private val configurationsRepository: ConfigurationsRepository,
    private val userRepository: UserReadRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CommunityTabState?>(null)
    val state: StateFlow<CommunityTabState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = configurationsRepository.getCommunityConfiguration()
            val user = userRepository.getUserModel()
            val planetCode = user?.planetCode.orEmpty()

            _state.value = CommunityTabState(
                planetCode = planetCode,
                parentCode = config.parentCode,
                communityName = config.communityName,
                planetType = config.planetType
            )
        }
    }
}
