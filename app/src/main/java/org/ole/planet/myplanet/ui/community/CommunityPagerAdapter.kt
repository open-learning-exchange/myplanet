package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.ole.planet.myplanet.ui.enterprises.EnterpriseCalendarFragment
import org.ole.planet.myplanet.ui.enterprises.FinanceFragment

class CommunityPagerAdapter(fm: FragmentManager, val id: String) : FragmentStatePagerAdapter(fm) {
    var titles = arrayOf("News", "Community Leaders", "Finances", "Calendar")
    override fun getItem(position: Int): Fragment {
        if (position == 0) {
            return CommunityFragment()
        }else if( position == 1){
            return LeadersFragment()
        }else if(position ==2){
            return FinanceFragment();
        }
        else {
            var f = EnterpriseCalendarFragment()
            val b = Bundle()
            b.putString("id", id)
            f.arguments = b
            return f
        }
    }

    override fun getCount(): Int {
        return titles.size
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return titles[position]
    }
}