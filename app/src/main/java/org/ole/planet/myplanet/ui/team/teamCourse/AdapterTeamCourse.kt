package org.ole.planet.myplanet.ui.team.teamCourse

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.team.teamCourse.AdapterTeamCourse.ViewHolderTeamCourse

class AdapterTeamCourse(private val context: Context, private val list: List<RealmMyCourse>, mRealm: Realm?, teamId: String?, settings: SharedPreferences) : RecyclerView.Adapter<ViewHolderTeamCourse>() {
    private lateinit var rowTeamResourceBinding: RowTeamResourceBinding
    private var listener: OnHomeItemClickListener? = null
    private val settings: SharedPreferences
    private val teamCreator: String

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        this.settings = settings
        teamCreator = RealmMyTeam.getTeamCreator(teamId, mRealm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeamCourse {
        rowTeamResourceBinding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTeamCourse(rowTeamResourceBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeamCourse, position: Int) {
        rowTeamResourceBinding.tvTitle.text = list[position].courseTitle
        rowTeamResourceBinding.tvDescription.text = list[position].description
        holder.itemView.setOnClickListener {
            if (listener != null) {
                val b = Bundle()
                b.putString("id", list[position].courseId)
                listener?.openCallFragment(TakeCourseFragment.newInstance(b))
            }
        }
        if (!settings.getString("userId", "--").equals(teamCreator, ignoreCase = true)) {
            holder.itemView.findViewById<View>(R.id.iv_remove).visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderTeamCourse(rowTeamResourceBinding: RowTeamResourceBinding) :
        RecyclerView.ViewHolder(rowTeamResourceBinding.root)
}
