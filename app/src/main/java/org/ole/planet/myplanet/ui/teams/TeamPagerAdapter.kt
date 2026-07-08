package org.ole.planet.myplanet.ui.teams

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.OnMemberChangeListener
import org.ole.planet.myplanet.callback.OnTeamUpdateListener
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ApplicantsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.CoursesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.DocumentsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.JoinRequestsPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.MembersPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.ResourcesPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.SurveyPage
import org.ole.planet.myplanet.ui.teams.TeamPageConfig.TeamPage
import org.ole.planet.myplanet.ui.teams.courses.TeamCoursesFragment
import org.ole.planet.myplanet.ui.teams.members.MembersFragment
import org.ole.planet.myplanet.ui.teams.members.RequestsFragment
import org.ole.planet.myplanet.ui.teams.resources.TeamResourcesFragment

class TeamPagerAdapter(
    private val parentFragment: Fragment,
    private var pages: List<TeamPageConfig>,
    private val teamId: String?,
    private val onMemberChangeListener: OnMemberChangeListener,
    private val teamUpdateListener: OnTeamUpdateListener
) : FragmentStateAdapter(parentFragment) {

    fun updatePages(newPages: List<TeamPageConfig>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = pages.size
            override fun getNewListSize(): Int = newPages.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return pages[oldItemPosition].id == newPages[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return pages[oldItemPosition] == newPages[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        pages = newPages.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = pages.size

    fun getPageTitle(position: Int): CharSequence =
        parentFragment.getString(pages[position].titleRes)

    fun getPageConfig(position: Int): TeamPageConfig? = pages.getOrNull(position)

    override fun getItemId(position: Int) = pages[position].id.hashCode().toLong()

    override fun containsItem(itemId: Long) = pages.any { it.id.hashCode().toLong() == itemId }

    override fun createFragment(position: Int): Fragment {
        val page = pages[position]
        val fragment = page.createFragment()

        when (page) {
            TeamPage, MembersPage -> if (fragment is MembersFragment) {
                fragment.setOnMemberChangeListener(onMemberChangeListener)
            }
            ApplicantsPage, JoinRequestsPage -> if (fragment is RequestsFragment) {
                fragment.setOnMemberChangeListener(onMemberChangeListener)
            }
            CoursesPage -> if (fragment is TeamCoursesFragment) {
                MainApplication.listener = fragment
            }
            DocumentsPage, ResourcesPage -> if (fragment is TeamResourcesFragment) {
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

        if (fragment is PlanFragment) {
            fragment.setTeamUpdateListener(teamUpdateListener)
        }

        val args = fragment.arguments ?: Bundle().also { fragment.arguments = it }
        if (!args.containsKey("id")) {
            args.putString("id", teamId)
        }

        args.putString("fragmentType", page.id)
        args.putInt("fragmentPosition", position)

        return fragment
    }
}
