package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel

class AdapterLeader(var context: Context,var leaders: List<RealmUserModel>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        var v = LayoutInflater.from(context).inflate(R.layout.row_joined_user, parent, false)
        return ViewHolderLeader(v)
    }

    override fun getItemCount(): Int {
        return leaders!!.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if(holder is ViewHolderLeader){
            holder.title.text = leaders[position].toString()
            holder.tv_description.text = leaders[position].email
        }
    }


    internal inner class ViewHolderLeader(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var title: TextView = itemView.findViewById(R.id.tv_title)
        var tv_description: TextView = itemView.findViewById(R.id.tv_description)
        var icon: ImageView = itemView.findViewById(R.id.ic_more)
    }

}
