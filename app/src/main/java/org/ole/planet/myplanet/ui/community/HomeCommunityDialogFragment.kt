package org.ole.planet.myplanet.ui.community

import android.os.BaseBundle
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_team_detail.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.service.UserProfileDbHandler
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
        var user = UserProfileDbHandler(activity!!).userModel
        view_pager.adapter = CommunityPagerAdapter(childFragmentManager, user.planetCode + "@" + user.parentCode,true)
        toolbar.title = user.planetCode
        toolbar.subtitle = TimeUtils.getFormatedDateWithTime(Date().time)
        tab_layout.setupWithViewPager(view_pager)
    }

}