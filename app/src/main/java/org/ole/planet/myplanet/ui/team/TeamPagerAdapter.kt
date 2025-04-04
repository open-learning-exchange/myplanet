package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
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

class TeamPagerAdapter(fm: FragmentActivity, team: RealmMyTeam?, isInMyTeam: Boolean) : FragmentStateAdapter(fm) {
    private val teamId: String? = team?._id
    private val list: MutableList<String> = ArrayList()
    private val isEnterprise: Boolean = TextUtils.equals(team?.type, "enterprise")

    init {
        if (isInMyTeam || team?.isPublic == true) {
            list.add(context.getString(R.string.chat))
            list.add(context.getString(if (isEnterprise) R.string.mission else R.string.plan))
            list.add(context.getString(if (isEnterprise) R.string.team else R.string.members))
            list.add(context.getString(R.string.tasks))
            list.add(context.getString(R.string.calendar))
            list.add(context.getString(R.string.survey))
            list.add(context.getString(if (isEnterprise) R.string.finances else R.string.courses))
            if (isEnterprise) {
                list.add(context.getString(R.string.reports))
            }
            list.add(context.getString(if (isEnterprise) R.string.documents else R.string.resources))
            list.add(context.getString(if (isEnterprise) R.string.applicants else R.string.join_requests))
        } else {
            list.add(context.getString(if (isEnterprise) R.string.mission else R.string.plan))
            list.add(context.getString(if (isEnterprise) R.string.team else R.string.members))
        }
    }

    fun getPageTitle(position: Int): CharSequence {
        return list[position]
    }

    override fun createFragment(position: Int): Fragment {
        if (position < 0 || position >= list.size) {
            throw IllegalArgumentException("Invalid position: $position. List size: ${list.size}")
        }
        val fragment: Fragment = when (list[position]) {
            context.getString(R.string.chat) -> DiscussionListFragment()
            context.getString(R.string.plan), context.getString(R.string.mission) -> PlanFragment()
            context.getString(R.string.members), context.getString(R.string.team) -> JoinedMemberFragment()
            context.getString(R.string.tasks) -> TeamTaskFragment()
            context.getString(R.string.calendar) -> TeamCalendarFragment()
            context.getString(R.string.survey) -> SurveyFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("isTeam", true)
                    putString("teamId", teamId)
                }
            }
            context.getString(R.string.courses) -> TeamCourseFragment()
            context.getString(R.string.finances) -> FinanceFragment()
            context.getString(R.string.reports) -> ReportsFragment()
            context.getString(R.string.resources), context.getString(R.string.documents) -> TeamResourceFragment().apply { MainApplication.listener = this }
            context.getString(R.string.join_requests), context.getString(R.string.applicants) -> MembersFragment()
            else -> throw IllegalArgumentException("Invalid fragment type for position: $position")
        }
        if (fragment.arguments == null) {
            fragment.arguments = Bundle().apply {
                putString("id", teamId)
            }
        }
        return fragment
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
