package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.team.TeamCalendarFragment
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment
import org.ole.planet.myplanet.ui.enterprises.ReportsFragment
import org.ole.planet.myplanet.ui.news.NewsFragment

class CommunityPagerAdapter(private val fm: FragmentActivity, private val id: String, private var fromLogin: Boolean) : FragmentStateAdapter(fm) {
    override fun createFragment(position: Int): Fragment {
        val fragment: Fragment = when (position) {
            0 -> {
                NewsFragment()
            }
            1 -> {
                LeadersFragment()
            }
            2 -> {
                TeamCalendarFragment()
            }
            3 -> {
                ServicesFragment()
            }
            4 -> {
                FinanceFragment()
            }
            else -> {
                ReportsFragment()
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
        return when (position) {
            0 -> fm.getString(R.string.our_voices)
            1 -> fm.getString(R.string.community_leaders)
            2 -> fm.getString(R.string.calendar)
            3 -> if (!fromLogin) fm.getString(R.string.services) else ""
            4 -> if (!fromLogin) fm.getString(R.string.finances) else ""
            5 -> if (!fromLogin) fm.getString(R.string.reports) else ""
            else -> ""
        }
    }
}
