package org.ole.planet.myplanet.ui.teams

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.callback.TeamUpdateListener
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ApplicantsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.CoursesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.DocumentsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.MembersPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ResourcesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.SurveyPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TeamPage
import org.ole.planet.myplanet.ui.teams.courses.TeamCoursesFragment
import org.ole.planet.myplanet.ui.teams.member.MemberFragment
import org.ole.planet.myplanet.ui.teams.member.MembersFragment
import org.ole.planet.myplanet.ui.teams.resources.TeamResourcesFragment

class TeamPagerAdapter(
    private val parentFragment: Fragment,
    private val pages: List<TeamPageConfig>,
    private val teamId: String?,
    private val memberChangeListener: MemberChangeListener,
    private val teamUpdateListener: TeamUpdateListener
) : FragmentStateAdapter(parentFragment) {

    override fun getItemCount(): Int = pages.size

    fun getPageTitle(position: Int): CharSequence =
        parentFragment.getString(pages[position].titleRes)

    override fun getItemId(position: Int): Long {
        val page = pages.getOrNull(position)
        val pageId = page?.id?.hashCode()?.toLong() ?: position.toLong()
        return pageId
    }

    override fun containsItem(itemId: Long): Boolean {
        val contains = pages.any { it.id.hashCode().toLong() == itemId }
        return contains
    }

    override fun createFragment(position: Int): Fragment {
        val page = pages[position]
        val fragment = page.createFragment()

        when (page) {
            TeamPage -> if (fragment is MemberFragment) {
                fragment.setMemberChangeListener(memberChangeListener)
            }
            MembersPage -> if (fragment is MemberFragment) {
                fragment.setMemberChangeListener(memberChangeListener)
            }
            ApplicantsPage, JoinRequestsPage -> if (fragment is MembersFragment) {
                fragment.setMemberChangeListener(memberChangeListener)
            }
            SurveyPage -> {
                fragment.arguments = (fragment.arguments ?: Bundle()).apply {
                    putBoolean("isTeam", true)
                    putString("teamId", teamId)
                }
            }
            else -> {}
        }

        if (fragment is PlanFragment) {
            fragment.setTeamUpdateListener(teamUpdateListener)
        }

        val args = fragment.arguments ?: Bundle().also { fragment.arguments = it }
        if (!args.containsKey("id")) {
            args.putString("id", teamId)
        }

        args.putString("fragmentType", page.id)
        args.putInt("fragmentPosition", position)

        return fragment
    }

    fun updateListenerForCurrentPage(position: Int) {
        val page = pages.getOrNull(position) ?: return

        // Find the fragment for this position using the parent fragment's childFragmentManager
        val fragmentTag = "f$position"
        val fragment = parentFragment.childFragmentManager.findFragmentByTag(fragmentTag)

        when (page) {
            CoursesPage -> if (fragment is TeamCoursesFragment) {
                MainApplication.listener = fragment
            }
            DocumentsPage, ResourcesPage -> if (fragment is TeamResourcesFragment) {
                MainApplication.listener = fragment
            }
            else -> {
                // For other pages, clear the listener if it was pointing to a resource/course fragment
                if (MainApplication.listener is TeamCoursesFragment || MainApplication.listener is TeamResourcesFragment) {
                    MainApplication.listener = null
                }
            }
        }
    }
}
