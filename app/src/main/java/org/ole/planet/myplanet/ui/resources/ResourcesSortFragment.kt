package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseSortBottomSheetFragment

class ResourcesSortFragment : BaseSortBottomSheetFragment<ResourcesViewModel.SortMode>() {
    fun interface SortSelectionListener {
        fun onSortSelected(mode: ResourcesViewModel.SortMode)
    }

    private var listener: SortSelectionListener? = null
    private var currentMode: ResourcesViewModel.SortMode = ResourcesViewModel.SortMode.NONE
    private var isDateAscending: Boolean = true
    private var isTitleAscending: Boolean = false

    override val dateSortValue = ResourcesViewModel.SortMode.DATE
    override val titleSortValue = ResourcesViewModel.SortMode.TITLE

    fun setListener(listener: SortSelectionListener) {
        this.listener = listener
    }

    fun setCurrentMode(mode: ResourcesViewModel.SortMode) {
        currentMode = mode
    }

    fun setCurrentDirection(isDateAscending: Boolean, isTitleAscending: Boolean) {
        this.isDateAscending = isDateAscending
        this.isTitleAscending = isTitleAscending
    }

    override fun currentSortValue(): ResourcesViewModel.SortMode = currentMode

    override fun onSortSelected(value: ResourcesViewModel.SortMode) {
        listener?.onSortSelected(value)
    }

    override fun dateLabel(): String {
        val arrow = if (isDateAscending) "↑" else "↓"
        return "${getString(R.string.order_by_date)} $arrow"
    }

    override fun titleLabel(): String {
        val arrow = if (isTitleAscending) "↑" else "↓"
        return "${getString(R.string.order_by_title)} $arrow"
    }
}
