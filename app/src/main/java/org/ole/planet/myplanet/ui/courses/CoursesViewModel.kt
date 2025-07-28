package org.ole.planet.myplanet.ui.courses

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.di.CourseRepository
import org.ole.planet.myplanet.di.LibraryRepository
import org.ole.planet.myplanet.di.UserRepository
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.getCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val serverUrlMapper = ServerUrlMapper()

    private val _courses = MutableStateFlow<List<RealmMyCourse>>(emptyList())
    val courses: StateFlow<List<RealmMyCourse>> = _courses.asStateFlow()

    private val _progressMap = MutableStateFlow<HashMap<String?, JsonObject>>(hashMapOf())
    val progressMap: StateFlow<HashMap<String?, JsonObject>> = _progressMap.asStateFlow()

    private val _ratingMap = MutableStateFlow<HashMap<String?, JsonObject>>(hashMapOf())
    val ratingMap: StateFlow<HashMap<String?, JsonObject>> = _ratingMap.asStateFlow()

    private val _libraryResources = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val libraryResources: StateFlow<List<RealmMyLibrary>> = _libraryResources.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun startCoursesSync(serverUrl: String, settings: SharedPreferences, prefManager: SharedPrefManager, isMyCourseLib: Boolean) {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isCoursesSynced()) {
            checkServerAndStartSync(serverUrl, settings, prefManager, isMyCourseLib)
        }
    }

    private fun checkServerAndStartSync(serverUrl: String, settings: SharedPreferences, prefManager: SharedPrefManager, isMyCourseLib: Boolean) {
        val mapping = serverUrlMapper.processUrl(serverUrl)
        viewModelScope.launch {
            updateServerIfNecessary(mapping, settings)
            startSyncManager(prefManager, isMyCourseLib)
        }
    }

    private fun startSyncManager(prefManager: SharedPrefManager, isMyCourseLib: Boolean) {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() { _isSyncing.value = true }
            override fun onSyncComplete() {
                _isSyncing.value = false
                prefManager.setCoursesSynced(true)
                refreshCoursesData(isMyCourseLib)
            }
            override fun onSyncFailed(msg: String?) {
                _isSyncing.value = false
                _syncError.value = msg
            }
        }, "full", listOf("courses"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping, settings: SharedPreferences) {
        withContext(Dispatchers.IO) {
            serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
                isServerReachable(url)
            }
        }
    }

    fun refreshCoursesData(isMyCourseLib: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val realm = userRepository.getRealm()
            val userId = userRepository.getCurrentUser()?.id
            val rating = getRatings(realm, "course", userId)
            val progress = getCourseProgress(realm, userId)
            val courses = courseRepository.getAllCourses()
            val sortedCourses = courses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
            val libs = if (isMyCourseLib) {
                val courseIds = courses.mapNotNull { it.id }
                libraryRepository.getAllLibraryItems()
                    .filter { !it.resourceOffline && it.resourceLocalAddress != null && it.courseId in courseIds }
            } else emptyList()
            withContext(Dispatchers.Main) {
                _courses.value = sortedCourses
                _ratingMap.value = rating
                _progressMap.value = progress
                if (isMyCourseLib) _libraryResources.value = libs
            }
        }
    }
}

