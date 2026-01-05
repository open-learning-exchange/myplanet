package org.ole.planet.myplanet.ui.courses

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowStepMistakeBinding

class StepMistakeAdapter(private var items: List<StepMistake>) : RecyclerView.Adapter<StepMistakeAdapter.ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<StepMistake>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowStepMistakeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvStep.text = (item.step + 1).toString()
        holder.binding.tvMistake.text = item.mistakes.toString()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class ViewHolder(val binding: RowStepMistakeBinding) : RecyclerView.ViewHolder(binding.root)
}
