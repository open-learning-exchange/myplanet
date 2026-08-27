package org.ole.planet.myplanet.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.UserRepository

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val resourcesRepository: ResourcesRepository
) : ViewModel() {
    val achievementUpdates: SharedFlow<Unit> = userRepository.achievementUpdates
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 0
        )

    private val _user = MutableStateFlow<UserEntity?>(null)
    val user: StateFlow<UserEntity?> = _user.asStateFlow()

    private val _achievement = MutableStateFlow<Achievement?>(null)
    val achievement: StateFlow<Achievement?> = _achievement.asStateFlow()

    fun loadUserAndAchievement() {
        viewModelScope.launch {
            val userModel = userRepository.getUserModel()
            _user.value = userModel
            val achievementId = userModel?.id + "@" + userModel?.planetCode
            _achievement.value = userRepository.initializeAchievement(achievementId)
        }
    }

    suspend fun saveAchievement(
        achievementId: String,
        header: String,
        goals: String,
        purpose: String,
        sendToNation: String,
        achievements: JsonArray,
        references: JsonArray,
        createdOn: String,
        username: String,
        parentCode: String,
        resumeFileName: String,
        profileFields: JsonObject
    ) {
        userRepository.updateAchievement(
            achievementId = achievementId,
            header = header,
            goals = goals,
            purpose = purpose,
            sendToNation = sendToNation,
            achievements = achievements,
            references = references,
            createdOn = createdOn,
            username = username,
            parentCode = parentCode,
            resumeFileName = resumeFileName
        )
        userRepository.updateProfileFields(_user.value?.id, profileFields)
    }

    suspend fun getAllLibraries(): List<MyLibrary> {
        return resourcesRepository.getAllLibraries()
    }
}

