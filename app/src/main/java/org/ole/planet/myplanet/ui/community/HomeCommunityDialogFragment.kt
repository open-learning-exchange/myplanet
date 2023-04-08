package org.ole.planet.myplanet.ui.community

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.TimeUtils
import java.util.*

class HomeCommunityDialogFragment : BottomSheetDialogFragment() {
    lateinit var binding: FragmentTeamDetailBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        initCommunityTab()
    }

    private fun initCommunityTab() {
        binding.llActionButtons.visibility = View.GONE
        var settings = requireActivity().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE)
        var sPlanetcode = settings.getString("planetCode", "")
        var sParentcode = settings.getString("parentCode", "")
        binding.viewPager.adapter =
            CommunityPagerAdapter(childFragmentManager, sPlanetcode + "@" + sParentcode, true)
        binding.title.text = sPlanetcode
        binding.title.setTextColor(resources.getColor(R.color.md_black_1000))
        binding.subtitle.setTextColor(resources.getColor(R.color.md_black_1000))
        binding.subtitle.text = TimeUtils.getFormatedDateWithTime(Date().time)
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }
}