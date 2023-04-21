package org.ole.planet.myplanet.ui.community.leaders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentMembersBinding
import org.ole.planet.myplanet.ui.community.AdapterLeader
import org.ole.planet.myplanet.ui.community.leaders.models.LeadersUIState
import org.ole.planet.myplanet.utilities.DialogUtils

@AndroidEntryPoint
class LeadersFragment : Fragment() {
    private var _binding: FragmentMembersBinding? = null
    private val binding: FragmentMembersBinding get() = _binding!!
    private val viewModel: LeadersViewModel by viewModels()

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
        observeViewState(leadersAdapter)
    }

    private fun observeViewState(leadersAdapter: AdapterLeader) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.leadersUIState
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { handleLeaderUIState(it, leadersAdapter) }
        }
    }

    private fun handleLeaderUIState(state: LeadersUIState, leadersAdapter: AdapterLeader) {
        with(binding) {
            progressBar.isVisible = state.isLoading
            rvMember.isVisible = !state.isLoading && state.errorMessage == null
                    && state.leaders.isNotEmpty()
            tvNodata.apply {
                isVisible = !state.isLoading && state.leaders.isEmpty()
                text = "No data available"
            }
            if (state.errorMessage != null) {
                DialogUtils.showSnack(requireView(), state.errorMessage)
            }
            leadersAdapter.submitList(state.leaders)
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