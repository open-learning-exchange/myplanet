package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class CommunityTabFragment : Fragment() {
    private var _binding: FragmentTeamDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CommunityTabViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectWhenStarted(viewModel.state) { state ->
            if (state == null) return@collectWhenStarted
            binding.viewPager2.adapter = CommunityPagerAdapter(requireActivity(), "${state.planetCode}@${state.parentCode}", false, state.planetType)
            TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
                tab.text = (binding.viewPager2.adapter as CommunityPagerAdapter).getPageTitle(position)
            }.attach()
            binding.title.text = if (state.planetCode.isEmpty()) state.communityName else state.planetCode
            binding.subtitle.text = state.planetType
            binding.llActionButtons.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
