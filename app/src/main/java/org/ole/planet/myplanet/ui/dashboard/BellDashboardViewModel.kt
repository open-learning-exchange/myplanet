package org.ole.planet.myplanet.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val databaseService: DatabaseService, @ApplicationContext private val context: Context
) : ViewModel() {
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _completedCourses = MutableStateFlow<List<CourseCompletion>>(emptyList())
    val completedCourses: StateFlow<List<CourseCompletion>> = _completedCourses.asStateFlow()

    private val _bellData = MutableStateFlow<BellData?>(null)
    val bellData: StateFlow<BellData?> = _bellData.asStateFlow()

    init {
        viewModelScope.launch {
            isNetworkConnectedFlow.collect { isConnected ->
                if (isConnected) {
                    updateNetworkStatus(NetworkStatus.Connecting)
                } else {
                    updateNetworkStatus(NetworkStatus.Disconnected)
                }
            }
        }
    }

    fun loadCompletedCourses(userId: String?) {
        viewModelScope.launch {
            val completed = databaseService.withRealmAsync { realm ->
                val myCourses = RealmMyCourse.getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).findAll())
                val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)

                myCourses.filter { course ->
                    val progress = courseProgress[course.id]
                    progress?.let {
                        it.asJsonObject["current"].asInt == it.asJsonObject["max"].asInt
                    } == true
                }.map {
                    CourseCompletion(it.courseId, it.courseTitle)
                }
            }
            _completedCourses.value = completed
        }
    }

    private fun updateNetworkStatus(status: NetworkStatus) {
        _networkStatus.value = status
    }

    suspend fun checkServerConnection(serverUrl: String): Boolean {
        val reachable = withContext(Dispatchers.IO) {
            isServerReachable(serverUrl)
        }
        updateNetworkStatus(if (reachable) NetworkStatus.Connected else NetworkStatus.Disconnected)
        return reachable
    }

    fun loadBellData(userId: String?, settings: SharedPreferences) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    val realmObjects = RealmMyLife.getMyLifeByUserId(realm, settings)
                    if (realmObjects.isEmpty()) {
                        val myLifeListBase = getMyLifeListBase(userId)
                        realm.executeTransaction { r ->
                            var weight = 1
                            for (item in myLifeListBase) {
                                val ml = r.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                                ml.title = item.title
                                ml.imageId = item.imageId
                                ml.weight = weight
                                ml.userId = item.userId
                                ml.isVisible = true
                                weight++
                            }
                        }
                    }

                    val rawMylife = RealmMyLife.getMyLifeByUserId(realm, settings)
                    val dbMylife = rawMylife.filter { it.isVisible }.map {
                        MyLifeItem(it.title, getImageResource(it.imageId ?: ""), it.isVisible)
                    }

                    val surveyCount = RealmSubmission.getNoOfSurveySubmissionByUser(userId, realm)
                    BellData(dbMylife, surveyCount)
                }
            }
            _bellData.value = data
        }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId ?: "", context.getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("my_achievement", userId ?: "", context.getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId ?: "", context.getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId ?: "", context.getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId ?: "", context.getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_calendar", userId ?: "", context.getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId ?: "", context.getString(R.string.mypersonals)))
        return myLifeList
    }

    private fun getImageResource(imageName: String): Int {
        return when (imageName) {
            "ic_myhealth" -> R.drawable.ic_myhealth
            "my_achievement" -> R.drawable.my_achievement
            "ic_submissions" -> R.drawable.ic_submissions
            "ic_my_survey" -> R.drawable.ic_my_survey
            "ic_references" -> R.drawable.ic_references
            "ic_calendar" -> R.drawable.ic_calendar
            "ic_mypersonals" -> R.drawable.ic_mypersonals
            else -> R.drawable.ic_myhealth
        }
    }
}

data class CourseCompletion(val courseId: String?, val courseTitle: String?)

data class MyLifeItem(
    val title: String?,
    val imageId: Int,
    val isVisible: Boolean
)

data class BellData(
    val myLifeItems: List<MyLifeItem>,
    val surveyCount: Int
)

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
