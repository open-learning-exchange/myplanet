package org.ole.planet.myplanet.ui.community

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserSessionManager

@AndroidEntryPoint
class LeadersFragment : Fragment() {
    private var binding: FragmentMembersBinding? = null
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    @Inject
    lateinit var userSessionManager: UserSessionManager
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leaders = settings.getString("communityLeaders", "")
        if (leaders.isNullOrEmpty()) {
            binding?.tvNodata?.let { it.text = getString(R.string.no_data_available) }
        } else {
            val leadersList = RealmUserModel.parseLeadersJson(leaders)
            binding?.rvMember?.layoutManager = GridLayoutManager(activity, 2)
            val adapter = CommunityLeadersAdapter(requireActivity(), userSessionManager)
            binding?.rvMember?.adapter = adapter
            adapter.submitList(leadersList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
