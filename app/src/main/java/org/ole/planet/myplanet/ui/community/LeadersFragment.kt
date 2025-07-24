package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import org.ole.planet.myplanet.di.AppPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LeadersFragment : Fragment() {
    private lateinit var fragmentMembersBinding: FragmentMembersBinding
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMembersBinding = FragmentMembersBinding.inflate(inflater, container, false)
        return fragmentMembersBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leaders = settings.getString("communityLeaders", "")
        if (leaders.isNullOrEmpty()) {
            fragmentMembersBinding.tvNodata.text = getString(R.string.no_data_available)
        } else {
            val leadersList = RealmUserModel.parseLeadersJson(leaders)
            fragmentMembersBinding.rvMember.layoutManager = GridLayoutManager(activity, 2)
            fragmentMembersBinding.rvMember.adapter = AdapterLeader(requireActivity(), leadersList)
        }
    }
}
