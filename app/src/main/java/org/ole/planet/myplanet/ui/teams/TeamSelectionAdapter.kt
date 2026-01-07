package org.ole.planet.myplanet.ui.teams

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.utilities.DiffUtils

class TeamSelectionAdapter(private val section: String, private val onClick: (RealmMyTeam) -> Unit) :
    ListAdapter<RealmMyTeam, TeamSelectionAdapter.TeamSelectionViewHolder>(
        DiffUtils.itemCallback<RealmMyTeam>(
            { old, new -> old._id == new._id },
            { old, new -> old.name == new.name }
        )
    ) {
    inner class TeamSelectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textView)
        private val teamIcon: ImageView = itemView.findViewById(R.id.teamIcon)

        fun bind(item: RealmMyTeam) {
            textView.text = item.name
            if (section == itemView.context.getString(R.string.teams)) {
                teamIcon.setImageResource(R.drawable.team)
            } else {
                teamIcon.setImageResource(R.drawable.business)
            }
            itemView.setOnClickListener { onClick(item) }
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
