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
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class CommunityTabFragment : Fragment() {
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
        var settings =
            requireActivity().getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
        var sParentcode = settings.getString("parentCode", "")

        var user = UserProfileDbHandler(requireActivity()).userModel
        binding.viewPager.adapter =
            CommunityPagerAdapter(childFragmentManager, user.planetCode + "@" + sParentcode, false)
        binding.tabLayout.setupWithViewPager(binding.viewPager)
        binding.title.text = user.planetCode
        binding.subtitle.text = TimeUtils.getFormatedDateWithTime(Date().time)
        binding.llActionButtons.visibility = View.GONE
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }
}
