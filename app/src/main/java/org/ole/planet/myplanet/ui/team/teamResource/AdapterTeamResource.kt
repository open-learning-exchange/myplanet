package org.ole.planet.myplanet.ui.team.teamResource

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary

class AdapterTeamResource(
    private val context: Context,
    private val list: MutableList<RealmMyLibrary>,
    private val canRemoveResources: Boolean,
    private val updateListener: ResourceUpdateListner,
    private val onRemoveResource: (RealmMyLibrary, Int) -> Unit,
) : RecyclerView.Adapter<AdapterTeamResource.ViewHolderTeamResource>() {

    private var listener: OnHomeItemClickListener? = null

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

        holder.rowTeamResourceBinding.ivRemove.apply {
            visibility = if (canRemoveResources) View.VISIBLE else View.GONE
            setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onRemoveResource(resource, adapterPosition)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun removeResourceAt(position: Int) {
        if (position < 0 || position >= list.size) return
        list.removeAt(position)
        notifyItemRemoved(position)
        updateListener.onResourceListUpdated()
    }

    class ViewHolderTeamResource(val rowTeamResourceBinding: RowTeamResourceBinding) : RecyclerView.ViewHolder(rowTeamResourceBinding.root)
}
