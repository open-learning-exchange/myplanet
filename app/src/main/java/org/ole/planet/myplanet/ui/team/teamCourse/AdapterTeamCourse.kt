package org.ole.planet.myplanet.ui.team.teamCourse

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamCreator
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.team.teamCourse.AdapterTeamCourse.ViewHolderTeamCourse
import org.ole.planet.myplanet.utilities.DiffUtils

class AdapterTeamCourse(
    private val context: Context,
    mRealm: Realm?,
    teamId: String?,
    settings: SharedPreferences
) : ListAdapter<RealmMyCourse, ViewHolderTeamCourse>(diffCallback) {
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
        val diffCallback = DiffUtils.itemCallback<RealmMyCourse>(
            areItemsTheSame = { old, new -> old.courseId == new.courseId },
            areContentsTheSame = { old, new -> old.courseTitle == new.courseTitle && old.description == new.description }
        )
    }
}
