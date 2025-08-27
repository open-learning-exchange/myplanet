package org.ole.planet.myplanet.ui.team

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment
import org.ole.planet.myplanet.ui.enterprises.ReportsFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.team.teamCourse.TeamCourseFragment
import org.ole.planet.myplanet.ui.team.teamDiscussion.DiscussionListFragment
import org.ole.planet.myplanet.ui.team.teamMember.JoinedMemberFragment
import org.ole.planet.myplanet.ui.team.teamMember.MembersFragment
import org.ole.planet.myplanet.ui.team.teamResource.TeamResourceFragment
import org.ole.planet.myplanet.ui.team.teamTask.TeamTaskFragment

sealed class TeamPageConfig(val id: String, @StringRes val titleRes: Int) {
    abstract fun createFragment(): Fragment

    object ChatPage : TeamPageConfig("CHAT", R.string.chat) {
        override fun createFragment() = DiscussionListFragment()
    }

    object PlanPage : TeamPageConfig("PLAN", R.string.plan) {
        override fun createFragment() = PlanFragment()
    }

    object MissionPage : TeamPageConfig("MISSION", R.string.mission) {
        override fun createFragment() = PlanFragment()
    }

    object TeamPage : TeamPageConfig("TEAM", R.string.team) {
        override fun createFragment() = JoinedMemberFragment()
    }

    object MembersPage : TeamPageConfig("MEMBERS", R.string.members) {
        override fun createFragment() = JoinedMemberFragment()
    }

    object TasksPage : TeamPageConfig("TASKS", R.string.tasks) {
        override fun createFragment() = TeamTaskFragment()
    }

    object CalendarPage : TeamPageConfig("CALENDAR", R.string.calendar) {
        override fun createFragment() = TeamCalendarFragment()
    }

    object SurveyPage : TeamPageConfig("SURVEY", R.string.survey) {
        override fun createFragment() = SurveyFragment()
    }

    object CoursesPage : TeamPageConfig("COURSES", R.string.courses) {
        override fun createFragment() = TeamCourseFragment()
    }

    object FinancesPage : TeamPageConfig("FINANCES", R.string.finances) {
        override fun createFragment() = FinanceFragment()
    }

    object ReportsPage : TeamPageConfig("REPORTS", R.string.reports) {
        override fun createFragment() = ReportsFragment()
    }

    object DocumentsPage : TeamPageConfig("DOCUMENTS", R.string.documents) {
        override fun createFragment() = TeamResourceFragment()
    }

    object ResourcesPage : TeamPageConfig("RESOURCES", R.string.resources) {
        override fun createFragment() = TeamResourceFragment()
    }

    object ApplicantsPage : TeamPageConfig("APPLICANTS", R.string.applicants) {
        override fun createFragment() = MembersFragment()
    }

    object JoinRequestsPage : TeamPageConfig("JOIN_REQUESTS", R.string.join_requests) {
        override fun createFragment() = MembersFragment()
    }
}
