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
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressBinding
import org.ole.planet.myplanet.model.CoursesProgressRow
import org.ole.planet.myplanet.utils.DiffUtils

class CoursesProgressAdapter(private val context: Context) : ListAdapter<CoursesProgressRow, CoursesProgressAdapter.CoursesProgressViewHolder>(DIFF_CALLBACK) {

    private val textColor = ContextCompat.getColor(context, R.color.daynight_textColor)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoursesProgressViewHolder {
        val binding = RowMyProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CoursesProgressViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CoursesProgressViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvTitle.text = item.courseName
        if (item.progressCurrent != null && item.progressMax != null) {
            holder.binding.tvDescription.text = context.getString(R.string.step_progress, item.progressCurrent, item.progressMax)
            holder.itemView.setOnClickListener {
                context.startActivity(Intent(context, CourseProgressActivity::class.java).putExtra("courseId", item.courseId))
            }
        }
        if (item.mistakes != null) holder.binding.tvTotal.text = item.mistakes.toString()
        else holder.binding.tvTotal.text = context.getString(R.string.message_placeholder, "0")
        showStepMistakes(item, holder.binding)
    }

    private fun showStepMistakes(item: CoursesProgressRow, binding: RowMyProgressBinding) {
        val stepMistake = item.stepMistake

        if (stepMistake != null && stepMistake.isNotEmpty()) {
            binding.llHeader.visibility = View.VISIBLE

            val currentChildCount = binding.llProgress.childCount
            val requiredChildCount = stepMistake.size

            if (currentChildCount > requiredChildCount) {
                binding.llProgress.removeViews(requiredChildCount, currentChildCount - requiredChildCount)
            } else if (currentChildCount < requiredChildCount) {
                for (i in currentChildCount until requiredChildCount) {
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
                        gravity = Gravity.CENTER
                        setTextColor(textColor)
                    }

                    val mistakeView = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        gravity = Gravity.CENTER
                        setTextColor(textColor)
                    }

                    row.addView(stepView)
                    row.addView(mistakeView)

                    binding.llProgress.addView(row)
                }
            }

            var i = 0
            stepMistake.forEach { (stepKey, mistakes) ->
                val row = binding.llProgress.getChildAt(i) as LinearLayout
                val stepView = row.getChildAt(0) as TextView
                val mistakeView = row.getChildAt(1) as TextView

                stepView.text = "${stepKey.toInt().plus(1)}"
                mistakeView.text = "$mistakes"
                i++
            }
        } else {
            binding.llHeader.visibility = View.GONE
            binding.llProgress.removeAllViews()
        }
    }

    inner class CoursesProgressViewHolder(val binding: RowMyProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        val tvTitle = binding.tvTitle
        val tvTotal = binding.tvTotal
        val tvDescription = binding.tvDescription
    }

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<CoursesProgressRow>(
            areItemsTheSame = { old, new ->
                old.courseId == new.courseId
            },
            areContentsTheSame = { old, new ->
                old == new
            }
        )
    }
}
