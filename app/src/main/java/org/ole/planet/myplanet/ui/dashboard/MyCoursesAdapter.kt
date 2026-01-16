package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment

class MyCoursesAdapter(private val homeItemClickListener: OnHomeItemClickListener?) : ListAdapter<RealmMyCourse, MyCoursesAdapter.CourseViewHolder>(COURSE_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_course_home, parent, false)
        return CourseViewHolder(view as TextView)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = getItem(position)
        holder.bind(course, position)
    }

    inner class CourseViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(course: RealmMyCourse, position: Int) {
            textView.text = course.courseTitle
            textView.setTextColor(ContextCompat.getColor(textView.context, R.color.daynight_textColor))
            if (position % 2 == 0) {
                textView.setBackgroundResource(R.drawable.light_rect)
            } else {
                textView.setBackgroundResource(R.color.dashboard_item_alternative)
            }
            textView.setOnClickListener {
                homeItemClickListener?.let {
                    val f = TakeCourseFragment()
                    val b = android.os.Bundle()
                    b.putString("id", course.courseId)
                    f.arguments = b
                    it.openCallFragment(f)
                }
            }
        }
    }

    companion object {
        private val COURSE_COMPARATOR = object : DiffUtil.ItemCallback<RealmMyCourse>() {
            override fun areItemsTheSame(oldItem: RealmMyCourse, newItem: RealmMyCourse): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RealmMyCourse, newItem: RealmMyCourse): Boolean {
                return oldItem == newItem
            }
        }
    }
}
