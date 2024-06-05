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
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment
import org.ole.planet.myplanet.ui.enterprises.ReportsFragment
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
        list.add(MainApplication.context.getString(if (isEnterprise) R.string.mission else R.string.plan))
        list.add(MainApplication.context.getString(if (isEnterprise) R.string.team else R.string.joined_members))
        if (isInMyTeam || team?.isPublic == true) {
            list.add(MainApplication.context.getString(R.string.chat))
            list.add(MainApplication.context.getString(R.string.tasks))
            list.add(MainApplication.context.getString(R.string.calendar))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.finances else R.string.courses))
            if (isEnterprise) {
                list.add("Reports")
            }
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.documents else R.string.resources))
            list.add(MainApplication.context.getString(if (isEnterprise) R.string.applicants else R.string.join_requests))
            list.removeAt(0)
            list.removeAt(0)
            list.add(1, MainApplication.context.getString(if (isEnterprise) R.string.mission else R.string.plan))
            list.add(2, MainApplication.context.getString(if (isEnterprise) R.string.team else R.string.members))
        }
    }

    fun getPageTitle(position: Int): CharSequence {
        return list[position]
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun createFragment(position: Int): Fragment {
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
                if (isEnterprise) {
                    f = ReportsFragment()
                } else {
                    f = TeamResourceFragment()
                    MainApplication.listener = f
                }
            }
            7 -> {
                if (isEnterprise) {
                    f = TeamResourceFragment()
                    MainApplication.listener = f
                } else {
                    f = MembersFragment()
                }
            }
            8 -> {
                if (isEnterprise) {
                    f = MembersFragment()
                }
            }
        }
        return f
    }

    private val fragment: Fragment
        get() = if (isEnterprise) FinanceFragment() else TeamCourseFragment()

    override fun getItemCount(): Int {
        return list.size
    }
}
