package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import org.ole.planet.myplanet.repository.UserRepository
import javax.inject.Inject

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    val achievementUpdates: SharedFlow<Unit> = userRepository.achievementUpdates
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 0
        )
}
