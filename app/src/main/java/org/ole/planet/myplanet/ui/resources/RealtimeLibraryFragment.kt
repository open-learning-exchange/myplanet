package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.view.View
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.base.BaseRealtimeFragment
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.Utilities

abstract class RealtimeLibraryFragment : BaseRealtimeFragment<RealmMyLibrary>() {
    
    protected var lastUpdateTime = 0L
    protected var resourceList = mutableListOf<RealmMyLibrary>()
    
    override fun getWatchedTables(): List<String> {
        return listOf("resources", "library")
    }
    
    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        when (table) {
            "resources", "library" -> {
                updateLibraryData()
                showUpdateNotification(update)
            }
        }
    }
    
    private fun updateLibraryData() {
        val newList = getUpdatedResourceList()
        
        // Use DiffUtils for efficient updates
        val diffResult = DiffUtils.calculateDiff(
            resourceList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem ->
                oldItem.title == newItem.title &&
                    oldItem.description == newItem.description &&
                    oldItem.resourceLocalAddress == newItem.resourceLocalAddress
            }
        )
        
        resourceList.clear()
        resourceList.addAll(newList)
        
        requireActivity().runOnUiThread {
            recyclerView?.adapter?.let { adapter ->
                diffResult.dispatchUpdatesTo(adapter)
            }
        }
        
        lastUpdateTime = System.currentTimeMillis()
    }
    
    protected abstract fun getUpdatedResourceList(): List<RealmMyLibrary>
    
    private fun showUpdateNotification(update: TableDataUpdate) {
        if (update.newItemsCount > 0 || update.updatedItemsCount > 0) {
            val message = buildString {
                if (update.newItemsCount > 0) {
                    append("${update.newItemsCount} new resources")
                }
                if (update.updatedItemsCount > 0) {
                    if (update.newItemsCount > 0) append(", ")
                    append("${update.updatedItemsCount} updated")
                }
            }
            
            requireActivity().runOnUiThread {
                Utilities.toast(requireActivity(), "Library updated: $message")
            }
        }
    }
    
    override fun shouldAutoRefresh(table: String): Boolean {
        // Only auto-refresh if the last update was more than 1 second ago
        // This prevents too frequent updates
        return System.currentTimeMillis() - lastUpdateTime > 1000
    }
}
