package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_members.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import java.util.ArrayList

class LeadersFragment : Fragment() {
    private var _binding: FragmentMembersBinding? = null
    private val binding: FragmentMembersBinding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        val leadersAdapter = createLeadersAdapter()
        setupRecyclerView(leadersAdapter)
        setData(leadersAdapter)
    }

    private fun setData(leadersAdapter: AdapterLeader) {
        val mRealm = DatabaseService(requireContext()).realmInstance;
        val leaders = mRealm.where(RealmMyTeam::class.java)
            .equalTo("isLeader", true).findAll()
        if (leaders.isEmpty()) {
            binding.tvNodata.text = "No data available"
        } else {
            val list = ArrayList<RealmUserModel>()
            for (team in leaders) {
                val model = mRealm.where(RealmUserModel::class.java)
                    .equalTo(/* fieldName = */ "id", /* value = */
                        team.user_id
                    ).findFirst()
                if (model != null && !list.contains(model))
                    list.add(model)
            }
            leadersAdapter.submitList(list)
        }
    }

    private fun setupRecyclerView(leadersAdapter: AdapterLeader) {
        binding.rvMember.apply {
            adapter = leadersAdapter
            hasFixedSize()
        }
    }

    private fun createLeadersAdapter(): AdapterLeader {
        return AdapterLeader()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //Nullify binding instance to avoid memory leaks
        _binding = null
    }
}