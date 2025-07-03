package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment
import org.ole.planet.myplanet.ui.enterprises.ReportsFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.team.teamCourse.TeamCourseFragment
import org.ole.planet.myplanet.ui.team.teamDiscussion.DiscussionListFragment
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberFragment
import org.ole.planet.myplanet.ui.team.teamMember.MembersFragment
import org.ole.planet.myplanet.ui.team.teamResource.TeamResourceFragment
import org.ole.planet.myplanet.ui.team.teamTask.TeamTaskFragment

class TeamPagerAdapter(
    private val fm: FragmentActivity,
    team: RealmMyTeam?,
    isInMyTeam: Boolean,
    private val memberChangeListener: MemberChangeListener
) : FragmentStateAdapter(fm) {
    private val teamId = team?._id
    private val isEnterprise = team?.type == "enterprise"

    private val pages = mutableListOf<TeamPage>()
    private val fragments: List<Fragment>

    init {
        if (isInMyTeam || team?.isPublic == true) {
            pages += TeamPage.CHAT
            pages += if (isEnterprise) TeamPage.MISSION else TeamPage.PLAN
            pages += if (isEnterprise) TeamPage.TEAM else TeamPage.MEMBERS
            pages += TeamPage.TASKS
            pages += TeamPage.CALENDAR
            pages += TeamPage.SURVEY
            pages += if (isEnterprise) TeamPage.FINANCES else TeamPage.COURSES
            if (isEnterprise) pages += TeamPage.REPORTS
            pages += if (isEnterprise) TeamPage.DOCUMENTS else TeamPage.RESOURCES
            pages += if (isEnterprise) TeamPage.APPLICANTS else TeamPage.JOIN_REQUESTS
        } else {
            pages += if (isEnterprise) TeamPage.MISSION else TeamPage.PLAN
            pages += if (isEnterprise) TeamPage.TEAM else TeamPage.MEMBERS
        }

        fragments = pages.map { page ->
            when (page) {
                TeamPage.CHAT -> DiscussionListFragment()
                TeamPage.PLAN, TeamPage.MISSION -> PlanFragment()
                TeamPage.MEMBERS, TeamPage.TEAM -> JoinedMemberFragment().apply { setMemberChangeListener(memberChangeListener) }
                TeamPage.TASKS -> TeamTaskFragment()
                TeamPage.CALENDAR -> TeamCalendarFragment()
                TeamPage.SURVEY -> SurveyFragment().apply {
                    arguments = Bundle().apply {
                        putBoolean("isTeam", true)
                        putString("teamId", teamId)
                    }
                }
                TeamPage.COURSES -> TeamCourseFragment()
                TeamPage.FINANCES -> FinanceFragment()
                TeamPage.REPORTS -> ReportsFragment()
                TeamPage.RESOURCES, TeamPage.DOCUMENTS -> TeamResourceFragment().apply {
                    MainApplication.listener = this
                }
                TeamPage.JOIN_REQUESTS,
                TeamPage.APPLICANTS -> MembersFragment().apply { setMemberChangeListener(memberChangeListener) }
                else -> throw IllegalArgumentException("Unknown page $page")
            }.apply {
                if (arguments == null) {
                    arguments = Bundle().apply { putString("id", teamId) }
                }
            }
        }
    }

    override fun getItemCount(): Int = fragments.size

    fun getPageTitle(position: Int): CharSequence =
        fm.getString(pages[position].getTitleRes())

    fun TeamPage.getTitleRes(): Int =
        R.string::class.java.getDeclaredField(this.name.lowercase()).getInt(null)

    override fun getItemId(position: Int): Long =
        pages[position].ordinal.toLong()

    override fun containsItem(itemId: Long): Boolean =
        pages.any { it.ordinal.toLong() == itemId }

    override fun createFragment(position: Int): Fragment =
        fragments[position]
}
