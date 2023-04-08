package org.ole.planet.myplanet.ui.course

import android.content.Context
import android.content.Intent
import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import org.ole.planet.myplanet.databinding.ItemProgressBinding
import org.ole.planet.myplanet.databinding.RowMyProgressBinding

class AdapterMyProgress(private val context: Context, private val list: JsonArray) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowMyProgressBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolderMyProgress(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            holder.tvTitle.text = list[position].asJsonObject["courseName"].asString
            if (list[position].asJsonObject.has("progress")) {
                holder.tvDescription.text =
                    "Current step : " + list[position].asJsonObject["progress"].asJsonObject["current"].asInt.toString() + " of " + list[position].asJsonObject["progress"].asJsonObject["max"].asInt.toString()
                holder.itemView.setOnClickListener {
                    context.startActivity(
                        Intent(
                            context,
                            CourseProgressActivity::class.java
                        ).putExtra("courseId", list[position].asJsonObject["courseId"].asString)
                    )
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

//                var stepView = LayoutInflater.from(context).inflate(R.layout.item_progress, null)
//                stepView.step.text = Html.fromHtml("<b>Step</b>")
//                stepView.mistake.text = Html.fromHtml("<b>Mistake</b>")
//                holder.llProgress.addView(stepView)
//                binding = ItemProgressBinding.inflate(layoutInflater, holder.llProgress, false)
                val stepView = ItemProgressBinding.inflate(LayoutInflater.from(context))
                stepView.step.text = Html.fromHtml("<b>Step</b>")
                stepView.mistake.text = Html.fromHtml("<b>Mistake</b>")
                holder.llProgress.addView(stepView.root)

                stepMistake.keySet().forEach {
//                    var stepView =
//                        LayoutInflater.from(context).inflate(R.layout.item_progress, null)
                    stepView.step.text = (it.toInt().plus(1).toString())
                    stepView.mistake.text = stepMistake[it].asInt.toString()
                    holder.llProgress.addView(stepView.root)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    internal inner class ViewHolderMyProgress(private val binding: RowMyProgressBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var tvTitle: TextView = binding.tvTitle
        var tvTotal: TextView = binding.tvTotal
        var llProgress: LinearLayout = binding.llProgress
        var tvDescription: TextView = binding.tvDescription

    }
}
