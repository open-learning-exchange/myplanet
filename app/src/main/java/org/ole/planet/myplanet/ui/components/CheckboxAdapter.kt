package org.ole.planet.myplanet.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R

class CheckboxAdapter(
    private val items: List<String>,
    private val initialSelectedItems: List<Int> = emptyList(),
    private val checkChangeListener: CheckChangeListener? = null
) : RecyclerView.Adapter<CheckboxAdapter.ViewHolder>() {

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
        val isChecked = selectedItemsList.contains(position)
        holder.checkedTextView.text = items[position]
        holder.checkedTextView.isChecked = isChecked

        holder.checkedTextView.setOnClickListener {
            if (selectedItemsList.contains(position)) {
                selectedItemsList.remove(position)
                holder.checkedTextView.isChecked = false
            } else {
                selectedItemsList.add(position)
                holder.checkedTextView.isChecked = true
            }
            checkChangeListener?.onCheckChange()
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val checkedTextView: CheckedTextView) : RecyclerView.ViewHolder(checkedTextView)
}
