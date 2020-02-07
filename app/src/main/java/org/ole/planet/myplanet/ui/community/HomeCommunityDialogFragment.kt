package org.ole.planet.myplanet.ui.community

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_team_detail.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.*

class HomeCommunityDialogFragment : BottomSheetDialogFragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_team_detail, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        initCommunityTab()
    }

    private fun initCommunityTab() {
        ll_action_buttons.visibility = View.GONE
        var settings = activity!!.getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE)
        var sPlanetcode = settings.getString("planetCode", "")
        var sParentcode = settings.getString("parentCode", "")
        view_pager.adapter = CommunityPagerAdapter(childFragmentManager, sPlanetcode + "@" + sParentcode, true)
        title.text = sPlanetcode
        title.setTextColor(resources.getColor(R.color.md_black_1000))
        subtitle.setTextColor(resources.getColor(R.color.md_black_1000))
        subtitle.text = TimeUtils.getFormatedDateWithTime(Date().time)
        tab_layout.setupWithViewPager(view_pager)
    }
}