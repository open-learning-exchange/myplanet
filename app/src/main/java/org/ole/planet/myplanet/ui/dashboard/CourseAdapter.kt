package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemCourseHomeBinding
import org.ole.planet.myplanet.model.RealmMyCourse

class CourseAdapter(
    private val courses: List<RealmMyCourse>,
    private val clickListener: (RealmMyCourse) -> Unit
) : RecyclerView.Adapter<CourseAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCourseHomeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val course = courses[position]
        holder.bind(course, position)
        holder.itemView.setOnClickListener { clickListener(course) }
    }

    override fun getItemCount(): Int = courses.size

    inner class ViewHolder(private val binding: ItemCourseHomeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(course: RealmMyCourse, position: Int) {
            binding.title.text = course.courseTitle
            val colorResId = if (position % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, colorResId))
        }
    }
}