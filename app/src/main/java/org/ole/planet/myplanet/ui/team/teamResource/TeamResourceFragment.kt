package org.ole.planet.myplanet.ui.team.teamResource

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
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
        Log.d("team", "View created, initializing library list")
        showLibraryList()
        if (!isMember()) {
            Log.d("team", "User is not a member, hiding add resource button")
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
        Log.d("team", "Fetching team resources for teamId: $teamId")
        val resourceIds = getResourceIds(teamId, mRealm)
        Log.d("team", "Fetched resource IDs: $resourceIds")

        val libraries: MutableList<RealmMyLibrary> = mRealm.where(RealmMyLibrary::class.java)
            .`in`("id", resourceIds.toTypedArray())
            .findAll()
            .toMutableList()

        Log.d("team", "Fetched libraries: ${libraries.map { it.title }}")

        adapterLibrary = settings?.let {
            AdapterTeamResource(requireActivity(), libraries, mRealm, teamId, it)
        }!!
        fragmentTeamResourceBinding.rvResource.layoutManager = GridLayoutManager(activity, 3)
        fragmentTeamResourceBinding.rvResource.adapter = adapterLibrary
        showNoData(fragmentTeamResourceBinding.tvNodata, adapterLibrary.itemCount, "teamResources")
    }

    private fun showResourceListDialog() {
        if (!isAdded) {
            Log.w("team", "Fragment is not attached, skipping resource list dialog")
            return
        }
        Log.d("team", "Opening resource selection dialog")

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

        Log.d("team", "Available resources to add: ${availableLibraries.map { it.title }}")

        alertDialogBuilder.setView(myLibraryAlertdialogBinding.root)
            .setPositiveButton(R.string.add) { _: DialogInterface?, _: Int ->
                val selected = myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList
                Log.d("team", "Selected resources: ${selected.map { availableLibraries[it].title }}")

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
                    Log.d("team", "Added resource to team: ${team.title} (ID: ${team.resourceId})")
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
        Log.d("team", "Setting up list with available libraries: $names")

        val adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv.setCheckChangeListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.isNotEmpty()
            Log.d("team", "Selected items count: ${lv.selectedItemsList.size}")
        }
        lv.adapter = adapter
        alertDialog.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.isNotEmpty()
    }

    override fun onAddDocument() {
        Log.d("team", "onAddDocument() called, opening resource selection dialog")
        showResourceListDialog()
    }
}
