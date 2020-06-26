package org.ole.planet.myplanet.ui.course

import android.content.Context
import android.content.Intent
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import kotlinx.android.synthetic.main.item_progress.view.*
import kotlinx.android.synthetic.main.row_my_progress.view.*
import org.ole.planet.myplanet.R

class AdapterMyProgress(private val context: Context, private val list: JsonArray) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.row_my_progress, parent, false)
        return ViewHolderMyProgress(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            holder.tvTitle.text = list[position].asJsonObject["courseName"].asString
            if (list[position].asJsonObject.has("progress")) {
                holder.tvDescription.text = "Current step : " + list[position].asJsonObject["progress"].asJsonObject["current"].asInt.toString() + " of " + list[position].asJsonObject["progress"].asJsonObject["max"].asInt.toString()
                holder.itemView.setOnClickListener {
                    context.startActivity(Intent(context, CourseProgressActivity::class.java).putExtra("courseId", list[position].asJsonObject["courseId"].asString))
                }
            }
            if (list[position].asJsonObject.has("mistakes"))
                holder.tvTotal.text = list[position].asJsonObject["mistakes"].asString
            else
                holder.tvTotal.text = "0"
            showStepMistakes(holder, position);

        }
    }

    private fun showStepMistakes(holder: ViewHolderMyProgress, position: Int) {
        if (list[position].asJsonObject.has("stepMistake")) {
            var stepMistake = list[position].asJsonObject["stepMistake"].asJsonObject
            holder.llProgress.removeAllViews()
            if (stepMistake.keySet().size > 0) {
                var stepView = LayoutInflater.from(context).inflate(R.layout.item_progress, null)
                stepView.step.text = Html.fromHtml("<b>Step</b>")
                stepView.mistake.text = Html.fromHtml("<b>Mistake</b>")
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
        var tvDescription: TextView = itemView.tv_description

    }
}
