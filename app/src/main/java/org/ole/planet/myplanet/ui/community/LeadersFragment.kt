package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

class LeadersFragment : Fragment() {
    lateinit var binding: FragmentMembersBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var mRealm = DatabaseService(requireActivity()).realmInstance;
        val leaders = mRealm.where(RealmMyTeam::class.java).equalTo("isLeader", true).findAll()
        if (leaders.isEmpty()) {
            binding.tvNodata.text = "No data available"
        } else {
            binding.rvMember.layoutManager = GridLayoutManager(activity, 2)
            val list = ArrayList<RealmUserModel>()
            for (team in leaders) {
                val model =
                    mRealm.where(RealmUserModel::class.java).equalTo("id", team.user_id)
                        .findFirst()
                if (model != null && !list.contains(model))
                    list.add(model)
            }
            binding.rvMember.adapter = AdapterLeader(requireActivity(), list)
        }

    }


}