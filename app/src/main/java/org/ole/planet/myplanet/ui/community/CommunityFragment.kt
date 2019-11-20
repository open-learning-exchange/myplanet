package org.ole.planet.myplanet.ui.community


import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import kotlinx.android.synthetic.main.alert_add_link.view.*
import kotlinx.android.synthetic.main.fragment_community.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.library.LibraryFragment
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class CommunityFragment : BaseContainerFragment(){

    var user: RealmUserModel? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.fragment_community, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mRealm = DatabaseService(activity!!).realmInstance
        user = UserProfileDbHandler(activity!!).userModel
        btn_library.setOnClickListener {
            homeItemClickListener.openCallFragment(LibraryFragment())
        }
        val list = mRealm.where(RealmNews::class.java)
                .equalTo("docType", "message", Case.INSENSITIVE)
                .equalTo("viewableBy", "community", Case.INSENSITIVE)
                .equalTo("createdOn", user?.planetCode, Case.INSENSITIVE)
                .findAll()
        rv_community.layoutManager = LinearLayoutManager(activity!!)
        Utilities.log("list size " + list.size)
        var adapter = AdapterNews(activity, list, user, null);
        adapter.setmRealm(mRealm)
        rv_community.adapter = adapter
        ic_add.setOnClickListener {
//            showAddLinkForm()
            AddLinkFragment().show(childFragmentManager, "")
        }

    }


}
