package org.ole.planet.myplanet.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyTeam

class GrandChildAdapter(private val items: List<RealmMyTeam>, private val section: String, private val onClick: (RealmMyTeam) -> Unit) : RecyclerView.Adapter<GrandChildAdapter.GrandChildViewHolder>() {
    inner class GrandChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textView)
        private val teamIcon: ImageView = itemView.findViewById(R.id.teamIcon)

        fun bind(item: RealmMyTeam) {
            textView.text = item.name
            if (section == itemView.context.getString(R.string.teams)) {
                teamIcon.setImageResource(R.drawable.team)
            } else {
                teamIcon.setImageResource(R.drawable.enterprises)
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GrandChildViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.expandable_list_grand_child_item, parent, false)
        return GrandChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: GrandChildViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
