package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager

@AndroidEntryPoint
class CommunityTabFragment : Fragment() {
    private var _binding: FragmentTeamDetailBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var userSessionManager: UserSessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentCode = sharedPrefManager.getParentCode()
        val communityName = sharedPrefManager.getCommunityName()
        viewLifecycleOwner.lifecycleScope.launch {
            val user = userSessionManager.getUserModel()
            val planetCode = user?.planetCode.orEmpty()
            binding.viewPager2.adapter = CommunityPagerAdapter(requireActivity(), "$planetCode@$parentCode", false, sharedPrefManager)
            TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
                tab.text = (binding.viewPager2.adapter as CommunityPagerAdapter)
                    .getPageTitle(position)
                    .toString()
                    .uppercase(Locale.ENGLISH)
            }.attach()
            binding.title.text = if (planetCode.isEmpty()) communityName else planetCode
            binding.subtitle.text = sharedPrefManager.getRawString("planetType")
            binding.llActionButtons.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
