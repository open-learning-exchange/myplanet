package org.ole.planet.myplanet.ui.team.teamResource

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamCreator

class AdapterTeamResource(
    private val context: Context,
    private val list: MutableList<RealmMyLibrary>,
    private val mRealm: Realm,
    teamId: String?,
    private val settings: SharedPreferences
) : RecyclerView.Adapter<AdapterTeamResource.ViewHolderTeamResource>() {

    private var listener: OnHomeItemClickListener? = null
    private val teamCreator: String = getTeamCreator(teamId, mRealm)

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
        val isLeader =settings.getString("userId", "--").equals(teamCreator, ignoreCase = true)
        if (!isLeader) {
            holder.rowTeamResourceBinding.ivRemove.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun removeResource(resource: RealmMyLibrary, position: Int) {
        if (position < 0 || position >= list.size) return

        mRealm.executeTransaction { realm ->
            val itemToDelete = realm.where(RealmMyTeam::class.java)
                .equalTo("resourceId", resource.id)
                .findFirst()

            if (itemToDelete != null) {
                itemToDelete.resourceId = ""
                itemToDelete.updated = true
            }
        }

        list.removeAt(position)
        notifyItemRemoved(position)
    }

    class ViewHolderTeamResource(val rowTeamResourceBinding: RowTeamResourceBinding) : RecyclerView.ViewHolder(rowTeamResourceBinding.root)
}
