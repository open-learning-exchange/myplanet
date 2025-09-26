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
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.TeamPageListener
import org.ole.planet.myplanet.databinding.FragmentTeamResourceBinding
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.CheckboxListView

@AndroidEntryPoint
class TeamResourceFragment : BaseTeamFragment(), ResourceUpdateListner {
    private var _binding: FragmentTeamResourceBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapterLibrary: AdapterTeamResource

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showLibraryList()
        if (!isMember()) {
            binding.fabAddResource.visibility = View.GONE
        }
        binding.fabAddResource.setOnClickListener { showResourceListDialog() }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun showLibraryList() {
        if (!isAdded || activity == null) return
        val safeActivity = activity ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val libraries = teamRepository.getTeamResources(teamId).toMutableList()
            val canRemoveResources = teamRepository.isTeamLeader(teamId, user?.id)
            adapterLibrary = AdapterTeamResource(
                safeActivity,
                libraries,
                canRemoveResources,
                this@TeamResourceFragment,
            ) { resource, position ->
                handleResourceRemoval(resource, position)
            }
            binding.rvResource.layoutManager = GridLayoutManager(safeActivity, 3)
            binding.rvResource.adapter = adapterLibrary
            checkAndShowNoData()
        }
    }

    private fun showResourceListDialog() {
        if (!isAdded || activity == null) return
        val safeActivity = activity ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val existing = teamRepository.getTeamResources(teamId)
            val existingIds = existing.mapNotNull { it._id }
            val availableLibraries = libraryRepository.getAllLibraryItems()
                .filter { it._id !in existingIds }

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

            alertDialogBuilder.setView(myLibraryAlertdialogBinding.root)
                .setPositiveButton(R.string.add) { _: DialogInterface?, _: Int ->
                    val selectedResources = myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList
                        .map { index -> availableLibraries[index] }
                    viewLifecycleOwner.lifecycleScope.launch {
                        teamRepository.addResourceLinks(teamId, selectedResources, user)
                        showLibraryList()
                    }
                }.setNegativeButton(R.string.cancel, null)

            val alertDialog = alertDialogBuilder.create()
            alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
            listSetting(alertDialog, availableLibraries, myLibraryAlertdialogBinding.alertDialogListView)
        }
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
        showNoData(binding.tvNodata, adapterLibrary.itemCount, "teamResources")
    }

    override fun onResourceListUpdated() {
        checkAndShowNoData()
    }

    override fun onResourceUpdateFailed(messageResId: Int) {
        view?.let {
            Snackbar.make(it, getString(messageResId), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun handleResourceRemoval(resource: RealmMyLibrary, position: Int) {
        val resourceId = resource.id ?: resource.resourceId
        if (resourceId.isNullOrBlank()) {
            onResourceUpdateFailed(R.string.failed_to_remove_resource)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                teamRepository.removeResourceLink(teamId, resourceId)
            }.onSuccess {
                adapterLibrary.removeResourceAt(position)
            }.onFailure {
                onResourceUpdateFailed(R.string.failed_to_remove_resource)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
