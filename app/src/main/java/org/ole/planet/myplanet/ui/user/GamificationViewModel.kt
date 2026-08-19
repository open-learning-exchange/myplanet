package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.gamification.BadgeCategory
import org.ole.planet.myplanet.model.gamification.GamificationBadge
import org.ole.planet.myplanet.model.gamification.GamificationSummary
import org.ole.planet.myplanet.repository.GamificationRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class GamificationViewModel @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _gamificationSummary = MutableStateFlow<GamificationSummary?>(null)
    val gamificationSummary: StateFlow<GamificationSummary?> = _gamificationSummary.asStateFlow()

    private val _selectedCategory = MutableStateFlow(BadgeCategory.ALL)
    val selectedCategory: StateFlow<BadgeCategory> = _selectedCategory.asStateFlow()

    val filteredBadges: StateFlow<List<GamificationBadge>> = combine(
        _gamificationSummary,
        _selectedCategory
    ) { summary, category ->
        val badges = summary?.badges ?: emptyList()
        if (category == BadgeCategory.ALL) {
            badges
        } else {
            badges.filter { it.category == category }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private var currentUserId: String = ""
    private var currentUserName: String = ""

    fun loadGamificationData(userId: String, userName: String) {
        currentUserId = userId
        currentUserName = userName
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val uId = if (currentUserId.isNotBlank()) currentUserId else userRepository.getActiveUserIdSuspending()
            val uName = if (currentUserName.isNotBlank()) currentUserName else (userRepository.getUserModel()?.name ?: "")
            if (uId.isNotBlank() || uName.isNotBlank()) {
                _gamificationSummary.value = gamificationRepository.getGamificationSummary(uId, uName)
            }
        }
    }

    fun setCategory(category: BadgeCategory) {
        _selectedCategory.value = category
    }
}
