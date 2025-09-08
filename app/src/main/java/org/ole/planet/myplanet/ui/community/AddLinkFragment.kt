package org.ole.planet.myplanet.ui.community

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentAddLinkBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.ui.team.AdapterTeam
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class AddLinkFragment : BottomSheetDialogFragment(), AdapterView.OnItemSelectedListener {
    private var _binding: FragmentAddLinkBinding? = null
    private val binding get() = _binding!!
    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    @Inject
    lateinit var teamRepository: TeamRepository
    var selectedTeam: RealmMyTeam? = null

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isEnterprise = binding.spnLink.selectedItem.toString() == "Enterprises"
            val teams = teamRepository.getSelectableTeams(isEnterprise)
            binding.rvList.layoutManager = LinearLayoutManager(requireActivity())
            val adapter = AdapterTeam(requireActivity(), teams)
            adapter.setTeamSelectedListener(object : AdapterTeam.OnTeamSelectedListener {
                override fun onSelectedTeam(team: RealmMyTeam) {
                    this@AddLinkFragment.selectedTeam = team
                    Utilities.toast(requireActivity(), "Selected ${team.name}")
                }
            })
            binding.rvList.adapter = adapter
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { d ->
            val dialog = d as BottomSheetDialog
            val bottomSheet =
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            BottomSheetBehavior.from(bottomSheet!!).state = BottomSheetBehavior.STATE_EXPANDED
            BottomSheetBehavior.from(bottomSheet).skipCollapsed = true
            BottomSheetBehavior.from(bottomSheet).setHideable(true)
        }
        return bottomSheetDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.spnLink.onItemSelectedListener = this
        binding.btnSave.setOnClickListener {
            val type = binding.spnLink.selectedItem.toString()
            val title = binding.etName.text.toString()
            if (title.isEmpty()) {
                Utilities.toast(requireActivity(), getString(R.string.title_is_required))
                return@setOnClickListener
            }
            if (selectedTeam == null) {
                Utilities.toast(requireActivity(), getString(R.string.please_select_link_item_from_list))
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                teamRepository.addLink(type, title, selectedTeam!!._id!!)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
