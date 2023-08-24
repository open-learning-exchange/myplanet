package org.ole.planet.myplanet.ui.course

import android.content.Context
import android.content.Intent
import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
                holder.tvDescription.text =
                    context.getString(R.string.current_step) + list[position].asJsonObject["progress"].asJsonObject["current"].asInt.toString() + context.getString(R.string.of) + list[position].asJsonObject["progress"].asJsonObject["max"].asInt.toString()
                holder.itemView.setOnClickListener {
                    context.startActivity(
                        Intent(
                            context, CourseProgressActivity::class.java
                        ).putExtra("courseId", list[position].asJsonObject["courseId"].asString)
                    )
                }
            }
            if (list[position].asJsonObject.has("mistakes")) holder.tvTotal.text =
                list[position].asJsonObject["mistakes"].asString
            else holder.tvTotal.text = "0"
            showStepMistakes(holder, position);
        }
    }

    private fun showStepMistakes(holder: ViewHolderMyProgress, position: Int) {
        if (list[position].asJsonObject.has("stepMistake")) {
            var stepMistake = list[position].asJsonObject["stepMistake"].asJsonObject
            rowMyProgressBinding.llProgress.removeAllViews()
            itemProgressBinding = ItemProgressBinding.inflate(LayoutInflater.from(context))
            if (stepMistake.keySet().size > 0) {
                itemProgressBinding.step.text = HtmlCompat.fromHtml("<b>Step</b>", HtmlCompat.FROM_HTML_MODE_LEGACY)
                itemProgressBinding.mistake.text = HtmlCompat.fromHtml("<b>Mistake</b>", HtmlCompat.FROM_HTML_MODE_LEGACY)
                rowMyProgressBinding.llProgress.addView(itemProgressBinding.root)
                
                stepMistake.keySet().forEach {
                    itemProgressBinding.step.text = (it.toInt().plus(1).toString())
                    itemProgressBinding.mistake.text = stepMistake[it].asInt.toString()
                    rowMyProgressBinding.llProgress.addView(itemProgressBinding.root)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    internal inner class ViewHolderMyProgress(private val rowMyProgressBinding: RowMyProgressBinding) : RecyclerView.ViewHolder(rowMyProgressBinding.root) {
        var tvTitle = rowMyProgressBinding.tvTitle
        var tvTotal = rowMyProgressBinding.tvTotal
        var llProgress = rowMyProgressBinding.llProgress
        var tvDescription = rowMyProgressBinding.tvDescription
    }
}
