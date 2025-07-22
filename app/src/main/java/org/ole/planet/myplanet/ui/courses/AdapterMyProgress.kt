package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemProgressBinding
import org.ole.planet.myplanet.databinding.RowMyProgressBinding

class AdapterMyProgress(private val context: Context, private val list: JsonArray) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowMyProgressBinding: RowMyProgressBinding

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
                rowMyProgressBinding.llHeader.visibility = View.VISIBLE
                stepMistake.keySet().forEach { stepKey ->
                    val dataBinding = ItemProgressBinding.inflate(LayoutInflater.from(context))
                    dataBinding.step.text = "${stepKey.toInt().plus(1)}"
                    dataBinding.step.gravity = Gravity.CENTER
                    dataBinding.mistake.text = "${stepMistake[stepKey].asInt}"
                    dataBinding.mistake.gravity = Gravity.CENTER
                    rowMyProgressBinding.llProgress.addView(dataBinding.root)
                }
            } else {
                rowMyProgressBinding.llHeader.visibility = View.GONE
            }
        } else {
            rowMyProgressBinding.llHeader.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    internal inner class ViewHolderMyProgress(rowMyProgressBinding: RowMyProgressBinding) : RecyclerView.ViewHolder(rowMyProgressBinding.root) {
        var tvTitle = rowMyProgressBinding.tvTitle
        var tvTotal = rowMyProgressBinding.tvTotal
        //var llProgress = rowMyProgressBinding.llProgress
        var tvDescription = rowMyProgressBinding.tvDescription
    }
}
