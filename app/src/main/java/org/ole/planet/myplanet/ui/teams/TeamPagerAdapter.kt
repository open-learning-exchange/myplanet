package org.ole.planet.myplanet.ui.teams

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
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
    initialPages: List<TeamPageConfig>,
    private val teamId: String?,
    private val onMemberChangeListener: OnMemberChangeListener,
    private val teamUpdateListener: OnTeamUpdateListener
) : FragmentStateAdapter(parentFragment) {
    private val itemIds = mutableMapOf<String, Long>()
    private var nextId = 1L

    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<TeamPageConfig>() {
        override fun areItemsTheSame(oldItem: TeamPageConfig, newItem: TeamPageConfig): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TeamPageConfig, newItem: TeamPageConfig): Boolean {
            return oldItem == newItem
        }
    })

    init {
        initialPages.forEach { page ->
            if (!itemIds.containsKey(page.id)) {
                itemIds[page.id] = nextId++
            }
        }
        differ.submitList(initialPages.toList())
    }

    fun updatePages(newPages: List<TeamPageConfig>) {
        newPages.forEach { page ->
            if (!itemIds.containsKey(page.id)) {
                itemIds[page.id] = nextId++
            }
        }
        differ.submitList(newPages.toList())
    }

    override fun getItemCount(): Int = differ.currentList.size

    fun getPageTitle(position: Int): CharSequence =
        parentFragment.getString(differ.currentList[position].titleRes)

    fun getPageConfig(position: Int): TeamPageConfig? = differ.currentList.getOrNull(position)

    override fun getItemId(position: Int): Long {
        return itemIds[differ.currentList[position].id] ?: RecyclerView.NO_ID
    }

    override fun containsItem(itemId: Long): Boolean {
        return differ.currentList.any { itemIds[it.id] == itemId }
    }

    override fun createFragment(position: Int): Fragment {
        val page = differ.currentList[position]
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
