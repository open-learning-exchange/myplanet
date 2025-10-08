package org.ole.planet.myplanet.ui.sync

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator

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
    
    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    
    fun setupRealtimeSync() {
        // Listen to data update flow
        fragment.lifecycleScope.launch {
            fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncCoordinator.dataUpdateFlow
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
            mixin.getSyncRecyclerView()?.adapter?.notifyDataSetChanged()
        }
    }

    fun cleanup() {}
}
