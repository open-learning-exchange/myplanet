package org.ole.planet.myplanet.ui.team.teamResource

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamLeader

class AdapterTeamResource(
    private val context: Context,
    private val list: MutableList<RealmMyLibrary>,
    private val mRealm: Realm,
    teamId: String?,
    private val settings: SharedPreferences
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
        Log.d("team", "Binding resource: ${resource.title} at position: $position")

        holder.rowTeamResourceBinding.tvTitle.text = resource.title
        holder.rowTeamResourceBinding.tvDescription.text = resource.description

        holder.itemView.setOnClickListener {
            Log.d("team", "Opening details for: ${resource.title}")
            listener?.openLibraryDetailFragment(resource)
        }

        holder.rowTeamResourceBinding.ivRemove.setOnClickListener {
            Log.d("team", "Remove clicked for: ${resource.title}")
            removeResource(resource, position)
        }

        val isLeader = settings.getString("userId", "--").equals(teamLeader, ignoreCase = true)
        if (!isLeader) {
            Log.d("team", "User is not a team leader actual leader is ${teamLeader}, hiding remove button for: ${resource.title}")
            holder.rowTeamResourceBinding.ivRemove.visibility = View.GONE
        }else{
            Log.d("team", "User is a team leader, showing remove button for: ${resource.title}")
            holder.rowTeamResourceBinding.ivRemove.visibility = View.VISIBLE

        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun removeResource(resource: RealmMyLibrary, position: Int) {
        if (position < 0 || position >= list.size) return
        Log.d("team", "Attempting to remove resource: ${resource.title} at position: $position")

        val itemToDelete = mRealm.where(RealmMyTeam::class.java)
            .equalTo("resourceId", resource.id)
            .findFirst()

        if (itemToDelete != null) {
            Log.d("team", "Marking resource as removed: ${resource.title}")
            mRealm.executeTransaction {
                itemToDelete.resourceId = ""
                itemToDelete.updated = true
            }
        }

        list.removeAt(position)
        notifyItemRemoved(position)
        Log.d("team", "Resource removed successfully from the list")
    }

    class ViewHolderTeamResource(val rowTeamResourceBinding: RowTeamResourceBinding) : RecyclerView.ViewHolder(rowTeamResourceBinding.root)
}
