package org.ole.planet.myplanet.ui.team.teamCourse

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamCreator
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.team.teamCourse.AdapterTeamCourse.ViewHolderTeamCourse

class AdapterTeamCourse(private val context: Context, private var list: MutableList<RealmMyCourse>, mRealm: Realm?, teamId: String?, settings: SharedPreferences) : RecyclerView.Adapter<ViewHolderTeamCourse>() {
    private lateinit var rowTeamResourceBinding: RowTeamResourceBinding
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
    
    fun updateList(newList: List<RealmMyCourse>) {
        val diffCallback = TeamCourseDiffCallback(list, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        list.clear()
        list.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }
    
    fun getList(): List<RealmMyCourse> = list

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
    
    private class TeamCourseDiffCallback(
        private val oldList: List<RealmMyCourse>,
        private val newList: List<RealmMyCourse>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return try {
                oldList[oldItemPosition].courseId == newList[newItemPosition].courseId
            } catch (e: Exception) {
                false
            }
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return try {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                oldItem.courseTitle == newItem.courseTitle &&
                    oldItem.description == newItem.description &&
                    oldItem.createdDate == newItem.createdDate
            } catch (e: Exception) {
                false
            }
        }
    }
}
