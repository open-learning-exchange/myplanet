package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

@HiltViewModel
class SurveyViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val syncManager: SyncManager,
    @AppPreferences private val settings: SharedPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefManager = SharedPrefManager(context)
    private val serverUrlMapper = ServerUrlMapper()

    private var isTeam: Boolean = false
    private var teamId: String? = null
    private var currentIsTeamShareAllowed: Boolean = false
    private var searchQuery: String = ""

    data class SurveyUiState(val items: List<RealmStepExam>, val isSearch: Boolean)

    private val _surveys = MutableStateFlow(SurveyUiState(emptyList(), false))
    val surveys: StateFlow<SurveyUiState> = _surveys.asStateFlow()

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        object Success : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    sealed class AdoptionState {
        object Idle : AdoptionState()
        object Success : AdoptionState()
        data class Error(val message: String) : AdoptionState()
    }

    private val _adoptionState = MutableStateFlow<AdoptionState>(AdoptionState.Idle)
    val adoptionState: StateFlow<AdoptionState> = _adoptionState.asStateFlow()

    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    fun init(isTeam: Boolean, teamId: String?) {
        this.isTeam = isTeam
        this.teamId = teamId
        refreshSurveys()
    }

    fun updateSearch(query: String) {
        searchQuery = query
        refreshSurveys()
    }

    fun clearSearch() {
        searchQuery = ""
    }

    fun setTeamShareAllowed(flag: Boolean) {
        currentIsTeamShareAllowed = flag
        refreshSurveys()
    }

    fun adoptSurvey(exam: RealmStepExam, teamId: String?) {
        viewModelScope.launch {
            val success = databaseService.executeTransactionWithResultAsync { realm ->
                val userModel = realm.where(RealmUserModel::class.java)
                    .equalTo("id", settings.getString("userId", ""))
                    .findFirst()
                val sParentCode = settings.getString("parentCode", "")
                val planetCode = settings.getString("planetCode", "")

                val parentJsonString = try {
                    JSONObject().apply {
                        put("_id", exam.id)
                        put("name", exam.name)
                        put("courseId", exam.courseId ?: "")
                        put("sourcePlanet", exam.sourcePlanet ?: "")
                        put("teamShareAllowed", exam.isTeamShareAllowed)
                        put("noOfQuestions", exam.noOfQuestions)
                        put("isFromNation", exam.isFromNation)
                    }.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    "{}"
                }

                val userJsonString = try {
                    JSONObject().apply {
                        put("doc", JSONObject().apply {
                            put("_id", userModel?.id)
                            put("name", userModel?.name)
                            put("userId", userModel?.id ?: "")
                            put("teamPlanetCode", planetCode ?: "")
                            put("status", "active")
                            put("type", "team")
                            put("createdBy", userModel?.id ?: "")
                        })

                        if (isTeam && teamId != null) {
                            put("membershipDoc", JSONObject().apply {
                                put("teamId", teamId)
                            })
                        }
                    }.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    "{}"
                }

                val adoptionId = "${UUID.randomUUID()}"
                val examId = exam.id
                val userId = userModel?.id

                val existingAdoption = realm.where(RealmSubmission::class.java)
                    .equalTo("userId", userId)
                    .equalTo("parentId", examId)
                    .equalTo("status", "")
                    .findFirst()

                if (existingAdoption == null) {
                    realm.createObject(RealmSubmission::class.java, adoptionId).apply {
                        parentId = examId
                        parent = parentJsonString
                        this.userId = userId
                        user = userJsonString
                        type = "survey"
                        status = ""
                        uploaded = false
                        source = planetCode ?: ""
                        parentCode = sParentCode ?: ""
                        startTime = System.currentTimeMillis()
                        lastUpdateTime = System.currentTimeMillis()
                        isUpdated = true

                        if (isTeam && teamId != null) {
                            membershipDoc = realm.createObject(RealmMembershipDoc::class.java).apply {
                                this.teamId = teamId
                            }
                        }
                    }
                    true
                } else {
                    false
                }
            } ?: false

            if (success) {
                _adoptionState.value = AdoptionState.Success
                refreshSurveys()
            } else {
                _adoptionState.value = AdoptionState.Error(
                    context.getString(R.string.failed_to_adopt_survey)
                )
            }
        }
    }

    fun resetAdoptionState() {
        _adoptionState.value = AdoptionState.Idle
    }

    fun refreshSurveys() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = databaseService.withRealm { realm ->
                val submissionQuery = realm.where(RealmSubmission::class.java)
                    .isNotNull("membershipDoc")
                    .findAll()

                val query = realm.where(RealmStepExam::class.java).equalTo("type", "surveys")

                if (isTeam) {
                    val teamSpecificExams = submissionQuery
                        .filter { it.membershipDoc?.teamId == teamId }
                        .mapNotNull { JSONObject(it.parent ?: "{}").optString("_id") }
                        .filter { it.isNotEmpty() }
                        .toSet()

                    if (currentIsTeamShareAllowed) {
                        val teamSubmissions = submissionQuery
                            .filter { it.membershipDoc?.teamId == teamId }
                            .mapNotNull { JSONObject(it.parent ?: "{}" ).optString("_id") }
                            .filter { it.isNotEmpty() }
                            .toSet()

                        query.beginGroup()
                            .equalTo("isTeamShareAllowed", true)
                            .and()
                            .not().`in`("id", teamSubmissions.toTypedArray())
                        query.endGroup()
                    } else {
                        query.beginGroup()
                            .equalTo("teamId", teamId)
                            .or()
                            .`in`("id", teamSpecificExams.toTypedArray())
                        query.endGroup()
                    }
                } else {
                    query.equalTo("isTeamShareAllowed", false)
                }

                realm.copyFromRealm(query.findAll())
            }

            val filtered = if (searchQuery.isNotEmpty()) search(searchQuery, result) else result
            _surveys.value = SurveyUiState(filtered, searchQuery.isNotEmpty())
        }
    }

    private fun search(s: String, list: List<RealmStepExam>): List<RealmStepExam> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQuery = normalizeText(s)
        val startsWithQuery = mutableListOf<RealmStepExam>()
        val containsQuery = mutableListOf<RealmStepExam>()

        for (item in list) {
            val title = item.name?.let { normalizeText(it) } ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (queryParts.all { title.contains(normalizeText(it), ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun startExamSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isExamsSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        viewModelScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url -> isServerReachable(url) }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                _syncState.value = SyncState.Syncing
            }

            override fun onSyncComplete() {
                _syncState.value = SyncState.Success
                prefManager.setExamsSynced(true)
                refreshSurveys()
            }

            override fun onSyncFailed(msg: String?) {
                _syncState.value = SyncState.Error(msg ?: "Unknown error")
            }
        }, "full", listOf("exams"))
    }
}

