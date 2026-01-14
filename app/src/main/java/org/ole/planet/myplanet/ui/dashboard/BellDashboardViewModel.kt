package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow

@HiltViewModel
class BellDashboardViewModel @Inject constructor(
    private val databaseService: DatabaseService
) : ViewModel() {
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
        viewModelScope.launch {
            android.util.Log.d("BadgeConditions", "========== LOADING BADGES (WEB MATCHING MODE) ==========")
            android.util.Log.d("BadgeConditions", "Starting badge load for userId: $userId")

            val completed = databaseService.withRealmAsync { realm ->
                val myCourses = RealmMyCourse.getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).findAll())
                android.util.Log.d("BadgeConditions", "Total user courses found: ${myCourses.size}")

                val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)
                android.util.Log.d("BadgeConditions", "Course progress entries found: ${courseProgress.size}")

                // Get all certifications to check which courses are part of certification programs
                val allCertifications = realm.where(RealmCertification::class.java).findAll()
                android.util.Log.d("BadgeConditions", "Total certifications in database: ${allCertifications.size}")

                // Log certification details
                allCertifications.forEachIndexed { idx, cert ->
                    android.util.Log.d("BadgeConditions", "Certification #${idx + 1}: ${cert.name}")
                    android.util.Log.d("BadgeConditions", "  - ID: ${cert._id}")
                }

                // Check user's achievement record
                val userAchievement = realm.where(org.ole.planet.myplanet.model.RealmAchievement::class.java)
                    .equalTo("_id", userId + "@" + realm.where(org.ole.planet.myplanet.model.RealmUserModel::class.java)
                        .equalTo("id", userId)
                        .findFirst()?.planetCode)
                    .findFirst()

                android.util.Log.d("BadgeConditions", "User achievement record found: ${userAchievement != null}")
                if (userAchievement != null) {
                    android.util.Log.d("BadgeConditions", "Achievement header: ${userAchievement.achievementsHeader}")
                    android.util.Log.d("BadgeConditions", "Send to nation: ${userAchievement.sendToNation}")
                    android.util.Log.d("BadgeConditions", "Number of achievements: ${userAchievement.achievements?.size ?: 0}")
                }

                val completedCourses = mutableListOf<CourseCompletion>()
                myCourses.forEachIndexed { index, course ->
                    val hasValidId = !course.courseId.isNullOrBlank()
                    val hasValidTitle = !course.courseTitle.isNullOrBlank()

                    // Get all progress records for this course and user
                    val progressRecords = realm.where(org.ole.planet.myplanet.model.RealmCourseProgress::class.java)
                        .equalTo("userId", userId)
                        .equalTo("courseId", course.courseId)
                        .findAll()

                    // Count UNIQUE steps that are passed (handles duplicate progress records)
                    val passedStepNumbers = progressRecords
                        .filter { it.passed }
                        .map { it.stepNum }
                        .toSet()
                    val passedSteps = passedStepNumbers.size
                    val totalSteps = course.courseSteps?.size ?: 0

                    // Web logic: ALL steps must be passed AND course must have at least one step
                    val allStepsPassed = passedSteps == totalSteps && totalSteps > 0

                    // Check if this course is part of any certification (for badge color)
                    val isPartOfCertification = RealmCertification.isCourseCertified(realm, course.courseId)

                    android.util.Log.d("BadgeConditions", "Course #${index + 1}: ${course.courseTitle}")
                    android.util.Log.d("BadgeConditions", "  - Course ID: ${course.courseId}")
                    android.util.Log.d("BadgeConditions", "  - Total steps: $totalSteps")
                    android.util.Log.d("BadgeConditions", "  - Passed steps: $passedSteps")
                    android.util.Log.d("BadgeConditions", "  - All steps passed: $allStepsPassed")
                    android.util.Log.d("BadgeConditions", "  - Has Valid ID: $hasValidId")
                    android.util.Log.d("BadgeConditions", "  - Has Valid Title: $hasValidTitle")
                    android.util.Log.d("BadgeConditions", "  - Part of Certification: $isPartOfCertification")

                    // Match web behavior: Show badge if ALL steps are passed AND course has steps
                    if (allStepsPassed && hasValidId && hasValidTitle) {
                        completedCourses.add(CourseCompletion(course.courseId, course.courseTitle))
                        android.util.Log.d("BadgeConditions", "  ✓ ADDED TO BADGE LIST (all steps passed)")
                    } else {
                        when {
                            totalSteps == 0 -> android.util.Log.d("BadgeConditions", "  ✗ NO STEPS - Badge not shown")
                            !allStepsPassed -> android.util.Log.d("BadgeConditions", "  ✗ NOT ALL STEPS PASSED ($passedSteps/$totalSteps) - Badge not shown")
                            !hasValidId || !hasValidTitle -> android.util.Log.d("BadgeConditions", "  ✗ INVALID DATA - Badge not shown")
                        }
                    }
                }

                android.util.Log.d("BadgeConditions", "Total completed courses (badges to show): ${completedCourses.size}")
                android.util.Log.d("BadgeConditions", "Web matching logic: Showing courses where ALL steps are passed")
                completedCourses
            }
            _completedCourses.value = completed
            android.util.Log.d("BadgeConditions", "========== BADGE LOADING COMPLETE ==========")
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
