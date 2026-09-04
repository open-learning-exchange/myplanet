package org.ole.planet.myplanet.ui.components

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.GridSpanCalculator
import org.ole.planet.myplanet.utils.ListViewMode

class ListViewModeController(
    private val fragment: Fragment, private val recyclerView: RecyclerView,
    private val toggleGridButton: ImageButton?, private val toggleListButton: ImageButton?,
    private val getMode: () -> ListViewMode, private val setMode: (ListViewMode) -> Unit,
    private val onModeChanged: (ListViewMode) -> Unit
) {
    private val spanUpdateRunnable = Runnable { updateGridSpanIfNeeded() }
    private val layoutChangeListener = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft) {
            recyclerView.removeCallbacks(spanUpdateRunnable)
            recyclerView.post(spanUpdateRunnable)
        }
    }

    fun setup() {
        updateToggleUi(getMode())
        toggleGridButton?.setOnClickListener { setViewMode(ListViewMode.GRID) }
        toggleListButton?.setOnClickListener { setViewMode(ListViewMode.LIST) }
        recyclerView.addOnLayoutChangeListener(layoutChangeListener)
    }

    fun refreshSpanOnResume() {
        recyclerView.removeCallbacks(spanUpdateRunnable)
        recyclerView.post(spanUpdateRunnable)
    }

    fun teardown() {
        recyclerView.removeOnLayoutChangeListener(layoutChangeListener)
        recyclerView.removeCallbacks(spanUpdateRunnable)
    }

    private fun setViewMode(mode: ListViewMode) {
        setMode(mode)
        updateToggleUi(mode)
        onModeChanged(mode)
    }

    private fun applyRecyclerLayoutManager(mode: ListViewMode) {
        val currentLayoutManager = recyclerView.layoutManager
        if (mode == ListViewMode.GRID) {
            if (currentLayoutManager is GridLayoutManager) {
                currentLayoutManager.spanCount = currentSpanCount()
            } else {
                recyclerView.layoutManager = GridLayoutManager(fragment.requireContext(), currentSpanCount())
            }
        } else {
            if (currentLayoutManager !is LinearLayoutManager || currentLayoutManager is GridLayoutManager) {
                recyclerView.layoutManager = LinearLayoutManager(fragment.requireContext())
            }
        }
    }

    private fun currentSpanCount(): Int {
        val displayMetrics = fragment.requireContext().resources.displayMetrics
        val widthPx = recyclerView.width.takeIf { it > 0 } ?: displayMetrics.widthPixels
        val widthDp = (widthPx / displayMetrics.density).toInt()
        return GridSpanCalculator.columnCount(widthDp)
    }

    private fun updateGridSpanIfNeeded() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpan = currentSpanCount()
            if (layoutManager.spanCount != currentSpan) {
                layoutManager.spanCount = currentSpan
            }
        }
    }

    private fun updateToggleUi(mode: ListViewMode) {
        val isGrid = mode == ListViewMode.GRID
        val context = fragment.requireContext()
        val activeColor = ContextCompat.getColor(context, android.R.color.white)
        val inactiveColor = ContextCompat.getColor(context, R.color.daynight_textColor)
        toggleGridButton?.setBackgroundResource(if (isGrid) R.drawable.bg_toggle_selected else android.R.color.transparent)
        toggleListButton?.setBackgroundResource(if (!isGrid) R.drawable.bg_toggle_selected else android.R.color.transparent)
        toggleGridButton?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(if (isGrid) activeColor else inactiveColor)) }
        toggleListButton?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(if (!isGrid) activeColor else inactiveColor)) }
        applyRecyclerLayoutManager(mode)
    }
}
