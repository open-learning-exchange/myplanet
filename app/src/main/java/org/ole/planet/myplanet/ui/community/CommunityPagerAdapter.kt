package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import org.ole.planet.myplanet.ui.enterprises.EnterpriseCalendarFragment

class CommunityPagerAdapter(fm: FragmentManager, val id: String) : FragmentStatePagerAdapter(fm) {
    override fun getItem(position: Int): Fragment {
        if (position == 0) {
            return CommunityFragment()
        } else {
            var f = EnterpriseCalendarFragment()
            val b = Bundle()
            b.putString("id", id)
            f.arguments = b
            return f
        }
    }

    override fun getCount(): Int {
        return 2
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return if (position == 0) {
            "Community"
        } else {
            "Calendar"
        }
    }
}