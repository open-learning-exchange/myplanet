package org.ole.planet.myplanet.ui.team.teamResource

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utilities.DiffUtils

class AdapterTeamResource(
    private val context: Context,
    private val canRemoveResources: Boolean,
    private val updateListener: ResourceUpdateListner,
    private val onRemoveResource: (RealmMyLibrary, Int) -> Unit,
) : ListAdapter<RealmMyLibrary, AdapterTeamResource.ViewHolderTeamResource>(ITEM_CALLBACK) {

    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeamResource {
        val binding = RowTeamResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderTeamResource(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeamResource, position: Int) {
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition == RecyclerView.NO_POSITION) return

        val resource = getItem(adapterPosition)

        holder.binding.apply {
            tvTitle.text = resource.title
            tvDescription.text = resource.description

            root.setOnClickListener {
                listener?.openLibraryDetailFragment(resource)
            }

            ivRemove.apply {
                visibility = if (canRemoveResources) View.VISIBLE else View.GONE
                setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        onRemoveResource(getItem(currentPosition), currentPosition)
                    }
                }
            }
        }
    }

    fun removeResourceAt(position: Int) {
        if (position < 0 || position >= currentList.size) return
        val newList = currentList.toMutableList()
        newList.removeAt(position)
        submitList(newList)
        updateListener.onResourceListUpdated()
    }

    class ViewHolderTeamResource(val binding: RowTeamResourceBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val ITEM_CALLBACK = DiffUtils.itemCallback<RealmMyLibrary>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem.title == newItem.title && oldItem.description == newItem.description }
        )
    }
}
