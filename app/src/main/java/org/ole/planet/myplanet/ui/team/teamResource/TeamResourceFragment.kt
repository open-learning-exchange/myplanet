package org.ole.planet.myplanet.ui.team.teamResource

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.TeamPageListener
import org.ole.planet.myplanet.databinding.FragmentTeamResourceBinding
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getResourceIds
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.CheckboxListView
import java.util.UUID

class TeamResourceFragment : BaseTeamFragment(), TeamPageListener {
    private lateinit var fragmentTeamResourceBinding: FragmentTeamResourceBinding
    private lateinit var adapterLibrary: AdapterTeamResource
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamResourceBinding = FragmentTeamResourceBinding.inflate(inflater, container, false)
        return fragmentTeamResourceBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showLibraryList()
        if (!isMember()) {
            fragmentTeamResourceBinding.fabAddResource.visibility = View.GONE
        }
        fragmentTeamResourceBinding.fabAddResource.setOnClickListener { showResourceListDialog() }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun showLibraryList() {
        if (!isAdded || activity == null) return
        val safeActivity = activity ?: return

        val libraries: List<RealmMyLibrary> = mRealm.where(RealmMyLibrary::class.java).`in`("id", getResourceIds(teamId, mRealm).toTypedArray<String>()).findAll()
        adapterLibrary = settings?.let { AdapterTeamResource(safeActivity, libraries, mRealm, teamId, it) }!!
        fragmentTeamResourceBinding.rvResource.layoutManager = GridLayoutManager(safeActivity, 3)
        fragmentTeamResourceBinding.rvResource.adapter = adapterLibrary
        showNoData(fragmentTeamResourceBinding.tvNodata, adapterLibrary.itemCount, "teamResources")
    }

    private fun showResourceListDialog() {
        if (!isAdded || activity == null) return
        val safeActivity = activity ?: return

        val titleView = TextView(safeActivity).apply {
            text = getString(R.string.select_resource)
            setTextColor(context.getColor(R.color.daynight_textColor))
            setPadding(75, 50, 0, 0)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }

        val myLibraryAlertdialogBinding = MyLibraryAlertdialogBinding.inflate(layoutInflater)
        val alertDialogBuilder = AlertDialog.Builder(safeActivity)
            .setCustomTitle(titleView)

        val libraries: List<RealmMyLibrary> = mRealm.where(RealmMyLibrary::class.java)
            .not().`in`("_id", getResourceIds(teamId, mRealm).toTypedArray())
            .findAll()

        alertDialogBuilder.setView(myLibraryAlertdialogBinding.root)
            .setPositiveButton(R.string.add) { _: DialogInterface?, _: Int ->
                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }
                for (se in myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList) {
                    val team = mRealm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                    team.teamId = teamId
                    team.title = libraries[se].title
                    team.status = user!!.parentCode
                    team.resourceId = libraries[se]._id
                    team.docType = "resourceLink"
                    team.updated = true
                    team.teamType = "local"
                    team.teamPlanetCode = user!!.planetCode
                }
                mRealm.commitTransaction()
                showLibraryList()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = alertDialogBuilder.create()
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
        listSetting(alertDialog, libraries, myLibraryAlertdialogBinding.alertDialogListView)
    }

    private fun listSetting(alertDialog: AlertDialog, libraries: List<RealmMyLibrary>, lv: CheckboxListView) {
        val names = ArrayList<String?>()
        for (i in libraries.indices) {
            names.add(libraries[i].title)
        }
        val adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv.setCheckChangeListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.size > 0
        }
        lv.adapter = adapter
        alertDialog.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.size > 0
    }

    override fun onAddDocument() {
        showResourceListDialog()
    }
}
