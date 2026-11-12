package org.ole.planet.myplanet.ui.teams.courses

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ItemCourseListBinding
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.courses.toCourse
import org.ole.planet.myplanet.utils.CourseSubjectClassifier
import org.ole.planet.myplanet.utils.CoursesItemUtils
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ListViewMode

class TeamCoursesAdapter(
    private val context: Context,
    private val canRemove: Boolean,
    private val onRemove: (MyCourse) -> Unit = {}
) : ListAdapter<MyCourse, TeamCoursesAdapter.ViewHolder>(DIFF_CALLBACK) {
    private val removeLabel: String by lazy { context.getString(R.string.remove) }
    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCourseListBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val myCourse = getItem(position)
        val course = myCourse.toCourse()
        val subject = CourseSubjectClassifier.classify(course.subjectLevel)
        val binding = holder.binding

        CoursesItemUtils.bindCover(context, ListViewMode.LIST, course, subject, binding.coverContainer, binding.ivCover, binding.ivSubjectIcon)
        binding.title.text = course.courseTitle
        binding.tvMeta.text = CoursesItemUtils.buildMetaLine(context, course)
        binding.isMyCourse.visibility = if (course.isMyCourse) View.VISIBLE else View.GONE
        binding.checkbox.visibility = View.GONE
        binding.statusBadge.visibility = View.GONE

        binding.root.setOnClickListener {
            if (listener != null) {
                val b = Bundle()
                b.putString("id", course.courseId)
                listener?.openCallFragment(TakeCourseFragment.newInstance(b))
            }
        }

        if (canRemove) {
            binding.ivChevron.setImageResource(R.drawable.baseline_close_24)
            binding.ivChevron.contentDescription = removeLabel
            binding.ivChevron.setOnClickListener { onRemove(myCourse) }
        } else {
            binding.ivChevron.setImageResource(R.drawable.ic_right_arrow)
            binding.ivChevron.contentDescription = null
            binding.ivChevron.setOnClickListener(null)
        }
    }

    class ViewHolder(val binding: ItemCourseListBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<MyCourse>(
            { oldItem, newItem -> oldItem.id == newItem.id },
            { oldItem, newItem ->
                oldItem.courseTitle == newItem.courseTitle &&
                        oldItem.description == newItem.description
            }
        )
    }
}
