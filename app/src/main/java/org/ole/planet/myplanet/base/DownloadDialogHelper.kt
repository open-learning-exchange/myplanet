package org.ole.planet.myplanet.base

import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.ui.components.CheckboxListView
import org.ole.planet.myplanet.utils.Utilities

fun BaseResourceFragment.showDownloadDialog(dbMyLibrary: List<RealmMyLibrary?>) {
    if (!isAdded) return
    val librariesForDialog = dbMyLibrary

    if (librariesForDialog.isEmpty()) {
        return
    }

    activity?.let { fragmentActivity ->
        val inflater = fragmentActivity.layoutInflater
        val rootView = fragmentActivity.findViewById<ViewGroup>(android.R.id.content)
        val convertView = inflater.inflate(R.layout.my_library_alertdialog, rootView, false)
        var lv: CheckboxListView? = null

        val alertDialogBuilder = AlertDialog.Builder(fragmentActivity, R.style.AlertDialogTheme)
        alertDialogBuilder.setView(convertView)
            .setTitle(R.string.download_suggestion)
            .setPositiveButton(R.string.download_selected) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    if (configurationsRepository.checkServerAvailability()) {
                        lv?.selectedItemsList?.let { selectedItems ->
                            val userId = profileDbHandler.getUserModel()?.id
                            if (userId != null) {
                                val resourceIds = selectedItems.mapNotNull { index ->
                                    librariesForDialog.getOrNull(index)?.resourceId
                                }
                                resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
                                Utilities.toast(activity, getString(R.string.added_to_my_library))
                            }

                            val selectedLibraries = selectedItems.mapNotNull { index -> librariesForDialog.getOrNull(index) }
                            val filtered = selectedLibraries.filterNotNull()
                            if (resourcesRepository.downloadResources(filtered)) {
                                trackDownloadUrls(filtered.mapNotNull { lib -> lib.resourceRemoteAddress })
                                showProgressDialog()
                            }
                        }
                    } else {
                        showNotConnectedToast()
                    }
                }
            }.setNeutralButton(R.string.download_all) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    if (configurationsRepository.checkServerAvailability()) {
                        val userId = profileDbHandler.getUserModel()?.id
                        if (userId != null) {
                            val validLibraryItems = librariesForDialog.filterNotNull()
                            resourcesRepository.addAllResourcesToUserLibrary(validLibraryItems, userId)
                            Utilities.toast(activity, getString(R.string.added_to_my_library))
                        }

                        val filtered = librariesForDialog.filterNotNull()
                        if (resourcesRepository.downloadResources(filtered)) {
                            trackDownloadUrls(filtered.mapNotNull { lib -> lib.resourceRemoteAddress })
                            showProgressDialog()
                        }
                    } else {
                        showNotConnectedToast()
                    }
                }
            }.setNegativeButton(R.string.txt_cancel, null)

        val downloadSuggestionDialog = alertDialogBuilder.create()

        lv = convertView?.findViewById(R.id.alertDialog_listView)
        val names = librariesForDialog.map { it?.title }
        val adapter = ArrayAdapter(fragmentActivity.baseContext, R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv?.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv?.setCheckChangeListener {
            downloadSuggestionDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
        }
        lv?.adapter = adapter

        downloadSuggestionDialog.show()
        downloadSuggestionDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv?.selectedItemsList?.size ?: 0) > 0
    }
}
