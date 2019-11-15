package org.ole.planet.myplanet.ui.community


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import kotlinx.android.synthetic.main.fragment_community.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.news.AdapterNews

/**
 * A simple [Fragment] subclass.
 */
class CommunityFragment : Fragment() {
    lateinit var mRealm: Realm;
    var user: RealmUserModel? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.fragment_community, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val list = mRealm.where(RealmNews::class.java).sort("time", Sort.DESCENDING)
                .equalTo("docType", "message", Case.INSENSITIVE)
                .equalTo("viewableBy", "community", Case.INSENSITIVE)
                .equalTo("replyTo", "", Case.INSENSITIVE)
                .findAll()
        rv_community.layoutManager = LinearLayoutManager(activity!!)
        rv_community.adapter = AdapterNews(activity, list, user, null)

    }

}
