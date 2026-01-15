package org.ole.planet.myplanet.ui.community

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.service.UserSessionManager

@AndroidEntryPoint
class CommunityTabFragment : Fragment() {
    private var _binding: FragmentTeamDetailBinding? = null
    private val binding get() = _binding!!
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    @Inject
    lateinit var userSessionManager: UserSessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentCode = settings.getString("parentCode", "").orEmpty()
        val communityName = settings.getString("communityName", "").orEmpty()
        val user = userSessionManager.userModel
        val planetCode = user?.planetCode.orEmpty()
        binding.viewPager2.adapter = CommunityPagerAdapter(requireActivity(), "$planetCode@$parentCode", false, settings)
        TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
            tab.text = (binding.viewPager2.adapter as CommunityPagerAdapter).getPageTitle(position)
        }.attach()
        binding.title.text = if (planetCode.isEmpty()) communityName else planetCode
        binding.subtitle.text = settings.getString("planetType", "")
        binding.llActionButtons.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
