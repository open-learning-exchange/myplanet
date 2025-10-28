package org.ole.planet.myplanet.ui.navigation

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.chat.ChatHistoryListFragment
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.dashboard.AboutFragment
import org.ole.planet.myplanet.ui.dashboard.BellDashboardFragment
import org.ole.planet.myplanet.ui.dashboard.DisclaimerFragment
import org.ole.planet.myplanet.ui.dashboard.InactiveDashboardFragment
import org.ole.planet.myplanet.ui.dashboard.MyActivityFragment
import org.ole.planet.myplanet.ui.dashboard.SurveyFragment
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationsFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.mylife.LifeFragment
import org.ole.planet.myplanet.ui.resources.ResourceDetailFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.references.ReferenceFragment
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment
import org.ole.planet.myplanet.ui.userprofile.EditAchievementFragment
import org.ole.planet.myplanet.ui.userprofile.MyPersonalsFragment
import org.ole.planet.myplanet.ui.userprofile.MyHealthFragment
import org.ole.planet.myplanet.ui.submission.SubmissionDetailFragment

/**
 * Central definition of dashboard navigation targets used across the module.
 */
sealed class DashboardDestination(
    val tag: String,
    val selection: Selection = Selection(),
    val addToBackStack: Boolean = true
) {
    data class Selection(
        @IdRes val bottomNavigationItemId: Int? = null,
        val drawerItemIdentifier: Long? = null
    )

    abstract fun createFragment(): Fragment

    object Home : DashboardDestination(
        tag = "DashboardHome",
        selection = Selection(bottomNavigationItemId = R.id.menu_home, drawerItemIdentifier = 0L),
        addToBackStack = false
    ) {
        override fun createFragment(): Fragment = BellDashboardFragment()
    }

    object Library : DashboardDestination(
        tag = "ResourcesFragment",
        selection = Selection(bottomNavigationItemId = R.id.menu_library, drawerItemIdentifier = 3L)
    ) {
        override fun createFragment(): Fragment = ResourcesFragment()
    }

    data class MyLibrary(private val extras: Bundle? = null) : DashboardDestination(
        tag = "MyResourcesFragment",
        selection = Selection(bottomNavigationItemId = R.id.menu_mylibrary, drawerItemIdentifier = 1L)
    ) {
        override fun createFragment(): Fragment = ResourcesFragment().apply {
            val args = (extras?.let { Bundle(it) } ?: Bundle()).apply {
                if (!containsKey("isMyCourseLib")) {
                    putBoolean("isMyCourseLib", true)
                }
            }
            arguments = args
        }
    }

    object Courses : DashboardDestination(
        tag = "CoursesFragment",
        selection = Selection(bottomNavigationItemId = R.id.menu_courses, drawerItemIdentifier = 4L)
    ) {
        override fun createFragment(): Fragment = CoursesFragment()
    }

    data class MyCourses(private val extras: Bundle? = null) : DashboardDestination(
        tag = "MyCoursesFragment",
        selection = Selection(bottomNavigationItemId = R.id.menu_mycourses, drawerItemIdentifier = 2L)
    ) {
        override fun createFragment(): Fragment = CoursesFragment().apply {
            val args = (extras?.let { Bundle(it) } ?: Bundle()).apply {
                if (!containsKey("isMyCourseLib")) {
                    putBoolean("isMyCourseLib", true)
                }
            }
            arguments = args
        }
    }

    data class Team(
        private val fromDashboard: Boolean = false,
        private val type: String? = null,
        private val extras: Bundle? = null
    ) : DashboardDestination(
        tag = when {
            type == "enterprise" -> "Enterprise"
            fromDashboard -> "MyTeamDashboardFragment"
            else -> "TeamFragment"
        },
        selection = Selection(
            drawerItemIdentifier = when {
                type == "enterprise" -> 6L
                fromDashboard -> 0L
                else -> 5L
            }
        )
    ) {
        override fun createFragment(): Fragment = TeamFragment().apply {
            val args = (extras?.let { Bundle(it) } ?: Bundle()).apply {
                type?.let { putString("type", it) }
                if (fromDashboard) {
                    putBoolean("fromDashboard", true)
                }
            }
            arguments = args
        }
    }

    object Community : DashboardDestination(
        tag = "CommunityTabFragment",
        selection = Selection(drawerItemIdentifier = 7L)
    ) {
        override fun createFragment(): Fragment = CommunityTabFragment()
    }

    object Surveys : DashboardDestination(
        tag = "SurveyFragment",
        selection = Selection(drawerItemIdentifier = 8L)
    ) {
        override fun createFragment(): Fragment = SurveyFragment()
    }

    object Feedback : DashboardDestination(
        tag = "FeedbackFragment"
    ) {
        override fun createFragment(): Fragment = FeedbackFragment()
    }

    object FeedbackList : DashboardDestination(
        tag = "FeedbackListFragment"
    ) {
        override fun createFragment(): Fragment = FeedbackListFragment()
    }

    object Notifications : DashboardDestination(
        tag = "NotificationsFragment"
    ) {
        override fun createFragment(): Fragment = NotificationsFragment()
    }

    object ChatHistory : DashboardDestination(
        tag = "ChatHistoryListFragment"
    ) {
        override fun createFragment(): Fragment = ChatHistoryListFragment()
    }

    object Disclaimer : DashboardDestination(
        tag = "DisclaimerFragment"
    ) {
        override fun createFragment(): Fragment = DisclaimerFragment()
    }

    object About : DashboardDestination(
        tag = "AboutFragment"
    ) {
        override fun createFragment(): Fragment = AboutFragment()
    }

    object MyActivity : DashboardDestination(
        tag = "MyActivityFragment"
    ) {
        override fun createFragment(): Fragment = MyActivityFragment()
    }

    object Life : DashboardDestination(
        tag = "LifeFragment"
    ) {
        override fun createFragment(): Fragment = LifeFragment()
    }

    object Inactive : DashboardDestination(
        tag = "InactiveDashboardFragment"
    ) {
        override fun createFragment(): Fragment = InactiveDashboardFragment()
    }

    data class ResourceDetail(private val resourceId: String?) : DashboardDestination(
        tag = "ResourceDetail_${resourceId ?: "unknown"}"
    ) {
        override fun createFragment(): Fragment = ResourceDetailFragment().apply {
            arguments = Bundle().apply { putString("libraryId", resourceId) }
        }
    }

    data class SubmissionDetail(private val submissionId: String?) : DashboardDestination(
        tag = "SubmissionDetail_${submissionId ?: "unknown"}"
    ) {
        override fun createFragment(): Fragment = SubmissionDetailFragment().apply {
            arguments = Bundle().apply { putString("id", submissionId) }
        }
    }

    data class MySubmission(private val type: String? = null) : DashboardDestination(
        tag = "MySubmission_${type ?: "all"}"
    ) {
        override fun createFragment(): Fragment = type?.let { MySubmissionFragment.newInstance(it) }
            ?: MySubmissionFragment()
    }

    data class TakeCourse(private val courseId: String?, private val position: Int = 0) : DashboardDestination(
        tag = "TakeCourse_${courseId ?: position}"
    ) {
        override fun createFragment(): Fragment = TakeCourseFragment().apply {
            arguments = Bundle().apply {
                putString("id", courseId)
                putInt("position", position)
            }
        }
    }

    object Calendar : DashboardDestination(tag = "CalendarFragment") {
        override fun createFragment(): Fragment = CalendarFragment()
    }

    object References : DashboardDestination(tag = "ReferenceFragment") {
        override fun createFragment(): Fragment = ReferenceFragment()
    }

    object Achievements : DashboardDestination(tag = "AchievementFragment") {
        override fun createFragment(): Fragment = AchievementFragment()
    }

    object EditAchievement : DashboardDestination(tag = "EditAchievementFragment") {
        override fun createFragment(): Fragment = EditAchievementFragment()
    }

    object MyPersonals : DashboardDestination(tag = "MyPersonalsFragment") {
        override fun createFragment(): Fragment = MyPersonalsFragment()
    }

    object MyHealth : DashboardDestination(tag = "MyHealthFragment") {
        override fun createFragment(): Fragment = MyHealthFragment()
    }

    data class Custom(
        private val fragmentFactory: () -> Fragment,
        private val stableTag: String,
        private val customSelection: Selection = Selection(),
        private val shouldAddToBackStack: Boolean = true
    ) : DashboardDestination(stableTag, customSelection, shouldAddToBackStack) {
        override fun createFragment(): Fragment = fragmentFactory()
    }
}
