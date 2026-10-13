package org.ole.planet.myplanet.ui.courses

import org.ole.planet.myplanet.base.BaseSortFragment

class CoursesSortFragment : BaseSortFragment<CoursesViewModel.SortType>() {
    fun interface SortSelectionListener {
        fun onSortSelected(type: CoursesViewModel.SortType)
    }

    private var listener: SortSelectionListener? = null
    private var currentType: CoursesViewModel.SortType? = null

    override val dateSortValue = CoursesViewModel.SortType.DATE
    override val titleSortValue = CoursesViewModel.SortType.TITLE

    fun setListener(listener: SortSelectionListener) {
        this.listener = listener
    }

    fun setCurrentType(type: CoursesViewModel.SortType?) {
        currentType = type
    }

    override fun hasListener(): Boolean = listener != null

    override fun currentSortValue(): CoursesViewModel.SortType? = currentType

    override fun onSortSelected(value: CoursesViewModel.SortType) {
        listener?.onSortSelected(value)
    }
}
