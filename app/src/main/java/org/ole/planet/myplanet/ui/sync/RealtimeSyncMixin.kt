package org.ole.planet.myplanet.ui.sync

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.OnBaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.OnDiffRefreshListener
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager

interface RealtimeSyncMixin {
    fun getWatchedTables(): List<String>
    fun onDataUpdated(table: String, update: TableDataUpdate)
    fun getSyncRecyclerView(): RecyclerView?
    fun shouldAutoRefresh(table: String): Boolean = true
}

class RealtimeSyncHelper(
    private val fragment: Fragment,
    private val mixin: RealtimeSyncMixin
) {
    
    private val syncManagerInstance = RealtimeSyncManager.getInstance()
    
    private val onRealtimeSyncListener = object : OnBaseRealtimeSyncListener() {
        override fun onTableDataUpdated(update: TableDataUpdate) {
            if (mixin.getWatchedTables().contains(update.table)) {
                mixin.onDataUpdated(update.table, update)
                if (mixin.shouldAutoRefresh(update.table)) {
                    refreshRecyclerView()
                }
            }
        }
        
        override fun onSyncStarted() {}
        override fun onSyncComplete() {}
        override fun onSyncFailed(msg: String?) {}
    }
    
    fun setupRealtimeSync() {
        syncManagerInstance.addListener(onRealtimeSyncListener)

        fragment.viewLifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    cleanup()
                }
            }
        })
        
        // Listen to data update flow
        fragment.lifecycleScope.launch {
            fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncManagerInstance.dataUpdateFlow
                    .filter { update -> mixin.getWatchedTables().contains(update.table) }
                    .distinctUntilChanged { old, new -> 
                        old.table == new.table && 
                        old.newItemsCount == new.newItemsCount && 
                        old.updatedItemsCount == new.updatedItemsCount 
                    }
                    .collect { update ->
                        mixin.onDataUpdated(update.table, update)
                        if (mixin.shouldAutoRefresh(update.table)) {
                            refreshRecyclerView()
                        }
                    }
            }
        }
    }
    
    private fun refreshRecyclerView() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val adapter = mixin.getSyncRecyclerView()?.adapter ?: return@launch
            when {
                adapter is OnDiffRefreshListener -> adapter.refreshWithDiff()
                adapter is ListAdapter<*, *> -> {
                    (adapter as ListAdapter<Any, *>).let { listAdapter ->
                        listAdapter.submitList(listAdapter.currentList.toList())
                    }
                }
            }
        }
    }
    
    fun cleanup() {
        syncManagerInstance.removeListener(onRealtimeSyncListener)
    }
}
