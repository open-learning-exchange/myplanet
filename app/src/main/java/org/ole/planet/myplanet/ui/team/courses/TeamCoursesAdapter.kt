package org.ole.planet.myplanet.ui.team.courses

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamCreator
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment

class TeamCoursesAdapter(
    private val context: Context,
    private var list: MutableList<RealmMyCourse>,
    mRealm: Realm?,
    teamId: String?,
    settings: SharedPreferences
) : RecyclerView.Adapter<TeamCoursesAdapter.ViewHolder>() {
    private var listener: OnHomeItemClickListener? = null
    private val settings: SharedPreferences
    private val teamCreator: String

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        this.settings = settings
        teamCreator = getTeamCreator(teamId, mRealm)
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
        if (!settings.getString("userId", "--").equals(teamCreator, ignoreCase = true)) {
            holder.binding.ivRemove.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(val binding: RowTeamResourceBinding) : RecyclerView.ViewHolder(binding.root)
}
