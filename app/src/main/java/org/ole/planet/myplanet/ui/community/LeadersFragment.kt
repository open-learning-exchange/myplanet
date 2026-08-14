package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class LeadersFragment : Fragment() {
    private var binding: FragmentMembersBinding? = null
    private val viewModel: LeadersViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentMembersBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.rvMember?.layoutManager = GridLayoutManager(activity, 2)
        val adapter = CommunityLeadersAdapter(requireActivity())
        binding?.rvMember?.adapter = adapter

        collectWhenStarted(viewModel.leaders) { leadersList ->
            if (leadersList.isEmpty()) {
                binding?.tvNodata?.let { it.text = getString(R.string.no_data_available) }
            } else {
                binding?.tvNodata?.let { it.text = "" }
                adapter.submitList(leadersList)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
