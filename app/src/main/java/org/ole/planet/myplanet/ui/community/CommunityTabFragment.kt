package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class CommunityTabFragment : Fragment() {
    private var _binding: FragmentTeamDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sParentcode = settings.getString("parentCode", "")
        val communityName = settings.getString("communityName", "")
        val user = UserProfileDbHandler(requireActivity()).userModel
        binding.viewPager2.adapter = CommunityPagerAdapter(requireActivity(), user?.planetCode + "@" + sParentcode, false, settings)
        TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
            tab.text = (binding.viewPager2.adapter as CommunityPagerAdapter).getPageTitle(position)
        }.attach()
        binding.title.text = if (user?.planetCode == "") communityName else user?.planetCode
        binding.subtitle.text = settings.getString("planetType", "")
        binding.llActionButtons.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
