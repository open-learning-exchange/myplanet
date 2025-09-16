package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ApplicantsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.DocumentsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.MembersPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.ResourcesPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.SurveyPage
import org.ole.planet.myplanet.ui.team.TeamPageConfig.TeamPage
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberFragment
import org.ole.planet.myplanet.ui.team.teamMember.MembersFragment
import org.ole.planet.myplanet.ui.team.teamResource.TeamResourceFragment

class TeamPagerAdapter(
    private val host: Fragment,
    private val pages: List<TeamPageConfig>,
    private val teamId: String?,
    private val memberChangeListener: MemberChangeListener
) : FragmentStateAdapter(host) {

    override fun getItemCount(): Int = pages.size

    fun getPageTitle(position: Int): CharSequence =
        host.getString(pages[position].titleRes)

    override fun getItemId(position: Int): Long = pages[position].id.hashCode().toLong()

    override fun containsItem(itemId: Long): Boolean =
        pages.any { it.id.hashCode().toLong() == itemId }

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

        val args = fragment.arguments ?: Bundle().also { fragment.arguments = it }
        if (!args.containsKey("id")) {
            args.putString("id", teamId)
        }

        return fragment
    }
}
