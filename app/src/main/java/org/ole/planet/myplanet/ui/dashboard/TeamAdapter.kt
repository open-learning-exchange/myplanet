package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamHomeBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import io.realm.RealmResults

class TeamAdapter(
    private val teams: RealmResults<RealmMyTeam>,
    private val clickListener: (RealmMyTeam) -> Unit,
    private val viewTeamListener: (RealmMyTeam) -> Unit
) : RecyclerView.Adapter<TeamAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTeamHomeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val team = teams[position]
        holder.bind(team, position)
        holder.itemView.setOnClickListener { clickListener(team) }
        holder.binding.btnViewTeam.setOnClickListener { viewTeamListener(team) }
    }

    override fun getItemCount(): Int = teams.size

    inner class ViewHolder(val binding: ItemTeamHomeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(team: RealmMyTeam, position: Int) {
            binding.tvName.text = team.name
            val colorResId = if (position % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, colorResId))
        }
    }
}