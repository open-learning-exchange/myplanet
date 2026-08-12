package org.ole.planet.myplanet.ui.sync

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.ole.planet.myplanet.callback.OnDiffRefreshListener
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.collectWhenStarted

interface RealtimeSyncMixin {
    fun getWatchedTables(): List<String>
    fun onDataUpdated(table: String, update: TableDataUpdate)
    fun getSyncRecyclerView(): RecyclerView?
    fun shouldAutoRefresh(table: String): Boolean = true
}

class RealtimeSyncHelper(
    private val fragment: Fragment,
    private val mixin: RealtimeSyncMixin,
    private val syncManagerInstance: RealtimeSyncManager
) {

    @OptIn(FlowPreview::class)
    fun setupRealtimeSync() {
        fragment.collectWhenStarted(
            syncManagerInstance.dataUpdateFlow
                .filter { update -> mixin.getWatchedTables().contains(update.table) }
                .distinctUntilChanged { old, new ->
                    old.table == new.table &&
                    old.newItemsCount == new.newItemsCount &&
                    old.updatedItemsCount == new.updatedItemsCount
                }
                .debounce(300)
        ) { update ->
            mixin.onDataUpdated(update.table, update)
            if (mixin.shouldAutoRefresh(update.table)) {
                refreshRecyclerView()
            }
        }
    }

    private fun refreshRecyclerView() {
        val adapter = mixin.getSyncRecyclerView()?.adapter ?: return
        if (adapter is OnDiffRefreshListener) {
            adapter.refreshWithDiff()
        }
    }

}
