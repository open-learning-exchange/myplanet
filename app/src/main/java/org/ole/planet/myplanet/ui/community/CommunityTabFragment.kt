package org.ole.planet.myplanet.ui.community


import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.fragment_team_detail.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.ui.team.TeamPagerAdapter
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*

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
        var settings = activity!!.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
        var sParentcode = settings.getString("parentCode", "")

        var user = UserProfileDbHandler(activity!!).userModel
        view_pager.adapter = CommunityPagerAdapter(childFragmentManager, user.planetCode + "@" + sParentcode, false)
        tab_layout.setupWithViewPager(view_pager)
        title.text = user.planetCode
        subtitle.text = TimeUtils.getFormatedDateWithTime(Date().time)
        ll_action_buttons.visibility = View.GONE
        tab_layout.setupWithViewPager(view_pager)
    }
}
