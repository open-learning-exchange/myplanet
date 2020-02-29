package org.ole.planet.myplanet.ui.course

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import kotlinx.android.synthetic.main.row_my_progress_grid.view.*
import org.ole.planet.myplanet.R

class AdapterProgressGrid(private val context: Context, private val list: JsonArray) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.row_my_progress_grid, parent, false)
        return ViewHolderMyProgress(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            if (list[position].asJsonObject.has("percentage")) {
                holder.tvProgress.text = list[position].asJsonObject["percentage"].asString + "%"
                if (list[position].asJsonObject["completed"].asBoolean) {
                    holder.itemView.setBackgroundColor(context.resources.getColor(R.color.md_green_500))
                } else {
                    holder.itemView.setBackgroundColor(context.resources.getColor(R.color.md_yellow_500))
                }
            } else {
                holder.itemView.setBackgroundColor(context.resources.getColor(R.color.md_red_500))
            }
        }
    }


    override fun getItemCount(): Int {
        return list.size()
    }

    internal inner class ViewHolderMyProgress(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvProgress: TextView = itemView.tv_progress


    }
}
