package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentAddLinkBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.team.AdapterTeam
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class AddLinkFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentAddLinkBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var databaseService: DatabaseService
    var selectedTeam: RealmMyTeam? = null

    private fun loadTeams(type: String) {
        databaseService.withRealm { realm ->
            val teams = realm.copyFromRealm(
                realm.where(RealmMyTeam::class.java)
                    .isEmpty("teamId")
                    .isNotEmpty("name")
                    .equalTo(
                        "type",
                        if (type == "Enterprises") "enterprise" else ""
                    )
                    .notEqualTo("status", "archived")
                    .findAll()
            )
            binding.rvList.layoutManager = LinearLayoutManager(requireActivity())
            val adapter = AdapterTeam(requireActivity(), teams, databaseService)
            adapter.setTeamSelectedListener(object : AdapterTeam.OnTeamSelectedListener {
                override fun onSelectedTeam(team: RealmMyTeam) {
                    selectedTeam = team
                    Utilities.toast(requireActivity(), "Selected ${team.name}")
                }
            })
            binding.rvList.adapter = adapter
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.spnLink.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadTeams(binding.spnLink.selectedItem.toString())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
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

            databaseService.withRealm { realm ->
                realm.executeTransaction { r ->
                    val team = r.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                    team.docType = "link"
                    team.updated = true
                    team.title = title
                    team.route = """/${type.lowercase(Locale.ROOT)}/view/${selectedTeam!!._id}"""
                }
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
