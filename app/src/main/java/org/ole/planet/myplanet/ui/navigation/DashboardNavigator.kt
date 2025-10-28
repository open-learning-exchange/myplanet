package org.ole.planet.myplanet.ui.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.ole.planet.myplanet.ui.chat.ChatHistoryListFragment
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.dashboard.AboutFragment
import org.ole.planet.myplanet.ui.dashboard.BellDashboardFragment
import org.ole.planet.myplanet.ui.dashboard.DisclaimerFragment
import org.ole.planet.myplanet.ui.dashboard.InactiveDashboardFragment
import org.ole.planet.myplanet.ui.dashboard.MyActivityFragment
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationsFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.mylife.LifeFragment
import org.ole.planet.myplanet.ui.myhealth.MyHealthFragment
import org.ole.planet.myplanet.ui.mypersonals.MyPersonalsFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.ui.userprofile.UserProfileFragment

/**
 * Coordinates dashboard fragment transactions based on [DashboardDestination].
 */
class DashboardNavigator(
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int,
    private val bottomNavigationView: BottomNavigationView? = null,
    private val drawerSelectionListener: ((Long?) -> Unit)? = null
) {
    private val destinationsByTag = mutableMapOf<String, DashboardDestination>()

    fun navigate(destination: DashboardDestination) {
        val tag = destination.tag
        destinationsByTag[tag] = destination

        val existingFragment = fragmentManager.findFragmentByTag(tag)
        if (existingFragment != null) {
            if (existingFragment.isVisible) {
                applySelection(destination)
                return
            }
            NavigationHelper.popBackStack(fragmentManager, tag, 0)
            applySelection(destination)
            return
        }

        val fragment = destination.createFragment()
        NavigationHelper.replaceFragment(
            fragmentManager,
            containerId,
            fragment,
            addToBackStack = destination.addToBackStack,
            tag = tag
        )
        applySelection(destination)
    }

    fun handleBackStackChanged() {
        val fragment = fragmentManager.findFragmentById(containerId) ?: return
        val tag = fragment.tag ?: return
        val destination = destinationsByTag[tag] ?: inferDestination(fragment, tag)
        applySelection(destination)
    }

    private fun inferDestination(fragment: Fragment, tag: String): DashboardDestination {
        val inferred = when (fragment) {
            is BellDashboardFragment -> DashboardDestination.Home
            is ResourcesFragment -> {
                val isMy = fragment.arguments?.getBoolean("isMyCourseLib", false) == true
                if (isMy) DashboardDestination.MyLibrary(fragment.arguments) else DashboardDestination.Library
            }
            is CoursesFragment -> {
                val isMy = fragment.arguments?.getBoolean("isMyCourseLib", false) == true
                if (isMy) DashboardDestination.MyCourses(fragment.arguments) else DashboardDestination.Courses
            }
            is TeamFragment -> {
                val type = fragment.arguments?.getString("type")
                val fromDashboard = fragment.arguments?.getBoolean("fromDashboard", false) == true
                DashboardDestination.Team(fromDashboard = fromDashboard, type = type, extras = fragment.arguments)
            }
            is CommunityTabFragment -> DashboardDestination.Community
            is SurveyFragment -> DashboardDestination.Surveys
            is FeedbackFragment -> DashboardDestination.Feedback
            is FeedbackListFragment -> DashboardDestination.FeedbackList
            is NotificationsFragment -> DashboardDestination.Notifications
            is ChatHistoryListFragment -> DashboardDestination.ChatHistory
            is DisclaimerFragment -> DashboardDestination.Disclaimer
            is AboutFragment -> DashboardDestination.About
            is MyActivityFragment -> DashboardDestination.MyActivity
            is LifeFragment -> DashboardDestination.Life
            is InactiveDashboardFragment -> DashboardDestination.Inactive
            is UserProfileFragment -> DashboardDestination.UserProfile
            is MyPersonalsFragment -> DashboardDestination.MyPersonals
            is MyHealthFragment -> DashboardDestination.MyHealth
            else -> DashboardDestination.Custom({ fragment }, tag)
        }
        destinationsByTag[tag] = inferred
        return inferred
    }

    private fun applySelection(destination: DashboardDestination) {
        destination.selection.bottomNavigationItemId?.let { id ->
            val menu = bottomNavigationView?.menu ?: return@let
            val item = menu.findItem(id)
            if (item != null && !item.isChecked) {
                item.isChecked = true
            }
        }
        drawerSelectionListener?.invoke(destination.selection.drawerItemIdentifier)
    }
}
