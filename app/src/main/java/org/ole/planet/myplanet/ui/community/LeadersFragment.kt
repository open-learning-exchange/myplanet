package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

class LeadersFragment : Fragment() {
    private lateinit var fragmentMembersBinding: FragmentMembersBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        fragmentMembersBinding = FragmentMembersBinding.inflate(inflater, container, false)
        return fragmentMembersBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mRealm = DatabaseService(requireActivity()).realmInstance
        val leaders = mRealm.where(RealmMyTeam::class.java).equalTo("isLeader", true).findAll()
        if (leaders.isEmpty()) {
            fragmentMembersBinding.tvNodata.text = getString(R.string.no_data_available)
        } else {
            fragmentMembersBinding.rvMember.layoutManager = GridLayoutManager(activity, 2)
            val list = ArrayList<RealmUserModel>()
            for (team in leaders) {
                val model =
                    mRealm.where(RealmUserModel::class.java).equalTo("id", team.userId).findFirst()
                if (model != null && !list.contains(model)) list.add(model)
            }
            fragmentMembersBinding.rvMember.adapter = AdapterLeader(requireActivity(), list)
        }
    }
}