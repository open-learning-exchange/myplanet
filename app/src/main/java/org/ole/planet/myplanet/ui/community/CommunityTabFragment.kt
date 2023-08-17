package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.Date

class CommunityTabFragment : Fragment() {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return fragmentTeamDetailBinding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var settings =
            requireActivity().getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
        var sParentcode = settings.getString("parentCode", "")

        var user = UserProfileDbHandler(requireActivity()).userModel
        fragmentTeamDetailBinding.viewPager.adapter =
            CommunityPagerAdapter(childFragmentManager, user.planetCode + "@" + sParentcode, false)
        fragmentTeamDetailBinding.tabLayout.setupWithViewPager(fragmentTeamDetailBinding.viewPager)
        fragmentTeamDetailBinding.title.text = user.planetCode
        fragmentTeamDetailBinding.subtitle.text = TimeUtils.getFormatedDateWithTime(Date().time)
        fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
        fragmentTeamDetailBinding.tabLayout.setupWithViewPager(fragmentTeamDetailBinding.viewPager)
    }
}