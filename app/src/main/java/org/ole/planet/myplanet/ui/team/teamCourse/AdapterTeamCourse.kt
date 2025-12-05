package org.ole.planet.myplanet.ui.team.teamCourse

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.team.teamCourse.AdapterTeamCourse.ViewHolderTeamCourse

class AdapterTeamCourse(
    private val context: Context,
    private val teamCreator: String,
    private val settings: SharedPreferences
) : ListAdapter<RealmMyCourse, ViewHolderTeamCourse>(DIFF_CALLBACK) {
    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeamCourse {
        val binding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTeamCourse(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeamCourse, position: Int) {
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

    class ViewHolderTeamCourse(val binding: RowTeamResourceBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RealmMyCourse>() {
            override fun areItemsTheSame(oldItem: RealmMyCourse, newItem: RealmMyCourse): Boolean {
                return oldItem.courseId == newItem.courseId
            }

            override fun areContentsTheSame(oldItem: RealmMyCourse, newItem: RealmMyCourse): Boolean {
                return oldItem.courseTitle == newItem.courseTitle && oldItem.description == newItem.description
            }
        }
    }
}
