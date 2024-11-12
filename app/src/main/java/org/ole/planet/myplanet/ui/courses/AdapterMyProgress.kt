package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemProgressBinding
import org.ole.planet.myplanet.databinding.RowMyProgressBinding

class AdapterMyProgress(private val context: Context, private val list: JsonArray) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowMyProgressBinding: RowMyProgressBinding
    private lateinit var itemProgressBinding: ItemProgressBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowMyProgressBinding = RowMyProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(rowMyProgressBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            holder.tvTitle.text = list[position].asJsonObject["courseName"].asString
            if (list[position].asJsonObject.has("progress")) {
                holder.tvDescription.text = context.getString(R.string.step_progress, list[position].asJsonObject["progress"].asJsonObject["current"].asInt, list[position].asJsonObject["progress"].asJsonObject["max"].asInt)
                holder.itemView.setOnClickListener {
                    context.startActivity(Intent(context, CourseProgressActivity::class.java).putExtra("courseId", list[position].asJsonObject["courseId"].asString))
                }
            }
            if (list[position].asJsonObject.has("mistakes")) holder.tvTotal.text =
                list[position].asJsonObject["mistakes"].asString
            else holder.tvTotal.text = context.getString(R.string.message_placeholder, "0")
            showStepMistakes(position)
        }
    }

    private fun showStepMistakes(position: Int) {
        if (list[position].asJsonObject.has("stepMistake")) {
            val stepMistake = list[position].asJsonObject["stepMistake"].asJsonObject
            rowMyProgressBinding.llProgress.removeAllViews()

            if (stepMistake.keySet().isNotEmpty()) {
                itemProgressBinding = ItemProgressBinding.inflate(LayoutInflater.from(context))
                itemProgressBinding.step.text = HtmlCompat.fromHtml("<b>Step</b>", HtmlCompat.FROM_HTML_MODE_LEGACY)
                itemProgressBinding.mistake.text = HtmlCompat.fromHtml("<b>Mistake</b>", HtmlCompat.FROM_HTML_MODE_LEGACY)
                rowMyProgressBinding.llProgress.addView(itemProgressBinding.root)
                
                stepMistake.keySet().forEach {
                    rowMyProgressBinding.llProgress.removeAllViews()
                    itemProgressBinding.step.text = "${it.toInt().plus(1)}"
                    itemProgressBinding.mistake.text = "${stepMistake[it].asInt}"
                    rowMyProgressBinding.llProgress.addView(itemProgressBinding.root)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    internal inner class ViewHolderMyProgress(rowMyProgressBinding: RowMyProgressBinding) : RecyclerView.ViewHolder(rowMyProgressBinding.root) {
        var tvTitle = rowMyProgressBinding.tvTitle
        var tvTotal = rowMyProgressBinding.tvTotal
//        var llProgress = rowMyProgressBinding.llProgress
        var tvDescription = rowMyProgressBinding.tvDescription
    }
}
