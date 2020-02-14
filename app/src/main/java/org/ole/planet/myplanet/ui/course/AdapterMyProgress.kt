package org.ole.planet.myplanet.ui.course

import android.content.Context
import android.content.Intent
import androidx.recyclerview.widget.RecyclerView

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.JsonArray
import io.realm.Realm
import kotlinx.android.synthetic.main.item_progress.view.*
import kotlinx.android.synthetic.main.row_my_progress.view.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterMyProgress(private val context: Context, private val list: JsonArray) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.row_my_progress, parent, false)
        return ViewHolderMyProgress(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            holder.tvTitle.text = list[position].asJsonObject["courseName"].asString
            if (list[position].asJsonObject.has("mistakes"))
                holder.tvTotal.text = list[position].asJsonObject["mistakes"].asString
            else
                holder.tvTotal.text = "0"
            var stepMistake = list[position].asJsonObject["stepMistake"].asJsonObject
            holder.llProgress.removeAllViews()
            if (stepMistake.keySet().size > 0) {
                var stepView = LayoutInflater.from(context).inflate(R.layout.item_progress, null)
                stepView.step.text = "Step"
                stepView.mistake.text = "Mistake"
                holder.llProgress.addView(stepView)
                stepMistake.keySet().forEach {
                    var stepView = LayoutInflater.from(context).inflate(R.layout.item_progress, null)
                    stepView.step.text = (it.toInt().plus(1).toString())
                    stepView.mistake.text = stepMistake[it].asInt.toString()
                    holder.llProgress.addView(stepView)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    internal inner class ViewHolderMyProgress(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvTitle: TextView = itemView.tv_title
        var tvTotal: TextView = itemView.tv_total
        var llProgress: LinearLayout = itemView.ll_progress

    }
}
