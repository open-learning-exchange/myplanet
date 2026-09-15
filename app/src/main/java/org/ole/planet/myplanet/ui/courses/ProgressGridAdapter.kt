package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressGridBinding
import org.ole.planet.myplanet.utils.DiffUtils

class ProgressGridAdapter(private val context: Context) :
    ListAdapter<JsonObject, ProgressGridAdapter.ViewHolderMyProgress>(
        DiffUtils.itemCallback<JsonObject>(
            areItemsTheSame = { oldItem, newItem -> oldItem["stepId"] == newItem["stepId"] },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    ) {
    private val colorCompleted by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getColor(context, R.color.md_green_500)
    }
    private val colorInProgress by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getColor(context, R.color.md_yellow_500)
    }
    private val colorMain by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getColor(context, R.color.mainColor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyProgress {
        val rowMyProgressGridBinding =
            RowMyProgressGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(rowMyProgressGridBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMyProgress, position: Int) {
        val item = getItem(position)
        if (item.has("percentage")) {
            holder.tvProgress.text =
                context.getString(R.string.percentage, item["percentage"].asString)
            if (item["completed"].asBoolean) {
                holder.itemView.setBackgroundColor(colorCompleted)
            } else {
                holder.itemView.setBackgroundColor(colorInProgress)
            }
        } else {
            holder.itemView.setBackgroundColor(colorMain)
        }
    }

    inner class ViewHolderMyProgress(rowMyProgressGridBinding: RowMyProgressGridBinding) :
        RecyclerView.ViewHolder(rowMyProgressGridBinding.root) {
        var tvProgress = rowMyProgressGridBinding.tvProgress
    }
}
