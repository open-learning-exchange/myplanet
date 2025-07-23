package org.ole.planet.myplanet.ui.team.teamResource

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamLeader
import org.ole.planet.myplanet.ui.team.teamResource.ResourceUpdateListner

class AdapterTeamResource(
    private val context: Context,
    private var list: MutableList<RealmMyLibrary>,
    private val mRealm: Realm,
    teamId: String?,
    private val settings: SharedPreferences,
    private val updateListener: ResourceUpdateListner
) : RecyclerView.Adapter<AdapterTeamResource.ViewHolderTeamResource>() {

    private var listener: OnHomeItemClickListener? = null
    private val teamLeader: String = getTeamLeader(teamId, mRealm)

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeamResource {
        val rowTeamResourceBinding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTeamResource(rowTeamResourceBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeamResource, position: Int) {
        val resource = list[position]

        holder.rowTeamResourceBinding.tvTitle.text = resource.title
        holder.rowTeamResourceBinding.tvDescription.text = resource.description

        holder.itemView.setOnClickListener {
            listener?.openLibraryDetailFragment(resource)
        }

        holder.rowTeamResourceBinding.ivRemove.setOnClickListener {
            removeResource(resource, position)
        }

        val isLeader = settings.getString("userId", "--").equals(teamLeader, ignoreCase = true)
        if (!isLeader) {
            holder.rowTeamResourceBinding.ivRemove.visibility = View.GONE
        }else{
            holder.rowTeamResourceBinding.ivRemove.visibility = View.VISIBLE

        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun removeResource(resource: RealmMyLibrary, position: Int) {
        if (position < 0 || position >= list.size) return

        val itemToDelete = mRealm.where(RealmMyTeam::class.java)
            .equalTo("resourceId", resource.id)
            .findFirst()

        if (itemToDelete != null) {
            mRealm.executeTransaction {
                itemToDelete.resourceId = ""
                itemToDelete.updated = true
            }
        }

        list.removeAt(position)
        notifyItemRemoved(position)

        if (list.isEmpty()) {
            updateListener.onResourceListUpdated()
        }
    }

    fun updateResourceList(newList: List<RealmMyLibrary>) {
        val diffCallback = TeamResourceDiffCallback()
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = list.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                diffCallback.areItemsTheSame(list[oldItemPosition], newList[newItemPosition])
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                diffCallback.areContentsTheSame(list[oldItemPosition], newList[newItemPosition])
        })
        list.clear()
        list.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    class ViewHolderTeamResource(val rowTeamResourceBinding: RowTeamResourceBinding) : RecyclerView.ViewHolder(rowTeamResourceBinding.root)

    private class TeamResourceDiffCallback {
        fun areItemsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
            return oldItem.id == newItem.id
        }

        fun areContentsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
            return oldItem.id == newItem.id &&
                    oldItem.title == newItem.title &&
                    oldItem.description == newItem.description &&
                    oldItem.resourceLocalAddress == newItem.resourceLocalAddress
        }
    }
}
