package org.ole.planet.myplanet.ui.team

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.enterprises.EnterpriseCalendarFragment
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment
import org.ole.planet.myplanet.ui.team.teamCourse.TeamCourseFragment
import org.ole.planet.myplanet.ui.team.teamDiscussion.DiscussionListFragment
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberFragment
import org.ole.planet.myplanet.ui.team.teamMember.MembersFragment
import org.ole.planet.myplanet.ui.team.teamResource.TeamResourceFragment
import org.ole.planet.myplanet.ui.team.teamTask.TeamTaskFragment

class TeamPagerAdapter(fm: FragmentManager?, team: RealmMyTeam, private val isInMyTeam: Boolean) : FragmentStatePagerAdapter(fm!!) {
    private val teamId: String?
    private val list: MutableList<String>
    private val isEnterprise: Boolean

    init {
        teamId = team._id
        isEnterprise = TextUtils.equals(team.type, "enterprise")
        list = ArrayList()
        list.add(MainApplication.context.getString(if (isEnterprise) R.string.mission else R.string.plan))
        list.add(MainApplication.context.getString(if (isEnterprise) R.string.team else R.string.joined_members))
        if (isInMyTeam || team.isPublic) {
            list.add(MainApplication.context.getString(R.string.chat))
            list.add(MainApplication.context.getString(R.string.tasks))
            list.add(MainApplication.context.getString(R.string.calendar))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.finances else R.string.courses))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.documents else R.string.resources))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.applicants else R.string.join_requests))
            list.removeAt(0)
            list.removeAt(0)
            list.add(1, MainApplication.context.getString(if (isEnterprise) R.string.mission else R.string.plan))
            list.add(2, MainApplication.context.getString(if (isEnterprise) R.string.team else R.string.members))
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        return list[position]
    }

    override fun getItem(position: Int): Fragment {
        val f: Fragment? = if (!isInMyTeam) {
            if (position == 0) PlanFragment() else {
                JoinedMemberFragment()
            }
        } else {
            checkCondition(position)
        }
        val b = Bundle()
        b.putString("id", teamId)
        f!!.arguments = b
        return f
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkCondition(position: Int): Fragment? {
        var f: Fragment? = null
        when (position) {
            0 -> f = DiscussionListFragment()
            1 -> f = PlanFragment()
            2 -> f = JoinedMemberFragment()
            3 -> f = TeamTaskFragment()
            4 -> f = EnterpriseCalendarFragment()
            5 -> f = fragment //finances
            6 -> {
                f = TeamResourceFragment()
                MainApplication.listener = f
            }

            7 -> f = MembersFragment()
        }
        return f
    }

    private val fragment: Fragment
        get() = if (isEnterprise) FinanceFragment() else TeamCourseFragment()

    override fun getCount(): Int {
        return list.size
    }
}
