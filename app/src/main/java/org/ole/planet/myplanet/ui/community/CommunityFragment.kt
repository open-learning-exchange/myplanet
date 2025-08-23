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
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.FragmentCommunityBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.ui.resources.ResourcesFragment

import javax.inject.Inject

@AndroidEntryPoint
class CommunityFragment : BaseContainerFragment(), AdapterNews.OnNewsItemClickListener {
    private lateinit var fragmentCommunityBinding: FragmentCommunityBinding
    @Inject
    lateinit var newsRepository: NewsRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    override fun addImage(llImage: LinearLayout?) {}
    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {}
    override fun onDataChanged() {}

    override fun showReply(news: RealmNews?, fromLogin: Boolean, nonTeamMember: Boolean) {
        if (news != null) {
            startActivity(Intent(activity, ReplyActivity::class.java).putExtra("id", news.id).putExtra("fromLogin", fromLogin))
        }
    }

    var user: RealmUserModel? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentCommunityBinding = FragmentCommunityBinding.inflate(inflater, container, false)
        return fragmentCommunityBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        user = userProfileDbHandler.userModel
        fragmentCommunityBinding.btnLibrary.setOnClickListener {
            homeItemClickListener?.openCallFragment(ResourcesFragment())
        }
        val orientation = resources.configuration.orientation
        changeLayoutManager(orientation)
        fetchAndDisplayNews()
    }

    private fun fetchAndDisplayNews() {
        lifecycleScope.launch {
            val newsList = newsRepository.getNews(user?.id, user?.planetCode, user?.parentCode)
            updatedNewsList(newsList)
        }
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

    private fun updatedNewsList(updatedList: List<RealmNews?>) {
        activity?.runOnUiThread {
            val updatedListAsMutable: MutableList<RealmNews?> = updatedList.toMutableList()
            val adapter = activity?.let {
                AdapterNews(it, updatedListAsMutable, user, null, "", null, userProfileDbHandler, newsRepository, viewLifecycleOwner)
            }
            adapter?.setListener(this)
            adapter?.setFromLogin(requireArguments().getBoolean("fromLogin", false))
            fragmentCommunityBinding.rvCommunity.adapter = adapter
            fragmentCommunityBinding.llEditDelete.visibility = if (user?.isManager() == true) View.VISIBLE else View.GONE
            adapter?.notifyDataSetChanged()
        }
    }
}
