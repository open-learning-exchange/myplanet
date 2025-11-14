package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.realm.Realm
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow

class BellDashboardViewModel : ViewModel() {
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Disconnected)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _completedCourses = MutableStateFlow<List<CourseCompletion>>(emptyList())
    val completedCourses: StateFlow<List<CourseCompletion>> = _completedCourses.asStateFlow()

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
        viewModelScope.launch(Dispatchers.IO) {
            var realm: Realm? = null
            try {
                realm = Realm.getDefaultInstance()
                val myCourses = RealmMyCourse.getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).findAll())
                val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)

                val completed = myCourses.filter { course ->
                    val progress = courseProgress[course.id]
                    progress?.let {
                        it.asJsonObject["current"].asInt == it.asJsonObject["max"].asInt
                    } == true
                }.map {
                    CourseCompletion(it.courseId, it.courseTitle)
                }
                _completedCourses.value = completed
            } finally {
                realm?.close()
            }
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
}

data class CourseCompletion(val courseId: String?, val courseTitle: String?)

sealed class NetworkStatus {
    object Disconnected : NetworkStatus()
    object Connecting : NetworkStatus()
    object Connected : NetworkStatus()
}
