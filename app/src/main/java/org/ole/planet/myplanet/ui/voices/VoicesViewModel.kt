package org.ole.planet.myplanet.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import com.google.gson.JsonArray
import org.ole.planet.myplanet.model.RealmNews
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
            withContext(dispatcherProvider.io) {
                voicesRepository.deletePost(newsId, teamName)
            }
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
            val result = withContext(dispatcherProvider.io) {
                voicesRepository.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName)
            }
            onResult(result)
        }
    }

    // Note: The following are read-only suspend functions designed to be called directly from
    // the UI's lifecycleScope, avoiding intermediate MutableStateFlow caching for point-in-time reads.
    suspend fun getUserById(userId: String): RealmUser? {
        return withContext(dispatcherProvider.io) {
            userRepository.getUserById(userId)
        }
    }

    suspend fun getReplyCount(newsId: String): Int {
        return withContext(dispatcherProvider.io) {
            try {
                voicesRepository.getReplyCount(newsId)
            } catch (e: Exception) {
                0
            }
        }
    }

    suspend fun getLibraryResource(resourceId: String): RealmMyLibrary? {
        return withContext(dispatcherProvider.io) {
            voicesRepository.getLibraryResource(resourceId)
        }
    }

    suspend fun isTeamLeader(teamId: String?, userId: String?): Boolean {
        return withContext(dispatcherProvider.io) {
            try {
                if (teamId != null) teamsRepository.isTeamLeader(teamId, userId) else false
            } catch (e: Exception) {
                false
            }
        }
    }

    fun filterNewsByLabel(
        newsList: List<RealmNews?>,
        selectedLabel: String
    ): Pair<List<String>, List<RealmNews?>> {
        val labelDisplayToValue = mutableMapOf<String, String>()
        val allLabels = mutableSetOf<String>()
        allLabels.add("All")

        Constants.LABELS.forEach { (labelName, labelValue) ->
            allLabels.add(labelName)
            labelDisplayToValue[labelName] = labelValue
        }

        allLabels.add("Shared Chat")

        newsList.forEach { news ->
            val sharedTeamName = extractSharedTeamName(news)
            if (sharedTeamName.isNotEmpty()) {
                allLabels.add(sharedTeamName)
            }

            news?.labels?.forEach { label ->
                val labelName = Constants.LABELS.entries.find { it.value == label }?.key
                    ?: VoicesLabelManager.formatLabelValue(label)
                allLabels.add(labelName)
                labelDisplayToValue.putIfAbsent(labelName, label)
            }
        }

        val displayLabels = allLabels.sorted()

        val filteredList = if (selectedLabel == "All") {
            newsList
        } else {
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
                        extractSharedTeamName(news) == selectedLabel
                    }
                }
            }
        }

        return Pair(displayLabels, filteredList)
    }

    private fun extractSharedTeamName(news: RealmNews?): String {
        if (!news?.viewIn.isNullOrEmpty()) {
            try {
                val ar = JsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                if (ar.size() > 1) {
                    val ob = ar[0].asJsonObject
                    if (ob.has("name") && !ob.get("name").isJsonNull) {
                        return ob.get("name").asString
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }
}
