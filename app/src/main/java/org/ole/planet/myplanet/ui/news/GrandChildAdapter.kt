package org.ole.planet.myplanet.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R

class GrandChildAdapter(private val items: List<String?>, private val onItemClicked: (String) -> Unit) : RecyclerView.Adapter<GrandChildAdapter.GrandChildViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GrandChildViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.expandable_list_grand_child_item, parent, false)
        return GrandChildViewHolder(view)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: GrandChildViewHolder, position: Int) {
        items[position]?.let { holder.bind(it) }
    }

    inner class GrandChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textView)

        fun bind(item: String) {
            textView.text = item
            itemView.setOnClickListener {
                onItemClicked(item)
            }
        }
    }
}
