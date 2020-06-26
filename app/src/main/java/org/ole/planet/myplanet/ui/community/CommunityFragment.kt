package org.ole.planet.myplanet.ui.community


import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.Case
import io.realm.Sort
import kotlinx.android.synthetic.main.fragment_community.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.library.LibraryFragment
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.utilities.Utilities

/**
 * A simple [Fragment] subclass.
 */
class CommunityFragment : BaseContainerFragment(), AdapterNews.OnNewsItemClickListener {
    override fun addImage(llImage: LinearLayout?) {
    }

    override fun showReply(news: RealmNews, fromLogin: Boolean) {
        startActivity(Intent(activity, ReplyActivity::class.java).putExtra("id", news?.id).putExtra("fromLogin", fromLogin))
    }




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
                .isEmpty("replyTo")
                .sort("time", Sort.DESCENDING)
                .findAll()
//        rv_community.layoutManager = LinearLayoutManager(activity!!)
        val orientation = resources.configuration.orientation
        changeLayoutManager(orientation)

        Utilities.log("list size " + list.size)
        var adapter = AdapterNews(activity, list, user, null)
        adapter.setListener(this)
        adapter.setFromLogin(arguments!!.getBoolean("fromLogin", false))
        adapter.setmRealm(mRealm)
        rv_community.adapter = adapter
      //  setFlexBox();
        ll_edit_delete.visibility = if (user!!.isManager()) View.VISIBLE else View.GONE


    }




    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig!!.orientation
        changeLayoutManager(orientation)
    }

    private fun changeLayoutManager(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rv_community.layoutManager = GridLayoutManager(activity, 2)
        } else {
            rv_community.layoutManager = LinearLayoutManager(activity)
        }
    }

}
