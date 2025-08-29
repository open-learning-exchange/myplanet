package org.ole.planet.myplanet.ui.team.teamResource

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamLeader
import org.ole.planet.myplanet.utilities.DiffUtils

class AdapterTeamResource(
    private val context: Context,
    resources: List<RealmMyLibrary>,
    private val mRealm: Realm,
    teamId: String?,
    private val settings: SharedPreferences,
    private val updateListener: ResourceUpdateListner
) : ListAdapter<RealmMyLibrary, AdapterTeamResource.ViewHolderTeamResource>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem ->
            oldItem.title == newItem.title &&
                oldItem.description == newItem.description
        }
    )
) {

    private var listener: OnHomeItemClickListener? = null
    private val teamLeader: String = getTeamLeader(teamId, mRealm)

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        submitList(resources)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeamResource {
        val rowTeamResourceBinding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTeamResource(rowTeamResourceBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeamResource, position: Int) {
        val resource = getItem(position)

        holder.rowTeamResourceBinding.tvTitle.text = resource.title
        holder.rowTeamResourceBinding.tvDescription.text = resource.description

        holder.itemView.setOnClickListener {
            listener?.openLibraryDetailFragment(resource)
        }

        holder.rowTeamResourceBinding.ivRemove.setOnClickListener {
            removeResource(resource, position, holder.itemView)
        }

        val isLeader = settings.getString("userId", "--").equals(teamLeader, ignoreCase = true)
        if (!isLeader) {
            holder.rowTeamResourceBinding.ivRemove.visibility = View.GONE
        } else {
            holder.rowTeamResourceBinding.ivRemove.visibility = View.VISIBLE

        }
    }

    fun removeResource(resource: RealmMyLibrary, position: Int, view: View? = null) {
        if (position < 0 || position >= currentList.size) return

        val resourceId = resource.id

        mRealm.executeTransactionAsync({ realm ->
            val itemToDelete = realm.where(RealmMyTeam::class.java)
                .equalTo("resourceId", resourceId)
                .findFirst()

            itemToDelete?.let {
                it.resourceId = ""
                it.updated = true
            }
        }, {
            val newList = currentList.toMutableList().apply { removeAt(position) }
            submitList(newList)

            if (newList.isEmpty()) {
                updateListener.onResourceListUpdated()
            }
        }, {
            view?.let {
                Snackbar.make(it, context.getString(R.string.failed_to_remove_resource), Snackbar.LENGTH_LONG).show()
            }
        })
    }

    class ViewHolderTeamResource(val rowTeamResourceBinding: RowTeamResourceBinding) : RecyclerView.ViewHolder(rowTeamResourceBinding.root)
}
