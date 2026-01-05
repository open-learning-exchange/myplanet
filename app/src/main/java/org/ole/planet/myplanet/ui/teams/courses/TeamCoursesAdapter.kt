package org.ole.planet.myplanet.ui.teams.courses

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.utilities.DiffUtils

class TeamCoursesAdapter(
    private val context: Context,
    private val settings: SharedPreferences,
    private val teamCreator: String
) : ListAdapter<RealmMyCourse, TeamCoursesAdapter.ViewHolder>(COURSE_DIFF_CALLBACK) {
    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val course = getItem(position)
        holder.binding.tvTitle.text = course.courseTitle
        holder.binding.tvDescription.text = course.description
        holder.binding.root.setOnClickListener {
            if (listener != null) {
                val b = Bundle()
                b.putString("id", course.courseId)
                listener?.openCallFragment(TakeCourseFragment.newInstance(b))
            }
        }
        if (!settings.getString("userId", "--").equals(teamCreator, ignoreCase = true)) {
            holder.binding.ivRemove.visibility = View.GONE
        }
    }

    class ViewHolder(val binding: RowTeamResourceBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val COURSE_DIFF_CALLBACK = DiffUtils.itemCallback<RealmMyCourse>(
            areItemsTheSame = { oldItem, newItem -> oldItem.courseId == newItem.courseId },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
