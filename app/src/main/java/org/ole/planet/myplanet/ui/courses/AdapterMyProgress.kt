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
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressBinding

class AdapterMyProgress(private val context: Context, private val list: List<CourseProgress>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowMyProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            val course = list[position]
            holder.binding.tvTitle.text = course.courseName
            course.progress?.let { progress ->
                holder.binding.tvDescription.text = context.getString(
                    R.string.step_progress,
                    progress["current"],
                    progress["max"]
                )
            }
            holder.itemView.setOnClickListener {
                context.startActivity(Intent(context, CourseProgressActivity::class.java).putExtra("courseId", course.courseId))
            }
            holder.binding.tvTotal.text = context.getString(R.string.message_placeholder, course.mistakes.toString())
            showStepMistakes(course, holder.binding)
        }
    }

    private fun showStepMistakes(course: CourseProgress, binding: RowMyProgressBinding) {
        val stepMistake = course.stepMistake
        binding.llProgress.removeAllViews()

        if (stepMistake.isNotEmpty()) {
            binding.llHeader.visibility = View.VISIBLE
            val textColor = ContextCompat.getColor(context, R.color.daynight_textColor)
            stepMistake.keys.forEach { stepKey ->
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
                    text = "${stepMistake[stepKey]}"
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
    }


    override fun getItemCount(): Int {
        return list.size
    }

    internal inner class ViewHolderMyProgress(val binding: RowMyProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        val tvTitle = binding.tvTitle
        val tvTotal = binding.tvTotal
        val tvDescription = binding.tvDescription
    }
}
