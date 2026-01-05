package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowMyProgressBinding
import org.ole.planet.myplanet.utilities.DiffUtils

class CoursesProgressAdapter(private val context: Context) : ListAdapter<CourseProgressItem, CoursesProgressAdapter.ViewHolderMyProgress>(DiffUtils.itemCallback({ old, new -> old.courseId == new.courseId }, { old, new -> old == new })) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMyProgress {
        val binding = RowMyProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderMyProgress(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderMyProgress, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolderMyProgress(val binding: RowMyProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        private val stepMistakeAdapter = StepMistakeAdapter(emptyList())

        init {
            binding.rvStepMistakes.layoutManager = LinearLayoutManager(context)
            binding.rvStepMistakes.adapter = stepMistakeAdapter
        }

        fun bind(item: CourseProgressItem) {
            binding.tvTitle.text = item.courseName
            binding.tvDescription.text = context.getString(R.string.step_progress, item.progressCurrent, item.progressMax)
            itemView.setOnClickListener {
                context.startActivity(Intent(context, CourseProgressActivity::class.java).putExtra("courseId", item.courseId))
            }
            binding.tvTotal.text = item.mistakes

            if (item.stepMistakes.isNotEmpty()) {
                binding.llHeader.visibility = View.VISIBLE
                binding.rvStepMistakes.visibility = View.VISIBLE
                stepMistakeAdapter.updateData(item.stepMistakes)
            } else {
                binding.llHeader.visibility = View.GONE
                binding.rvStepMistakes.visibility = View.GONE
            }
        }
    }
}
