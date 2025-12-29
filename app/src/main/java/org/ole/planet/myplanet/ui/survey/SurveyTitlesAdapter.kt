package org.ole.planet.myplanet.ui.survey

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utilities.DiffUtils

class SurveyTitlesAdapter(
    private val onItemClick: (Int) -> Unit,
    private val dialog: AlertDialog
) : ListAdapter<String, SurveyTitlesAdapter.SurveyViewHolder>(DIFF_CALLBACK) {

    inner class SurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)

        init {
            itemView.setOnClickListener {
                onItemClick(bindingAdapterPosition)
                dialog.dismiss()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return SurveyViewHolder(view)
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        holder.textView.text = getItem(position)
        holder.textView.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                R.color.daynight_textColor
            )
        )
    }

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<String>(
            areItemsTheSame = { oldItem, newItem -> oldItem == newItem },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
