package org.ole.planet.myplanet.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DiffUtils

class CheckboxAdapter(
    private val initialSelectedItems: List<Int> = emptyList(),
    private val checkChangeListener: CheckChangeListener? = null
) : ListAdapter<String, CheckboxAdapter.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { a, b -> a == b },
        areContentsTheSame = { a, b -> a == b }
    )
) {

    val selectedItemsList = ArrayList<Int>()

    init {
        selectedItemsList.addAll(initialSelectedItems)
    }

    override fun onCurrentListChanged(
        previousList: MutableList<String>,
        currentList: MutableList<String>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        if (previousList.isNotEmpty()) {
            val selectedValues = selectedItemsList.mapNotNull { index -> previousList.getOrNull(index) }
            selectedItemsList.clear()
            selectedValues.forEach { value ->
                val newIndex = currentList.indexOf(value)
                if (newIndex != -1) {
                    selectedItemsList.add(newIndex)
                }
            }
        }
    }

    fun interface CheckChangeListener {
        fun onCheckChange()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.rowlayout, parent, false) as CheckedTextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isChecked = selectedItemsList.contains(position)
        holder.checkedTextView.text = getItem(position)
        holder.checkedTextView.isChecked = isChecked

        holder.checkedTextView.setOnClickListener {
            if (selectedItemsList.contains(position)) {
                selectedItemsList.remove(Integer.valueOf(position))
                holder.checkedTextView.isChecked = false
            } else {
                selectedItemsList.add(position)
                holder.checkedTextView.isChecked = true
            }
            checkChangeListener?.onCheckChange()
        }
    }

    class ViewHolder(val checkedTextView: CheckedTextView) : RecyclerView.ViewHolder(checkedTextView)
}
