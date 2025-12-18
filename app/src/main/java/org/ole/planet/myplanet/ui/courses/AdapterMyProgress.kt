package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressBinding
import org.ole.planet.myplanet.utilities.DiffUtils

class AdapterMyProgress(private val context: Context) : ListAdapter<JsonObject, RecyclerView.ViewHolder>(DiffUtils.itemCallback({ old, new -> old.asJsonObject["courseId"]?.asString == new.asJsonObject["courseId"]?.asString }, { old, new -> getCourseProgressComparisonData(old) == getCourseProgressComparisonData(new) })) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowMyProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            val item = getItem(position)
            holder.binding.tvTitle.text = item.asJsonObject["courseName"].asString
            if (item.asJsonObject.has("progress")) {
                holder.binding.tvDescription.text = context.getString(R.string.step_progress, item.asJsonObject["progress"].asJsonObject["current"].asInt, item.asJsonObject["progress"].asJsonObject["max"].asInt)
                holder.itemView.setOnClickListener {
                    context.startActivity(Intent(context, CourseProgressActivity::class.java).putExtra("courseId", item.asJsonObject["courseId"].asString))
                }
            }
            if (item.asJsonObject.has("mistakes")) holder.binding.tvTotal.text =
                item.asJsonObject["mistakes"].asString
            else holder.binding.tvTotal.text = context.getString(R.string.message_placeholder, "0")
            showStepMistakes(position, holder.binding)
        }
    }

    private fun showStepMistakes(position: Int, binding: RowMyProgressBinding) {
        val item = getItem(position)
        if (item.asJsonObject.has("stepMistake")) {
            val stepMistake = item.asJsonObject["stepMistake"].asJsonObject
            binding.llProgress.removeAllViews()

            if (stepMistake.keySet().isNotEmpty()) {
                binding.llHeader.visibility = View.VISIBLE
                val textColor = ContextCompat.getColor(context, R.color.daynight_textColor)
                stepMistake.keySet().forEach { stepKey ->
                    val row = LinearLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }

                    val stepView = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        text = "${stepKey.toInt().plus(1)}"
                        gravity = Gravity.CENTER
                        setTextColor(textColor)
                    }

                    val mistakeView = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        text = "${stepMistake[stepKey].asInt}"
                        gravity = Gravity.CENTER
                        setTextColor(textColor)
                    }

                    row.addView(stepView)
                    row.addView(mistakeView)

                    binding.llProgress.addView(row)
                }
            } else {
                binding.llHeader.visibility = View.GONE
            }
        } else {
            binding.llHeader.visibility = View.GONE
        }
    }

    internal inner class ViewHolderMyProgress(val binding: RowMyProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        val tvTitle = binding.tvTitle
        val tvTotal = binding.tvTotal
        val tvDescription = binding.tvDescription
    }

    private fun getCourseProgressComparisonData(item: JsonObject): List<Any?> {
        val courseName = item.asJsonObject["courseName"]?.asString
        val progressCurrent = item.asJsonObject["progress"]?.asJsonObject?.get("current")?.asInt
        val progressMax = item.asJsonObject["progress"]?.asJsonObject?.get("max")?.asInt
        val mistakes = item.asJsonObject["mistakes"]?.asJsonObject
        val stepMistake = item.asJsonObject["stepMistake"]?.asJsonObject
        return listOf(courseName, progressCurrent, progressMax, mistakes, stepMistake)
    }
}
