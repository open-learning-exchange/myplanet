package org.ole.planet.myplanet.ui.community

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Case
import io.realm.RealmResults
import io.realm.Sort
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCommunityBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.ui.resources.ResourcesFragment

class CommunityFragment : BaseContainerFragment(), AdapterNews.OnNewsItemClickListener {
    private lateinit var fragmentCommunityBinding: FragmentCommunityBinding
    private var newList: RealmResults<RealmNews>? = null
    override fun addImage(llImage: LinearLayout?) {}
    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {}

    override fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean) {
        if (news != null) {
            startActivity(Intent(activity, ReplyActivity::class.java).putExtra("id", news.id).putExtra("fromLogin", fromLogin))
        }
    }

    var user: RealmUserModel? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCommunityBinding = FragmentCommunityBinding.inflate(inflater, container, false)
        newList = mRealm.where(RealmNews::class.java).equalTo("docType", "message", Case.INSENSITIVE)
            .equalTo("viewableBy", "community", Case.INSENSITIVE)
            .equalTo("createdOn", user?.planetCode, Case.INSENSITIVE).isEmpty("replyTo")
            .sort("time", Sort.DESCENDING).findAll()

        newList?.addChangeListener { results ->
            updatedNewsList(results)
        }
        return fragmentCommunityBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRealm = DatabaseService(requireActivity()).realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel
        fragmentCommunityBinding.btnLibrary.setOnClickListener {
            homeItemClickListener?.openCallFragment(ResourcesFragment())
        }
        newList = mRealm.where(RealmNews::class.java).equalTo("docType", "message", Case.INSENSITIVE)
            .equalTo("viewableBy", "community", Case.INSENSITIVE)
            .equalTo("createdOn", user?.planetCode, Case.INSENSITIVE).isEmpty("replyTo")
            .sort("time", Sort.DESCENDING).findAll()
        val orientation = resources.configuration.orientation
        changeLayoutManager(orientation)
        updatedNewsList(newList)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig.orientation
        changeLayoutManager(orientation)
    }

    private fun changeLayoutManager(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            fragmentCommunityBinding.rvCommunity.layoutManager = GridLayoutManager(activity, 2)
        } else {
            fragmentCommunityBinding.rvCommunity.layoutManager = LinearLayoutManager(activity)
        }
    }

    private fun updatedNewsList(updatedList: RealmResults<RealmNews>?) {
        activity?.runOnUiThread {
            val updatedListAsMutable: MutableList<RealmNews?> = updatedList?.toMutableList() ?: mutableListOf()
            val adapter = activity?.let { AdapterNews(it, updatedListAsMutable, user, null, "") }
            adapter?.setListener(this)
            adapter?.setFromLogin(requireArguments().getBoolean("fromLogin", false))
            adapter?.setmRealm(mRealm)
            fragmentCommunityBinding.rvCommunity.adapter = adapter
            fragmentCommunityBinding.llEditDelete.visibility = if (user?.isManager() == true) View.VISIBLE else View.GONE
            adapter?.notifyDataSetChanged()
        }
    }
}