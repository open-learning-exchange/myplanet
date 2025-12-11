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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressBinding
import org.ole.planet.myplanet.model.ProgressData

class AdapterMyProgress(private val context: Context) : ListAdapter<ProgressData, AdapterMyProgress.ViewHolderMyProgress>(PROGRESS_DATA_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyProgress {
        val binding = RowMyProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderMyProgress, position: Int) {
        val item = getItem(position)
        holder.binding.tvTitle.text = item.courseName
        holder.binding.tvDescription.text = context.getString(R.string.progress_percentage, item.progress)
        holder.itemView.setOnClickListener {
            context.startActivity(Intent(context, CourseProgressActivity::class.java).putExtra("courseId", item.courseId))
        }
        holder.binding.tvTotal.text = context.getString(R.string.mistakes_with_colon, item.mistakes)
        showStepMistakes(item.stepMistakes, holder.binding)
    }

    private fun showStepMistakes(stepMistakes: Map<String, Int>, binding: RowMyProgressBinding) {
        binding.llProgress.removeAllViews()
        if (stepMistakes.isNotEmpty()) {
            binding.llHeader.visibility = View.VISIBLE
            val textColor = ContextCompat.getColor(context, R.color.daynight_textColor)
            stepMistakes.keys.forEach { stepKey ->
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
                    text = "${stepMistakes[stepKey]}"
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

    inner class ViewHolderMyProgress(val binding: RowMyProgressBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val PROGRESS_DATA_COMPARATOR = object : DiffUtil.ItemCallback<ProgressData>() {
            override fun areItemsTheSame(oldItem: ProgressData, newItem: ProgressData): Boolean {
                return oldItem.courseId == newItem.courseId
            }

            override fun areContentsTheSame(oldItem: ProgressData, newItem: ProgressData): Boolean {
                return oldItem == newItem
            }
        }
    }
}
