package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressGridBinding

class AdapterProgressGrid(private val context: Context, private val list: JsonArray) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowMyProgressGridBinding: RowMyProgressGridBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowMyProgressGridBinding = RowMyProgressGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(rowMyProgressGridBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            if (list[position].asJsonObject.has("percentage")) {
                holder.tvProgress.text = context.getString(R.string.percentage, list[position].asJsonObject["percentage"].asString)
                if (list[position].asJsonObject["completed"].asBoolean) {
                    holder.itemView.setBackgroundColor(ContextCompat.getColor(context, com.mikepenz.materialize.R.color.md_green_500))
                } else {
                    holder.itemView.setBackgroundColor(ContextCompat.getColor(context, com.mikepenz.materialize.R.color.md_yellow_500))
                }
            } else {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.mainColor))
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    internal inner class ViewHolderMyProgress(rowMyProgressGridBinding: RowMyProgressGridBinding) : RecyclerView.ViewHolder(rowMyProgressGridBinding.root) {
        var tvProgress = rowMyProgressGridBinding.tvProgress
    }
}
