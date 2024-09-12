package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import androidx.appcompat.app.AlertDialog

class SurveyAdapter(
    private val surveys: List<String>,
    private val onItemClick: (Int) -> Unit,
    private val dialog: AlertDialog ) :
    RecyclerView.Adapter<SurveyAdapter.SurveyViewHolder>() {

    inner class SurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)

        init {
            itemView.setOnClickListener {
                onItemClick(adapterPosition)
                dialog.dismiss()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return SurveyViewHolder(view)
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        holder.textView.text = surveys[position]
        holder.textView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.daynight_textColor))
    }

    override fun getItemCount(): Int = surveys.size
}
