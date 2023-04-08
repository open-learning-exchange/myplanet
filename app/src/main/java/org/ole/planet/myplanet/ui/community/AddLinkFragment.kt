package org.ole.planet.myplanet.ui.community

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.Realm
import org.ole.planet.myplanet.databinding.FragmentAddLinkBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.team.AdapterTeam
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class AddLinkFragment : BottomSheetDialogFragment(), AdapterView.OnItemSelectedListener {
    lateinit var binding: FragmentAddLinkBinding
    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    lateinit var mRealm: Realm
    var selectedTeam: RealmMyTeam? = null;

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {

        val query = mRealm.where(RealmMyTeam::class.java)
            .isEmpty("teamId")
            .isNotEmpty("name")
            .equalTo(
                "type",
                if (binding.spnLink.selectedItem.toString() == "Enterprises"
                ) "enterprise" else ""
            )
            .notEqualTo("status", "archived")
            .findAll()
        binding.rvList.layoutManager = LinearLayoutManager(requireActivity())
        Utilities.log("SIZE ${query}")
        val adapter = AdapterTeam(requireActivity(), query, mRealm)
        adapter.setTeamSelectedListener { team ->
            this.selectedTeam = team;
            Utilities.toast(requireActivity(), """Selected ${team.name}""")
        }
        binding.rvList.adapter = adapter
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


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAddLinkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mRealm = DatabaseService(requireActivity()).realmInstance
        binding.spnLink.onItemSelectedListener = this
        binding.btnSave.setOnClickListener {
            val type = binding.spnLink?.selectedItem.toString()
            val title = binding.etName?.text.toString()
            if (title.isNullOrEmpty()) {
                Utilities.toast(requireActivity(), "Title is required")
                return@setOnClickListener
            }
            if (selectedTeam == null) {
                Utilities.toast(requireActivity(), "Please select link item from list")
                return@setOnClickListener
            }

            mRealm.executeTransaction {
                var team = it.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                team.docType = "link"
                team.isUpdated = true
                team.title = title
                team.route = """/${type.toLowerCase()}/view/${selectedTeam!!._id}"""
                dismiss()

            }
        }

    }

}
