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

data class AchievementSaveRequest(
    val achievementId: String,
    val header: String,
    val goals: String,
    val purpose: String,
    val sendToNation: String,
    val achievements: JsonArray,
    val references: JsonArray,
    val createdOn: String,
    val username: String,
    val parentCode: String,
    val resumeFileName: String,
    val profileFields: JsonObject,
)

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

    private val _achievementId = MutableStateFlow<String?>(null)
    val achievementId: StateFlow<String?> = _achievementId.asStateFlow()

    fun loadUserAndAchievement() {
        viewModelScope.launch {
            val userModel = userRepository.getUserModel()
            _user.value = userModel
            val id = userModel?.let { it.id + "@" + it.planetCode }
            _achievementId.value = id
            _achievement.value = id?.let { userRepository.initializeAchievement(it) }
        }
    }

    suspend fun saveAchievement(request: AchievementSaveRequest) {
        userRepository.updateAchievement(
            achievementId = request.achievementId,
            header = request.header,
            goals = request.goals,
            purpose = request.purpose,
            sendToNation = request.sendToNation,
            achievements = request.achievements,
            references = request.references,
            createdOn = request.createdOn,
            username = request.username,
            parentCode = request.parentCode,
            resumeFileName = request.resumeFileName
        )
        userRepository.updateProfileFields(_user.value?.id, request.profileFields)
    }

    suspend fun getAllLibraries(): List<MyLibrary> {
        return resourcesRepository.getAllLibraries()
    }
}
