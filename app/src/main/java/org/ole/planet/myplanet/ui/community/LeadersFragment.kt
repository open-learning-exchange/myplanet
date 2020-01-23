package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_members.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import java.util.ArrayList

class LeadersFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.fragment_members, container, false)
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var mRealm = DatabaseService(activity!!).realmInstance;
        val leaders = mRealm.where(RealmMyTeam::class.java).equalTo("isLeader", true).findAll()
        if (leaders.isEmpty()){
            tv_nodata.text = "No data available"
        }else{
            rv_member.layoutManager = GridLayoutManager(activity, 2)
            val list = ArrayList<RealmUserModel>()
            for (team in leaders) {
                val model = mRealm.where(RealmUserModel::class.java).equalTo("id", team.getUser_id()).findFirst()
                if (model != null && !list.contains(model))
                    list.add(model)
            }
            rv_member.adapter = AdapterLeader(activity!!, list)
        }

    }



}