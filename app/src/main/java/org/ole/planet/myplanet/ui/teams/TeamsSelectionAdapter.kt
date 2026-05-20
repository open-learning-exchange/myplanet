package org.ole.planet.myplanet.ui.teams

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.utils.DiffUtils

class TeamsSelectionAdapter(
    private val section: String,
    private val sharedIds: Set<String> = emptySet(),
    private val onClick: (TeamSummary) -> Unit
) : ListAdapter<TeamSummary, TeamsSelectionAdapter.TeamSelectionViewHolder>(
        DiffUtils.itemCallback<TeamSummary>(
            { old, new -> old._id == new._id },
            { old, new -> old.name == new.name }
        )
    ) {
    inner class TeamSelectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textView)
        private val teamIcon: ImageView = itemView.findViewById(R.id.teamIcon)
        private val sharedIcon: ImageView = itemView.findViewById(R.id.sharedIcon)

        fun bind(item: TeamSummary) {
            textView.text = item.name
            if (section == itemView.context.getString(R.string.teams)) {
                teamIcon.setImageResource(R.drawable.team)
            } else {
                teamIcon.setImageResource(R.drawable.business)
            }
            val alreadyShared = item._id in sharedIds
            sharedIcon.visibility = if (alreadyShared) View.VISIBLE else View.GONE
            if (alreadyShared) {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            } else {
                itemView.isClickable = true
                itemView.setOnClickListener { onClick(item) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamSelectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.expandable_list_grand_child_item, parent, false)
        return TeamSelectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeamSelectionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
