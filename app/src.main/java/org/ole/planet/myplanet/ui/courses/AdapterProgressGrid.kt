package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressGridBinding
import java.util.Locale

class AdapterProgressGrid(private val context: Context, private val stepProgressList: MutableList<StepProgress>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowMyProgressGridBinding: RowMyProgressGridBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowMyProgressGridBinding =
            RowMyProgressGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(rowMyProgressGridBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            val item = stepProgressList[position]
            if (item.percentage != null) {
                holder.tvProgress.text = context.getString(
                    R.string.percentage,
                    String.format(Locale.US, "%.2f", item.percentage)
                )
                if (item.completed == true) {
                    holder.itemView.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.md_green_500)
                    )
                } else {
                    holder.itemView.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.md_yellow_500)
                    )
                }
            } else {
                holder.itemView.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.mainColor)
                )
            }
        }
    }

    override fun getItemCount(): Int {
        return stepProgressList.size
    }

    fun addSteps(newSteps: List<StepProgress>) {
        val startPosition = stepProgressList.size
        stepProgressList.addAll(newSteps)
        notifyItemRangeInserted(startPosition, newSteps.size)
    }

    internal inner class ViewHolderMyProgress(rowMyProgressGridBinding: RowMyProgressGridBinding) : RecyclerView.ViewHolder(rowMyProgressGridBinding.root) {
        var tvProgress = rowMyProgressGridBinding.tvProgress
    }
}
