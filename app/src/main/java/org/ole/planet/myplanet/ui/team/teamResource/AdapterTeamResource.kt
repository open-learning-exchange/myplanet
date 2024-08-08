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
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getTeamCreator
import org.ole.planet.myplanet.ui.team.teamResource.AdapterTeamResource.ViewHolderTeamResource

class AdapterTeamResource(private val context: Context, private val list: List<RealmMyLibrary>, mRealm: Realm, teamId: String?, private val settings: SharedPreferences) : RecyclerView.Adapter<ViewHolderTeamResource>() {
    private lateinit var rowTeamResourceBinding: RowTeamResourceBinding
    private var listener: OnHomeItemClickListener? = null
    private val teamCreator: String = getTeamCreator(teamId, mRealm)

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeamResource {
        rowTeamResourceBinding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTeamResource(rowTeamResourceBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeamResource, position: Int) {
        rowTeamResourceBinding.tvTitle.text = list[position].title
        rowTeamResourceBinding.tvDescription.text = list[position].description
        holder.itemView.setOnClickListener {
            listener?.openLibraryDetailFragment(list[position])
        }
        rowTeamResourceBinding.ivRemove.setOnClickListener { }
        if (!settings.getString("userId", "--").equals(teamCreator, ignoreCase = true)) {
            rowTeamResourceBinding.ivRemove.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderTeamResource(rowTeamResourceBinding: RowTeamResourceBinding) : RecyclerView.ViewHolder(rowTeamResourceBinding.root)
}
