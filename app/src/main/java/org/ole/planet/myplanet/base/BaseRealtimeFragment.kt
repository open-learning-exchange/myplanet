package org.ole.planet.myplanet.base

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.BaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator

abstract class BaseRealtimeFragment<LI> : BaseRecyclerFragment<LI>() {
    
    protected abstract fun getWatchedTables(): List<String>
    protected abstract fun onDataUpdated(table: String, update: TableDataUpdate)
    protected open fun shouldAutoRefresh(table: String): Boolean = true
    
    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    
    private val realtimeSyncListener = object : BaseRealtimeSyncListener() {
        override fun onTableDataUpdated(update: TableDataUpdate) {
            if (getWatchedTables().contains(update.table)) {
                onDataUpdated(update.table, update)
                if (shouldAutoRefresh(update.table)) {
                    refreshRecyclerView()
                }
            }
        }
        
        override fun onSyncStarted() {
            // Show sync indicator if needed
        }
        
        override fun onSyncComplete() {
            // Hide sync indicator
        }
        
        override fun onSyncFailed(message: String?) {
            // Handle sync failure
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRealtimeSync()
    }
    
    private fun setupRealtimeSync() {
        syncCoordinator.addListener(realtimeSyncListener)
        
        // Listen to data update flow
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncCoordinator.dataUpdateFlow
                    .filter { update -> getWatchedTables().contains(update.table) }
                    .distinctUntilChanged { old, new -> 
                        old.table == new.table && 
                        old.newItemsCount == new.newItemsCount && 
                        old.updatedItemsCount == new.updatedItemsCount 
                    }
                    .collect { update ->
                        onDataUpdated(update.table, update)
                        if (shouldAutoRefresh(update.table)) {
                            refreshRecyclerView()
                        }
                    }
            }
        }
    }
    
    protected fun refreshRecyclerView() {
        viewLifecycleOwner.lifecycleScope.launch {
            recyclerView?.adapter?.notifyDataSetChanged()
            // Alternative: Use DiffUtil for more efficient updates
            // if (adapter is YourAdapter) adapter.updateData(newData)
        }
    }

    protected fun refreshRecyclerViewWithDiff(newData: List<*>) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Implement DiffUtil-based refresh for better performance
            // This should be implemented in subclasses with specific adapter types
        }
    }
    
    override fun onDestroyView() {
        syncCoordinator.removeListener(realtimeSyncListener)
        super.onDestroyView()
    }
}
