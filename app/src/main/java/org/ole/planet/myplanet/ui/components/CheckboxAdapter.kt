package org.ole.planet.myplanet.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R

class CheckboxAdapter(
    private val initialSelectedItems: List<Int> = emptyList(),
    private val checkChangeListener: CheckChangeListener? = null
) : ListAdapter<String, CheckboxAdapter.ViewHolder>(CheckboxDiffCallback()) {

    val selectedItemsList = ArrayList<Int>()

    init {
        selectedItemsList.addAll(initialSelectedItems)
    }

    fun interface CheckChangeListener {
        fun onCheckChange()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.rowlayout, parent, false) as CheckedTextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bindingAdapterPosition = holder.bindingAdapterPosition
        if (bindingAdapterPosition == RecyclerView.NO_POSITION) return

        val isChecked = selectedItemsList.contains(bindingAdapterPosition)
        holder.checkedTextView.text = getItem(bindingAdapterPosition)
        holder.checkedTextView.isChecked = isChecked

        holder.checkedTextView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener

            if (selectedItemsList.contains(currentPos)) {
                selectedItemsList.remove(Integer.valueOf(currentPos))
                holder.checkedTextView.isChecked = false
            } else {
                selectedItemsList.add(currentPos)
                holder.checkedTextView.isChecked = true
            }
            checkChangeListener?.onCheckChange()
        }
    }

    class ViewHolder(val checkedTextView: CheckedTextView) : RecyclerView.ViewHolder(checkedTextView)

    class CheckboxDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
