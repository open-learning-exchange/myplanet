package org.ole.planet.myplanet.ui.courses

import android.app.AlertDialog
import android.content.DialogInterface
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import org.ole.planet.myplanet.R

class CourseSelectionController(
    private val rootView: View,
    private val isMyCourseLib: Boolean,
    private val isGuest: Boolean,
    private val onRemoveConfirmed: () -> Unit,
    private val onArchiveConfirmed: () -> Unit,
    private val onAddToLib: () -> Unit,
    private val onSelectAllToggled: (checked: Boolean) -> Unit
) {
    lateinit var selectAll: CheckBox
        private set
    private lateinit var tvAddToLib: TextView
    private lateinit var btnRemove: Button
    private lateinit var btnArchive: Button
    private var isUpdatingSelectAllState = false
    private var currentSelectedCount = 0

    fun setup() {
        selectAll = rootView.findViewById(R.id.selectAllCourse)
        tvAddToLib = rootView.findViewById(R.id.tv_add)
        btnRemove = rootView.findViewById(R.id.btn_remove)
        btnArchive = rootView.findViewById(R.id.btn_archive)

        if (isGuest) {
            tvAddToLib.visibility = View.GONE
            btnRemove.visibility = View.GONE
            btnArchive.visibility = View.GONE
            selectAll.visibility = View.GONE
            return
        }

        tvAddToLib.setOnClickListener { onAddToLib() }

        btnRemove.setOnClickListener {
            showConfirmDialog(
                if (currentSelectedCount == 1) R.string.are_you_sure_you_want_to_leave_this_course
                else R.string.are_you_sure_you_want_to_leave_these_courses,
                onRemoveConfirmed
            )
        }

        btnArchive.setOnClickListener {
            showConfirmDialog(
                if (currentSelectedCount == 1) R.string.are_you_sure_you_want_to_archive_this_course
                else R.string.are_you_sure_you_want_to_archive_these_courses,
                onArchiveConfirmed
            )
        }

        selectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSelectAllState) return@setOnCheckedChangeListener
            onSelectAllToggled(isChecked)
            selectAll.text = rootView.context.getString(
                if (isChecked) R.string.unselect_all else R.string.select_all
            )
        }
    }

    fun onSelectionChanged(selectedCount: Int, areAllSelected: Boolean) {
        currentSelectedCount = selectedCount
        val hasSelection = selectedCount > 0
        btnArchive.isEnabled = hasSelection
        btnRemove.isEnabled = hasSelection
        tvAddToLib.isEnabled = hasSelection
        refreshActionVisibility()
        syncSelectAll(areAllSelected)
    }

    fun onListChanged(isEmpty: Boolean, hasSelectableItems: Boolean) {
        if (isEmpty) {
            selectAll.visibility = View.GONE
            tvAddToLib.visibility = View.GONE
            btnRemove.visibility = View.GONE
            btnArchive.visibility = View.GONE
        } else if (!isGuest) {
            selectAll.visibility = if (hasSelectableItems) View.VISIBLE else View.GONE
        }
    }

    fun clearAll(adapter: CoursesAdapter?) {
        adapter?.selectAllItems(false)
        currentSelectedCount = 0
        syncSelectAll(false)
        refreshActionVisibility()
    }

    private fun refreshActionVisibility() {
        if (currentSelectedCount > 0) {
            if (isMyCourseLib) {
                btnRemove.visibility = View.VISIBLE
                btnArchive.visibility = View.VISIBLE
            } else {
                tvAddToLib.visibility = View.VISIBLE
            }
        } else {
            if (isMyCourseLib) {
                btnRemove.visibility = View.GONE
                btnArchive.visibility = View.GONE
            } else {
                tvAddToLib.visibility = View.GONE
            }
        }
    }

    private fun syncSelectAll(allSelected: Boolean) {
        isUpdatingSelectAllState = true
        selectAll.isChecked = allSelected
        selectAll.text = rootView.context.getString(
            if (allSelected) R.string.unselect_all else R.string.select_all
        )
        isUpdatingSelectAllState = false
    }

    private fun showConfirmDialog(messageRes: Int, onConfirmed: () -> Unit) {
        AlertDialog.Builder(ContextThemeWrapper(rootView.context, R.style.CustomAlertDialog))
            .setMessage(messageRes)
            .setPositiveButton(R.string.yes) { _: DialogInterface, _: Int -> onConfirmed() }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
