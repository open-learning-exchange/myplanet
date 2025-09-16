package org.ole.planet.myplanet.ui.community

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
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

@AndroidEntryPoint
class CommunityFragment : BaseContainerFragment(), AdapterNews.OnNewsItemClickListener {
    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!
    private var newList: List<RealmNews> = emptyList()
    private var loadNewsJob: Job? = null

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    @Inject
    lateinit var newsRepository: NewsRepository
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
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        user = UserProfileDbHandler(requireActivity()).userModel
        binding.btnLibrary.setOnClickListener {
            homeItemClickListener?.openCallFragment(ResourcesFragment())
        }
        val orientation = resources.configuration.orientation
        changeLayoutManager(orientation)
        loadCommunityNews()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig.orientation
        changeLayoutManager(orientation)
    }

    override fun onDataChanged() {
        loadCommunityNews()
    }

    private fun changeLayoutManager(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.rvCommunity.layoutManager = GridLayoutManager(activity, 2)
        } else {
            binding.rvCommunity.layoutManager = LinearLayoutManager(activity)
        }
    }

    private fun loadCommunityNews() {
        val planetCode = user?.planetCode
        loadNewsJob?.cancel()
        loadNewsJob = viewLifecycleOwner.lifecycleScope.launch {
            newList = planetCode?.let { newsRepository.getCommunityNews(it) } ?: emptyList()
            updatedNewsList(newList)
        }
    }

    private fun updatedNewsList(updatedList: List<RealmNews>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val updatedListAsMutable: MutableList<RealmNews?> = updatedList.toMutableList()
            val adapter = activity?.let { AdapterNews(it, user, null, "", null, userProfileDbHandler) }
            adapter?.setmRealm(mRealm)
            adapter?.setListener(this@CommunityFragment)
            adapter?.setFromLogin(requireArguments().getBoolean("fromLogin", false))
            adapter?.updateList(updatedListAsMutable)
            binding.rvCommunity.adapter = adapter
            binding.llEditDelete.visibility = if (user?.isManager() == true) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadNewsJob?.cancel()
        _binding = null
    }
}
