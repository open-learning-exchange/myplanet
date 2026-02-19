package org.ole.planet.myplanet.ui.teams.courses

import android.content.Context
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

class TeamCoursesAdapter(
    private val context: Context,
    private val canRemove: Boolean
) : ListAdapter<RealmMyCourse, TeamCoursesAdapter.ViewHolder>(DiffCallback) {
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
        if (!canRemove) {
            holder.binding.ivRemove.visibility = View.GONE
        } else {
            holder.binding.ivRemove.visibility = View.VISIBLE
        }
    }

    class ViewHolder(val binding: RowTeamResourceBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<RealmMyCourse>() {
            override fun areItemsTheSame(oldItem: RealmMyCourse, newItem: RealmMyCourse): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RealmMyCourse, newItem: RealmMyCourse): Boolean {
                return oldItem.courseTitle == newItem.courseTitle &&
                        oldItem.description == newItem.description
            }
        }
    }
}
