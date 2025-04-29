package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
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

class TeamPagerAdapter(private val activity: FragmentActivity, private val team: RealmMyTeam?, private val isInMyTeam: Boolean) : FragmentStateAdapter(activity) {
    private val teamId: String? = team?._id
    private val isEnterprise: Boolean = TextUtils.equals(team?.type, "enterprise")

    fun getPageTitle(position: Int): CharSequence {
        val index = getPositionIndex(position)
        return when (index) {
            "chat" -> activity.getString(R.string.chat)
            "plan" -> activity.getString(if (isEnterprise) R.string.mission else R.string.plan)
            "members" -> activity.getString(if (isEnterprise) R.string.team else R.string.members)
            "tasks" -> activity.getString(R.string.tasks)
            "calendar" -> activity.getString(R.string.calendar)
            "survey" -> activity.getString(R.string.survey)
            "courses" -> activity.getString(if (isEnterprise) R.string.finances else R.string.courses)
            "reports" -> activity.getString(R.string.reports)
            "resources" -> activity.getString(if (isEnterprise) R.string.documents else R.string.resources)
            "requests" -> activity.getString(if (isEnterprise) R.string.applicants else R.string.join_requests)
            else -> ""
        }
    }

    private fun getPositionIndex(position: Int): String {
        if (isInMyTeam || team?.isPublic == true) {
            return when (position) {
                0 -> "chat"
                1 -> "plan"
                2 -> "members"
                3 -> "tasks"
                4 -> "calendar"
                5 -> "survey"
                6 -> if (isEnterprise) "courses" else "finances"
                7 -> if (isEnterprise) "reports" else "resources"
                8 -> if (isEnterprise) "resources" else "requests"
                9 -> if (isEnterprise) "requests" else ""
                else -> ""
            }
        } else {
            return when (position) {
                0 -> "plan"
                1 -> "members"
                else -> ""
            }
        }
    }

    override fun createFragment(position: Int): Fragment {
        val index = getPositionIndex(position)
        val fragment: Fragment = when (index) {
            "chat" -> DiscussionListFragment()
            "plan" -> PlanFragment()
            "members" -> JoinedMemberFragment()
            "tasks" -> TeamTaskFragment()
            "calendar" -> TeamCalendarFragment()
            "survey" -> SurveyFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("isTeam", true)
                    putString("teamId", teamId)
                }
            }
            "courses" -> TeamCourseFragment()
            "finances" -> FinanceFragment()
            "reports" -> ReportsFragment()
            "resources" -> TeamResourceFragment()
            "requests" -> MembersFragment()
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
        return if (isInMyTeam || team?.isPublic == true) {
            if (isEnterprise) 10 else 9
        } else {
            2
        }
    }
}