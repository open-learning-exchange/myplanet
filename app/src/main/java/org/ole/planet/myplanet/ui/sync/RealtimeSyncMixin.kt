package org.ole.planet.myplanet.ui.sync

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.OnDiffRefreshListener
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager

interface RealtimeSyncMixin {
    fun getWatchedTables(): List<String>
    fun onDataUpdated(table: String, update: TableDataUpdate)
    fun getSyncRecyclerView(): RecyclerView?
    fun shouldAutoRefresh(table: String): Boolean = true
}

class RealtimeSyncHelper(private val fragment: Fragment, private val mixin: RealtimeSyncMixin) {
    private val syncManagerInstance = RealtimeSyncManager.getInstance()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun setupRealtimeSync() {
        val watchedTablesSet = mixin.getWatchedTables().toSet()
        fragment.lifecycleScope.launch {
            fragment.repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncManagerInstance.dataUpdateFlow
                    .filter { update -> watchedTablesSet.contains(update.table) }
                    .distinctUntilChanged { old, new ->
                        old.table == new.table &&
                        old.newItemsCount == new.newItemsCount &&
                        old.updatedItemsCount == new.updatedItemsCount
                    }
                    .debounce(300)
                    .collect { update ->
                        mixin.onDataUpdated(update.table, update)
                        if (mixin.shouldAutoRefresh(update.table)) {
                            refreshRecyclerView(update)
                        }
                    }
            }
        }
    }

    private fun refreshRecyclerView(update: TableDataUpdate) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val adapter = mixin.getSyncRecyclerView()?.adapter ?: return@launch
            when (adapter) {
                is OnDiffRefreshListener -> adapter.refreshWithDiff()
                is ListAdapter<*, *> -> {
                    if (update.newItemsCount == 0 && update.updatedItemsCount == 0) return@launch
                    @Suppress("UNCHECKED_CAST")
                    (adapter as ListAdapter<Any, *>).let { listAdapter ->
                        listAdapter.submitList(listAdapter.currentList.toList())
                    }
                }
            }
        }
    }

}
