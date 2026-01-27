package org.ole.planet.myplanet.ui.teams.courses

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment

class TeamCoursesAdapter(
    private val context: Context,
    private var list: MutableList<RealmMyCourse>,
    private val teamsRepository: TeamsRepository,
    private val teamId: String?,
    private val settings: SharedPreferences
) : RecyclerView.Adapter<TeamCoursesAdapter.ViewHolder>() {
    private var listener: OnHomeItemClickListener? = null
    private var teamCreator: String = ""

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        CoroutineScope(Dispatchers.IO).launch {
            teamCreator = teamsRepository.getTeamCreator(teamId)
            withContext(Dispatchers.Main) {
                notifyDataSetChanged()
            }
        }
    }

    fun getList(): List<RealmMyCourse> = list

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val course = list[position]
        holder.binding.tvTitle.text = course.courseTitle
        holder.binding.tvDescription.text = course.description
        holder.binding.root.setOnClickListener {
            if (listener != null) {
                val b = Bundle()
                b.putString("id", course.courseId)
                listener?.openCallFragment(TakeCourseFragment.newInstance(b))
            }
        }
        if (teamCreator.isNotEmpty() && !settings.getString("userId", "--").equals(teamCreator, ignoreCase = true)) {
            holder.binding.ivRemove.visibility = View.GONE
        } else {
            // Keep original visibility logic or force visible if creator matches?
            // Assuming default layout has it visible or logic elsewhere handles it.
            // If teamCreator is empty (still loading), we might default to hidden or wait.
            // For now, if loaded and match, show (or don't hide).
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(val binding: RowTeamResourceBinding) : RecyclerView.ViewHolder(binding.root)
}
