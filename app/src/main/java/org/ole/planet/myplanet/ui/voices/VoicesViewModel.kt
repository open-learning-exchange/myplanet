package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.VoicesLabelManager
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils

@HiltViewModel
class VoicesViewModel @Inject constructor(
    private val voicesRepository: VoicesRepository,
    private val userRepository: UserRepository,
    private val teamsRepository: TeamsRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel(), LabelManipulator by DefaultLabelManipulator(voicesRepository, dispatcherProvider) {

    fun deletePost(newsId: String, teamName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            voicesRepository.deletePost(newsId, teamName)
            onComplete()
        }
    }

    fun shareNewsToCommunity(
        newsId: String,
        userId: String,
        planetCode: String,
        parentCode: String,
        teamName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
            onResult(result)
        }
    }

    // Note: The following are read-only suspend functions designed to be called directly from
    // the UI's lifecycleScope, avoiding intermediate MutableStateFlow caching for point-in-time reads.
    suspend fun getUserById(userId: String): RealmUser? {
        return userRepository.getUserById(userId)
    }

    suspend fun getReplyCount(newsId: String): Int {
        return try {
            voicesRepository.getReplyCount(newsId)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? {
        return voicesRepository.getLibraryResource(resourceId)
    }

    suspend fun isTeamLeader(teamId: String?, userId: String?): Boolean {
        return try {
            if (teamId != null) teamsRepository.isTeamLeader(teamId, userId) else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun collectLabels(newsList: List<RealmNews?>): List<String> = withContext(dispatcherProvider.default) {
        val allLabels = mutableSetOf<String>()
        allLabels.add("All")

        Constants.LABELS.forEach { (labelName, _) ->
            allLabels.add(labelName)
        }

        allLabels.add("Shared Chat")

        newsList.forEach { news ->
            val sharedTeamName = JsonUtils.extractSharedTeamName(news)
            if (sharedTeamName.isNotEmpty()) {
                allLabels.add(sharedTeamName)
            }

            news?.labels?.forEach { label ->
                val labelName = Constants.LABELS.entries.find { it.value == label }?.key
                    ?: VoicesLabelManager.formatLabelValue(label)
                allLabels.add(labelName)
            }
        }

        allLabels.sorted()
    }

    suspend fun filterByLabel(
        newsList: List<RealmNews?>,
        selectedLabel: String
    ): List<RealmNews?> = withContext(dispatcherProvider.default) {
        if (selectedLabel == "All") return@withContext newsList

        val labelDisplayToValue = mutableMapOf<String, String>()
        Constants.LABELS.forEach { (labelName, labelValue) ->
            labelDisplayToValue[labelName] = labelValue
        }
        newsList.forEach { news ->
            news?.labels?.forEach { label ->
                val labelName = Constants.LABELS.entries.find { it.value == label }?.key
                    ?: VoicesLabelManager.formatLabelValue(label)
                labelDisplayToValue.putIfAbsent(labelName, label)
            }
        }

        newsList.filter { news ->
            when {
                selectedLabel == "Shared Chat" -> {
                    news?.chat == true || news?.viewableBy.equals("community", ignoreCase = true)
                }
                labelDisplayToValue.containsKey(selectedLabel) -> {
                    val labelValue = labelDisplayToValue[selectedLabel]
                    news?.labels?.contains(labelValue) == true
                }
                else -> {
                    JsonUtils.extractSharedTeamName(news) == selectedLabel
                }
            }
        }
    }
}
