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
import org.ole.planet.myplanet.ui.team.teamResource.ResourceUpdateListner
import org.ole.planet.myplanet.databinding.FragmentTeamResourceBinding
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getResourceIds
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.CheckboxListView
import java.util.UUID

class TeamResourceFragment : BaseTeamFragment(), TeamPageListener, ResourceUpdateListner  {
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
        val resourceIds = getResourceIds(teamId, mRealm)
        val libraries: MutableList<RealmMyLibrary> = mRealm.where(RealmMyLibrary::class.java)
            .`in`("id", resourceIds.toTypedArray())
            .findAll()
            .toMutableList()

        adapterLibrary = settings?.let {
            AdapterTeamResource(requireActivity(), libraries, mRealm, teamId, it, this)
        }!!
        fragmentTeamResourceBinding.rvResource.layoutManager = GridLayoutManager(activity, 3)
        fragmentTeamResourceBinding.rvResource.adapter = adapterLibrary
        checkAndShowNoData()
    }

    private fun showResourceListDialog() {
        if (!isAdded) {
            return
        }

        val titleView = TextView(requireActivity()).apply {
            text = getString(R.string.select_resource)
            setTextColor(context.getColor(R.color.daynight_textColor))
            setPadding(75, 50, 0, 0)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }

        val myLibraryAlertdialogBinding = MyLibraryAlertdialogBinding.inflate(layoutInflater)
        val alertDialogBuilder = AlertDialog.Builder(requireActivity())
        alertDialogBuilder.setCustomTitle(titleView)

        val availableLibraries: List<RealmMyLibrary> = mRealm.where(RealmMyLibrary::class.java)
            .not().`in`("_id", getResourceIds(teamId, mRealm).toTypedArray())
            .findAll()

        alertDialogBuilder.setView(myLibraryAlertdialogBinding.root)
            .setPositiveButton(R.string.add) { _: DialogInterface?, _: Int ->
                val selected = myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList

                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }
                for (se in selected) {
                    val team = mRealm.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                    team.teamId = teamId
                    team.title = availableLibraries[se].title
                    team.status = user!!.parentCode
                    team.resourceId = availableLibraries[se]._id
                    team.docType = "resourceLink"
                    team.updated = true
                    team.teamType = "local"
                    team.teamPlanetCode = user!!.planetCode
                }
                mRealm.commitTransaction()
                showLibraryList()
            }.setNegativeButton(R.string.cancel, null)

        val alertDialog = alertDialogBuilder.create()
        alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
        listSetting(alertDialog, availableLibraries, myLibraryAlertdialogBinding.alertDialogListView)
    }

    private fun listSetting(alertDialog: AlertDialog, libraries: List<RealmMyLibrary>, lv: CheckboxListView) {
        val names = libraries.map { it.title }
        val adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv.setCheckChangeListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.isNotEmpty()
        }
        lv.adapter = adapter
        alertDialog.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.isNotEmpty()
    }

    fun checkAndShowNoData() {
        showNoData(fragmentTeamResourceBinding.tvNodata, adapterLibrary.itemCount, "teamResources")
    }

    override fun onResourceListUpdated() {
        checkAndShowNoData()
    }

    override fun onAddDocument() {
        showResourceListDialog()
    }
}
