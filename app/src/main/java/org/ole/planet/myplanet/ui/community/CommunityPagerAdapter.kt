package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.enterprises.EnterprisesFinancesFragment
import org.ole.planet.myplanet.ui.enterprises.EnterprisesReportsFragment
import org.ole.planet.myplanet.ui.teams.TeamCalendarFragment
import org.ole.planet.myplanet.ui.voices.VoicesFragment

class CommunityPagerAdapter(private val hostFragment: Fragment, private val id: String, private var fromLogin: Boolean, private val planetType: String?) : FragmentStateAdapter(hostFragment) {
    override fun createFragment(position: Int): Fragment {
        val fragment: Fragment = when (position) {
            0 -> {
                VoicesFragment()
            }
            1 -> {
                LeadersFragment()
            }
            2 -> {
                TeamCalendarFragment()
            }
            3 -> {
                CommunityServicesFragment()
            }
            4 -> {
                EnterprisesFinancesFragment()
            }
            else -> {
                EnterprisesReportsFragment()
            }
        }
        val b = Bundle()
        b.putString("id", id)
        b.putBoolean("fromLogin", fromLogin)
        b.putBoolean("fromCommunity", true)
        fragment.arguments = b
        return fragment
    }

    override fun getItemCount(): Int {
        return if (fromLogin) 3 else 6
    }

    fun getPageTitle(position: Int): CharSequence {
        val leaders = if (planetType == "community") {
            hostFragment.getString(R.string.community_leaders)
        } else {
            hostFragment.getString(R.string.nation_leaders)
        }
        return when (position) {
            0 -> hostFragment.getString(R.string.our_voices)
            1 -> leaders
            2 -> hostFragment.getString(R.string.calendar)
            3 -> if (!fromLogin) hostFragment.getString(R.string.services) else ""
            4 -> if (!fromLogin) hostFragment.getString(R.string.finances) else ""
            5 -> if (!fromLogin) hostFragment.getString(R.string.reports) else ""
            else -> ""
        }
    }
}
