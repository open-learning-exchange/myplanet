package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ApplicantsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.CalendarPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ChatPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.CoursesPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.DocumentsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.FinancesPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.MembersPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.MissionPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.PlanPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ReportsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ResourcesPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.SurveyPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.TasksPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.TeamPage
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberFragment
import org.ole.planet.myplanet.ui.team.teamMember.MembersFragment
import org.ole.planet.myplanet.ui.team.teamResource.TeamResourceFragment

class TeamPagerAdapter(
    private val fm: FragmentActivity,
    private val pages: List<TeamPageConfig>,
    private val teamId: String?,
    private val memberChangeListener: MemberChangeListener
) : FragmentStateAdapter(fm) {

    override fun getItemCount(): Int = pages.size

    fun getPageTitle(position: Int): CharSequence =
        fm.getString(pages[position].titleRes)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun containsItem(itemId: Long): Boolean =
        itemId in 0 until pages.size

    override fun createFragment(position: Int): Fragment {
        val page = pages[position]
        val fragment = page.createFragment()

        when (page) {
            TeamPage -> if (fragment is JoinedMemberFragment) {
                fragment.setMemberChangeListener(memberChangeListener)
            }
            MembersPage -> if (fragment is JoinedMemberFragment) {
                fragment.setMemberChangeListener(memberChangeListener)
            }
            ApplicantsPage, JoinRequestsPage -> if (fragment is MembersFragment) {
                fragment.setMemberChangeListener(memberChangeListener)
            }
            DocumentsPage, ResourcesPage -> if (fragment is TeamResourceFragment) {
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

        if (fragment.arguments == null) {
            fragment.arguments = Bundle().apply { putString("id", teamId) }
        } else if (!fragment.arguments!!.containsKey("id")) {
            fragment.arguments!!.putString("id", teamId)
        }

        return fragment
    }
}
