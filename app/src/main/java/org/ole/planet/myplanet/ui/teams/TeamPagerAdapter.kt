package org.ole.planet.myplanet.ui.teams

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
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
    private val fm: FragmentActivity,
    private val pages: List<TeamPageConfig>,
    private val teamId: String?,
    private val memberChangeListener: MemberChangeListener,
    private val teamUpdateListener: TeamUpdateListener
) : FragmentStateAdapter(fm) {

    override fun getItemCount(): Int = pages.size

    fun getPageTitle(position: Int): CharSequence =
        fm.getString(pages[position].titleRes)

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
            CoursesPage -> if (fragment is TeamCoursesFragment) {
                android.util.Log.d("TeamPagerAdapter", "Setting MainApplication.listener to TeamCoursesFragment")
                MainApplication.listener = fragment
            }
            DocumentsPage, ResourcesPage -> if (fragment is TeamResourcesFragment) {
                android.util.Log.d("TeamPagerAdapter", "Setting MainApplication.listener to TeamResourcesFragment")
                MainApplication.listener = fragment
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
}
