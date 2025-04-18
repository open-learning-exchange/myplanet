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

    private val pages = mutableListOf<Int>()

    init {
        if (isInMyTeam || team?.isPublic == true) {
            pages += R.string.chat
            pages += if (isEnterprise) R.string.mission else R.string.plan
            pages += if (isEnterprise) R.string.team else R.string.members
            pages += R.string.tasks
            pages += R.string.calendar
            pages += R.string.survey
            pages += if (isEnterprise) R.string.finances else R.string.courses
            if (isEnterprise) pages += R.string.reports
            pages += if (isEnterprise) R.string.documents else R.string.resources
            pages += if (isEnterprise) R.string.applicants else R.string.join_requests
        } else {
            pages += if (isEnterprise) R.string.mission else R.string.plan
            pages += if (isEnterprise) R.string.team else R.string.members
        }
    }

    override fun getItemCount() = pages.size
    fun getPageTitle(position: Int) = context.getString(pages[position])

    override fun getItemId(position: Int): Long =
        pages[position].toLong()

    override fun containsItem(itemId: Long): Boolean =
        pages.contains(itemId.toInt())

    override fun createFragment(position: Int): Fragment {
        val id = pages[position]
        println(position)
        val fragment: Fragment = when (id) {
            R.string.chat -> DiscussionListFragment()
            R.string.plan, R.string.mission -> PlanFragment()
            R.string.members, R.string.team -> JoinedMemberFragment()
            R.string.tasks -> TeamTaskFragment()
            R.string.calendar -> TeamCalendarFragment()
            R.string.survey -> SurveyFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("isTeam", true)
                    putString("teamId", teamId)
                }
            }
            R.string.courses -> TeamCourseFragment()
            R.string.finances -> FinanceFragment()
            R.string.reports -> ReportsFragment()
            R.string.resources, R.string.documents -> TeamResourceFragment().apply { MainApplication.listener = this }
            R.string.join_requests, R.string.applicants -> MembersFragment()
            else -> throw IllegalArgumentException("Invalid fragment type for position: $position")
        }
        if (fragment.arguments == null) {
            fragment.arguments = Bundle().apply {
                putString("id", teamId)
            }
        }
        return fragment
    }
}
