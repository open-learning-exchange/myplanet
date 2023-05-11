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
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_add_link.btn_save
import kotlinx.android.synthetic.main.fragment_add_link.et_name
import kotlinx.android.synthetic.main.fragment_add_link.rv_list
import kotlinx.android.synthetic.main.fragment_add_link.spn_link
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.team.AdapterTeam
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Locale
import java.util.UUID

class AddLinkFragment : BottomSheetDialogFragment(), AdapterView.OnItemSelectedListener {
    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    lateinit var mRealm: Realm
    var selectedTeam: RealmMyTeam? = null;

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {

        val query =
            mRealm.where(RealmMyTeam::class.java).isEmpty("teamId").isNotEmpty("name").equalTo(
                "type",
                if (spn_link.selectedItem.toString().equals("Enterprises")) "enterprise" else ""
            ).notEqualTo("status", "archived").findAll()
        rv_list.layoutManager = LinearLayoutManager(requireActivity())
        Utilities.log("SIZE ${query}")
        val adapter = AdapterTeam(requireActivity(), query, mRealm)
        adapter.setTeamSelectedListener { team ->
            this.selectedTeam = team;
            Utilities.toast(requireActivity(), """Selected ${team.name}""")
        }
        rv_list.adapter = adapter
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_link, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mRealm = DatabaseService(requireActivity()).realmInstance
        spn_link.onItemSelectedListener = this
        btn_save.setOnClickListener {
            var type = spn_link?.selectedItem.toString()
            var title = et_name?.text.toString()
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
                team.route = """/${type.toLowerCase(Locale.ROOT)}/view/${selectedTeam!!._id}"""
                dismiss()

            }
        }
    }
}
