package org.ole.planet.myplanet.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.settings.SettingsActivity
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.ui.surveys.SurveyFragment
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.teams.TeamFragment
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.utils.NotificationUtils

class DashboardNavigationCoordinator(
    private val activity: DashboardActivity,
    private val surveysRepository: SurveysRepository,
    private val teamsRepository: TeamsRepository,
    private val notificationManager: NotificationUtils.NotificationManager
) {

    fun handleInitialFragment(intent: Intent?) {
        if (intent != null && intent.hasExtra("fragmentToOpen")) {
            val fragmentToOpen = intent.getStringExtra("fragmentToOpen")
            if ("feedbackList" == fragmentToOpen) {
                activity.openMyFragment(FeedbackListFragment())
            }
        } else {
            activity.openCallFragment(BellDashboardFragment())
            activity.binding.appBarBell.bellToolbar.visibility = View.VISIBLE
        }
    }

    fun handleNotificationIntent(intent: Intent?) {
        val fromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
        if (fromNotification) {
            val notificationType = intent.getStringExtra("notification_type")
            val notificationId = intent.getStringExtra("notification_id")

            notificationId?.let {
                notificationManager.clearNotification(it)
                activity.markDatabaseNotificationAsRead(it)
            }

            when (notificationType) {
                NotificationUtils.TYPE_SURVEY -> {
                    val surveyId = intent.getStringExtra("surveyId")
                    if (surveyId != null) {
                        activity.openCallFragment(SurveyFragment().apply {
                            arguments = Bundle().apply {
                                putString("surveyId", surveyId)
                            }
                        })
                    } else {
                        activity.openNotificationsList(activity.user?.id ?: "")
                    }
                }
                NotificationUtils.TYPE_TASK -> {
                    val taskId = intent.getStringExtra("taskId")
                    if (taskId != null) {
                        activity.openMyFragment(TeamFragment().apply {
                            arguments = Bundle().apply {
                                putString("taskId", taskId)
                            }
                        })
                    } else {
                        activity.openNotificationsList(activity.user?.id ?: "")
                    }
                }
                NotificationUtils.TYPE_STORAGE -> {
                    activity.startActivity(Intent(activity, SettingsActivity::class.java))
                }
                NotificationUtils.TYPE_JOIN_REQUEST -> {
                    val teamName = intent.getStringExtra("teamName")
                    activity.openMyFragment(TeamFragment().apply {
                        arguments = Bundle().apply {
                            teamName?.let { putString("teamName", it) }
                        }
                    })
                }
                else -> {
                    activity.openNotificationsList(activity.user?.id ?: "")
                }
            }
        }

        if (intent?.getBooleanExtra("auto_navigate", false) == true) {
            DashboardActivity.isFromNotificationAction = true
            activity.result?.closeDrawer()

            val notificationType = intent.getStringExtra("notification_type")
            val relatedId = intent.getStringExtra("related_id")

            when (notificationType) {
                NotificationUtils.TYPE_SURVEY -> {
                    activity.lifecycleScope.launch {
                        handleSurveyNavigation(relatedId)
                    }
                }
                NotificationUtils.TYPE_TASK -> {
                    activity.lifecycleScope.launch {
                        handleTaskNavigation(relatedId)
                    }
                }
                NotificationUtils.TYPE_JOIN_REQUEST -> {
                    activity.lifecycleScope.launch {
                        handleJoinRequestNavigation(relatedId)
                    }
                }
                NotificationUtils.TYPE_RESOURCE -> {
                    activity.openCallFragment(ResourcesFragment(), "Resources")
                }
            }

            activity.lifecycleScope.launch {
                delay(1000)
                DashboardActivity.isFromNotificationAction = false
            }
        }
    }

    suspend fun handleSurveyNavigation(surveyId: String?) {
        if (surveyId != null) {
            val currentStepExam = surveysRepository.getSurvey(surveyId)
            SubmissionsAdapter.openSurvey(activity, currentStepExam?.id, false, false, "")
        }
    }

    suspend fun handleTaskNavigation(taskId: String?) {
        if (taskId == null) return

        val teamData = teamsRepository.getTaskTeamInfo(taskId)

        teamData?.let { (teamId, teamName, teamType) ->
            val f = TeamDetailFragment.newInstance(
                teamId = teamId,
                teamName = teamName,
                teamType = teamType,
                isMyTeam = true,
                navigateToPage = TasksPage
            )
            activity.openCallFragment(f)
        }
    }

    suspend fun handleJoinRequestNavigation(requestId: String?) {
        if (requestId != null) {
            val actualJoinRequestId = if (requestId.startsWith("join_request_")) {
                requestId.removePrefix("join_request_")
            } else {
                requestId
            }

            val teamId = teamsRepository.getJoinRequestTeamId(actualJoinRequestId)

            if (teamId?.isNotEmpty() == true) {
                val f = TeamDetailFragment()
                val b = Bundle()
                b.putString("id", teamId)
                b.putBoolean("isMyTeam", true)
                b.putString("navigateToPage", JoinRequestsPage.id)
                f.arguments = b
                activity.openCallFragment(f)
            }
        }
    }

    fun onClickTabItems(position: Int) {
        when (position) {
            0 -> activity.openCallFragment(BellDashboardFragment(), "dashboard")
            1 -> activity.openCallFragment(ResourcesFragment(), "library")
            2 -> activity.openCallFragment(CoursesFragment(), "course")
            4 -> activity.openEnterpriseFragment()
            3 -> activity.openCallFragment(TeamFragment(), "survey")
            5 -> {
                activity.openCallFragment(CommunityTabFragment(), "community")
            }
        }
    }
}
