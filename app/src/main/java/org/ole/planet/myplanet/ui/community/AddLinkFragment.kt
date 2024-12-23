package org.ole.planet.myplanet.ui.community

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.kotlin.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentAddLinkBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.team.AdapterTeam
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Locale
import java.util.UUID

class AddLinkFragment : BottomSheetDialogFragment(), AdapterView.OnItemSelectedListener {
    private lateinit var fragmentAddLinkBinding: FragmentAddLinkBinding
    lateinit var mRealm: Realm
    var selectedTeam: RealmMyTeam? = null

    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        val query = mRealm.query<RealmMyTeam>(RealmMyTeam::class, "teamId == null AND name != null AND type == $0 AND status != $1",
            if (fragmentAddLinkBinding.spnLink.selectedItem.toString() == "Enterprises") "enterprise" else "",
            "archived").find()
        fragmentAddLinkBinding.rvList.layoutManager = LinearLayoutManager(requireActivity())
        val adapter = AdapterTeam(requireActivity(), query, mRealm)
        adapter.setTeamSelectedListener(object : AdapterTeam.OnTeamSelectedListener {
            override fun onSelectedTeam(team: RealmMyTeam) {
                this@AddLinkFragment.selectedTeam = team
                Utilities.toast(requireActivity(), "Selected ${team.name}")
            }
        })

        fragmentAddLinkBinding.rvList.adapter = adapter
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { d ->
            val dialog = d as BottomSheetDialog
            val bottomSheet =
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            BottomSheetBehavior.from(bottomSheet!!).state = BottomSheetBehavior.STATE_EXPANDED
            BottomSheetBehavior.from(bottomSheet).skipCollapsed = true
            BottomSheetBehavior.from(bottomSheet).isHideable = true
        }
        return bottomSheetDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentAddLinkBinding = FragmentAddLinkBinding.inflate(inflater, container, false)
        return fragmentAddLinkBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRealm = DatabaseService().realmInstance
        fragmentAddLinkBinding.spnLink.onItemSelectedListener = this
        fragmentAddLinkBinding.btnSave.setOnClickListener {
            val type = fragmentAddLinkBinding.spnLink.selectedItem.toString()
            var titles = fragmentAddLinkBinding.etName.text.toString()
            if (titles.isEmpty()) {
                Utilities.toast(requireActivity(), getString(R.string.title_is_required))
                return@setOnClickListener
            }
            if (selectedTeam == null) {
                Utilities.toast(requireActivity(), getString(R.string.please_select_link_item_from_list))
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    mRealm.write {
                        val team = RealmMyTeam().apply {
                            _id = UUID.randomUUID().toString()
                            docType = "link"
                            updated = true
                            title = titles
                            route = """/${type.lowercase(Locale.ROOT)}/view/${selectedTeam?._id}"""
                        }
                        copyToRealm(team)
                    }
                    dismiss()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}