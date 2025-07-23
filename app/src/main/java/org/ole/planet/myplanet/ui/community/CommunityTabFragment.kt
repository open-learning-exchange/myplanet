package org.ole.planet.myplanet.ui.community

import android.content.Context
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
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

@AndroidEntryPoint
class CommunityTabFragment : Fragment() {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    @Inject
    @AppPreferences
    lateinit var appPreferences: SharedPreferences
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return fragmentTeamDetailBinding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = appPreferences
        val sParentcode = settings.getString("parentCode", "")
        val communityName = settings.getString("communityName", "")
        val user = UserProfileDbHandler(requireActivity()).userModel
        fragmentTeamDetailBinding.viewPager2.adapter = CommunityPagerAdapter(requireActivity(), user?.planetCode + "@" + sParentcode, false, settings)
        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as CommunityPagerAdapter).getPageTitle(position)
        }.attach()
        fragmentTeamDetailBinding.title.text = if (user?.planetCode == "") communityName else user?.planetCode
        fragmentTeamDetailBinding.subtitle.text = settings.getString("planetType", "")
        fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
    }
}
