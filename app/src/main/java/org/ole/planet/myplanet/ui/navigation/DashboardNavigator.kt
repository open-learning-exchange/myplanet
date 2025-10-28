package org.ole.planet.myplanet.ui.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView

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
            is org.ole.planet.myplanet.ui.dashboard.BellDashboardFragment -> DashboardDestination.Home
            is org.ole.planet.myplanet.ui.resources.ResourcesFragment -> {
                val isMy = fragment.arguments?.getBoolean("isMyCourseLib", false) == true
                if (isMy) DashboardDestination.MyLibrary(fragment.arguments) else DashboardDestination.Library
            }
            is org.ole.planet.myplanet.ui.courses.CoursesFragment -> {
                val isMy = fragment.arguments?.getBoolean("isMyCourseLib", false) == true
                if (isMy) DashboardDestination.MyCourses(fragment.arguments) else DashboardDestination.Courses
            }
            is org.ole.planet.myplanet.ui.team.TeamFragment -> {
                val type = fragment.arguments?.getString("type")
                val fromDashboard = fragment.arguments?.getBoolean("fromDashboard", false) == true
                DashboardDestination.Team(fromDashboard = fromDashboard, type = type, extras = fragment.arguments)
            }
            is org.ole.planet.myplanet.ui.community.CommunityTabFragment -> DashboardDestination.Community
            is org.ole.planet.myplanet.ui.dashboard.SurveyFragment -> DashboardDestination.Surveys
            is org.ole.planet.myplanet.ui.feedback.FeedbackFragment -> DashboardDestination.Feedback
            is org.ole.planet.myplanet.ui.feedback.FeedbackListFragment -> DashboardDestination.FeedbackList
            is org.ole.planet.myplanet.ui.dashboard.notification.NotificationsFragment -> DashboardDestination.Notifications
            is org.ole.planet.myplanet.ui.chat.ChatHistoryListFragment -> DashboardDestination.ChatHistory
            is org.ole.planet.myplanet.ui.dashboard.DisclaimerFragment -> DashboardDestination.Disclaimer
            is org.ole.planet.myplanet.ui.dashboard.AboutFragment -> DashboardDestination.About
            is org.ole.planet.myplanet.ui.dashboard.MyActivityFragment -> DashboardDestination.MyActivity
            is org.ole.planet.myplanet.ui.mylife.LifeFragment -> DashboardDestination.Life
            is org.ole.planet.myplanet.ui.dashboard.InactiveDashboardFragment -> DashboardDestination.Inactive
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
