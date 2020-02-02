package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.ole.planet.myplanet.ui.enterprises.EnterpriseCalendarFragment
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment
import org.ole.planet.myplanet.ui.news.NewsFragment

class CommunityPagerAdapter(fm: FragmentManager, val id: String, var fromLogin: Boolean) : FragmentStatePagerAdapter(fm) {
    var titles = arrayOf("News", "Community Leaders", "Calendar", "Services","Finances")
    var titles_login = arrayOf("News", "Community Leaders", "Calendar")
    override fun getItem(position: Int): Fragment {
        var fragment: Fragment;
        if (position == 0) {
            fragment = NewsFragment()
        } else if (position == 1) {
            fragment = LeadersFragment()
        }else if (position == 3) {
            fragment = ServicesFragment()
        } else if (position == 2) {
            fragment = EnterpriseCalendarFragment()
        } else {
            fragment =  FinanceFragment()
        }
        val b = Bundle()
        b.putString("id", id)
        b.putBoolean("fromLogin", fromLogin)
        fragment.arguments = b
        return fragment;
    }

    override fun getCount(): Int {
        return if (fromLogin) titles_login.size else titles.size
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return titles[position]
    }
}