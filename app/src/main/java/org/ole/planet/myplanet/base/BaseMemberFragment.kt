package org.ole.planet.myplanet.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.teams.BaseTeamFragment

abstract class BaseMemberFragment : BaseTeamFragment() {
    abstract val list: List<RealmUserModel?>
    abstract val adapter: RecyclerView.Adapter<*>?
    abstract val layoutManager: RecyclerView.LayoutManager?
    private var _binding: FragmentMembersBinding? = null
    protected val binding get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvMember.layoutManager = layoutManager
        binding.rvMember.adapter = adapter
        showNoData(binding.tvNodata, list.size, "members")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
