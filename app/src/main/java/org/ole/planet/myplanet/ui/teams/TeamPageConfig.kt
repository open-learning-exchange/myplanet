package org.ole.planet.myplanet.ui.teams

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.enterprises.EnterprisesFinancesFragment
import org.ole.planet.myplanet.ui.enterprises.EnterprisesReportsFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.teams.courses.TeamCoursesFragment
import org.ole.planet.myplanet.ui.teams.discussion.DiscussionListFragment
import org.ole.planet.myplanet.ui.teams.members.MembersFragment
import org.ole.planet.myplanet.ui.teams.members.RequestsFragment
import org.ole.planet.myplanet.ui.teams.resources.TeamResourcesFragment
import org.ole.planet.myplanet.ui.teams.tasks.TeamTaskFragment

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
        override fun createFragment() = MembersFragment()
    }

    object MembersPage : TeamPageConfig("MEMBERS", R.string.members) {
        override fun createFragment() = MembersFragment()
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
        override fun createFragment() = TeamCoursesFragment()
    }

    object FinancesPage : TeamPageConfig("FINANCES", R.string.finances) {
        override fun createFragment() = EnterprisesFinancesFragment()
    }

    object ReportsPage : TeamPageConfig("REPORTS", R.string.reports) {
        override fun createFragment() = EnterprisesReportsFragment()
    }

    object DocumentsPage : TeamPageConfig("DOCUMENTS", R.string.documents) {
        override fun createFragment() = TeamResourcesFragment()
    }

    object ResourcesPage : TeamPageConfig("RESOURCES", R.string.resources) {
        override fun createFragment() = TeamResourcesFragment()
    }

    object ApplicantsPage : TeamPageConfig("APPLICANTS", R.string.applicants) {
        override fun createFragment() = RequestsFragment()
    }

    object JoinRequestsPage : TeamPageConfig("JOIN_REQUESTS", R.string.join_requests) {
        override fun createFragment() = RequestsFragment()
    }
}
