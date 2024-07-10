package org.ole.planet.myplanet.ui.team

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.enterprises.EnterpriseCalendarFragment
import org.ole.planet.myplanet.ui.team.teamCourse.TeamCourseFragment
import org.ole.planet.myplanet.ui.team.teamDiscussion.DiscussionListFragment
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberFragment
import org.ole.planet.myplanet.ui.team.teamMember.MembersFragment
import org.ole.planet.myplanet.ui.team.teamResource.TeamResourceFragment
import org.ole.planet.myplanet.ui.team.teamTask.TeamTaskFragment

class TeamPagerAdapter(fm: FragmentActivity, team: RealmMyTeam?, private val isInMyTeam: Boolean) : FragmentStateAdapter(fm) {
    private val teamId: String? = team?._id
    private val list: MutableList<String> = ArrayList()
    private val isEnterprise: Boolean = TextUtils.equals(team?.type, "enterprise")

    init {
        list.add(MainApplication.context.getString(R.string.chat))
        list.add(MainApplication.context.getString(if (isEnterprise) R.string.mission else R.string.plan))
        list.add(MainApplication.context.getString(if (isEnterprise) R.string.team else R.string.joined_members))
        if (isInMyTeam || team?.isPublic == true) {
            list.add(MainApplication.context.getString(R.string.tasks))
            list.add(MainApplication.context.getString(R.string.calendar))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.finances else R.string.courses))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.documents else R.string.resources))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.applicants else R.string.join_requests))
        }
    }

    fun getPageTitle(position: Int): CharSequence {
        return list[position]
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun createFragment(position: Int): Fragment {
        if (position < 0 || position >= list.size) {
            throw IllegalArgumentException("Invalid position: $position. List size: ${list.size}")
        }
        val fragment: Fragment = when (list[position]) {
            MainApplication.context.getString(R.string.chat) -> DiscussionListFragment()
            MainApplication.context.getString(R.string.plan), MainApplication.context.getString(R.string.mission) -> PlanFragment()
            MainApplication.context.getString(R.string.joined_members), MainApplication.context.getString(R.string.team) -> JoinedMemberFragment()
            MainApplication.context.getString(R.string.tasks) -> TeamTaskFragment()
            MainApplication.context.getString(R.string.calendar) -> EnterpriseCalendarFragment()
            MainApplication.context.getString(R.string.courses), MainApplication.context.getString(R.string.finances) -> TeamCourseFragment()
            MainApplication.context.getString(R.string.resources), MainApplication.context.getString(R.string.documents) -> TeamResourceFragment().apply { MainApplication.listener = this }
            MainApplication.context.getString(R.string.join_requests), MainApplication.context.getString(R.string.applicants) -> MembersFragment()
            else -> throw IllegalArgumentException("Invalid fragment type for position: $position")
        }

        val bundle = Bundle()
        bundle.putString("id", teamId)
        fragment.arguments = bundle
        return fragment
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
