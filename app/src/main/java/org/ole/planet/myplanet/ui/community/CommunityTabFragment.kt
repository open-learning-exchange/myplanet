package org.ole.planet.myplanet.ui.community


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.fragment_team_detail.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.ui.team.TeamPagerAdapter

/**
 * A simple [Fragment] subclass.
 */
class CommunityTabFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_team_detail, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var user = UserProfileDbHandler(activity!!).userModel
        view_pager.adapter = CommunityPagerAdapter(childFragmentManager, user.planetCode + "@" + user.parentCode)
        tab_layout.setupWithViewPager(view_pager)
    }
}
