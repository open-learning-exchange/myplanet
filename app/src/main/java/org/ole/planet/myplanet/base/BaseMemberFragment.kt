package org.ole.planet.myplanet.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.team.BaseTeamFragment

abstract class BaseMemberFragment : BaseTeamFragment() {
    abstract val list: List<RealmUserModel?>
    abstract val adapter: RecyclerView.Adapter<*>?
    abstract val layoutManager: RecyclerView.LayoutManager?
    private lateinit var rvMember: RecyclerView
    private var tvNoData: TextView? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_members, container, false)
        rvMember = v.findViewById(R.id.rv_member)
        tvNoData = v.findViewById(R.id.tv_nodata)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvMember.layoutManager = layoutManager
        rvMember.adapter = adapter
        showNoData(tvNoData, list.size, "members")
    }
}
