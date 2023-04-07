package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.ole.planet.myplanet.ui.enterprises.EnterpriseCalendarFragment
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment
import org.ole.planet.myplanet.ui.news.NewsFragment

class CommunityPagerAdapter(fm: FragmentManager, val id: String, private var fromLogin: Boolean) : FragmentStatePagerAdapter(fm) {
    private var titles = arrayOf("News", "Community Leaders", "Calendar", "Services","Finances")
    private var titlesLogin = arrayOf("News", "Community Leaders", "Calendar")
    override fun getItem(position: Int): Fragment {
        val fragment: Fragment = when (position) {
            0 -> {
                NewsFragment()
            }
            1 -> {
                LeadersFragment()
            }
            3 -> {
                ServicesFragment()
            }
            2 -> {
                EnterpriseCalendarFragment()
            }
            else -> {
                FinanceFragment()
            }
        }
        val b = Bundle()
        b.putString("id", id)
        b.putBoolean("fromLogin", fromLogin)
        fragment.arguments = b
        return fragment
    }

    override fun getCount(): Int {
        return if (fromLogin) titlesLogin.size else titles.size
    }

    override fun getPageTitle(position: Int): CharSequence {
        return titles[position]
    }
}