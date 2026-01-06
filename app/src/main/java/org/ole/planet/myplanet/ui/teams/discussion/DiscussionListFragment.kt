package org.ole.planet.myplanet.ui.teams.discussion

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentDiscussionListBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsViewData
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.teams.BaseTeamFragment
import org.ole.planet.myplanet.ui.voices.NewsActions
import org.ole.planet.myplanet.ui.voices.NewsAdapter
import org.ole.planet.myplanet.ui.voices.NewsMapper
import org.ole.planet.myplanet.ui.voices.ReplyActivity

@AndroidEntryPoint
class DiscussionListFragment : BaseTeamFragment() {
    private var _binding: FragmentDiscussionListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var voicesRepository: VoicesRepository
    @Inject
    lateinit var teamsRepository: TeamsRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    private var teamId: String? = null
    private var user: RealmUserModel? = null
    private val _isMemberFlow = MutableStateFlow(false)
    val isMemberFlow: StateFlow<Boolean> = _isMemberFlow
    private var allNewsList: MutableList<NewsViewData> = mutableListOf()
    private var currentPage = 1
    private val pageSize = 20
    private var isLoading = false
    private var isLastPage = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscussionListBinding.inflate(inflater, container, false)
        teamId = arguments?.getString("teamId")
        user = userProfileDbHandler.getUserModelCopy()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupScrollListener()
        loadDiscussions()
    }

    private fun loadDiscussions() {
        if (isLoading || isLastPage) return
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            voicesRepository.getDiscussionsByTeamIdFlow(teamId!!, currentPage, pageSize).collect { newsList ->
                if (newsList.isEmpty()) {
                    isLastPage = true
                    binding.progressBar.visibility = View.GONE
                    isLoading = false
                    return@collect
                }
                val newsViewDataList = newsList.map {
                    NewsMapper.toNewsViewData(it, requireContext(), user, teamsRepository, userRepository, voicesRepository, teamId)
                }
                allNewsList.addAll(newsViewDataList)
                setData(allNewsList)
                currentPage++
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupScrollListener() {
        binding.rvDiscussion.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0) {
                        loadDiscussions()
                    }
                }
            }
        })
    }
    override fun onNewsItemClick(news: NewsViewData?) {
        NewsActions.showEditAlert(requireContext(), voicesRepository, teamsRepository, userRepository, news?.id, false, user) { newReply ->
            val index = allNewsList.indexOfFirst { it.id == news?.id }
            if (index != -1) {
                val currentNews = allNewsList[index]
                val updatedNews = currentNews.copy(replyCount = currentNews.replyCount + 1)
                allNewsList[index] = updatedNews
                setData(allNewsList)
            }
        }
    }

    override fun showReply(news: NewsViewData?) {
        startActivity(Intent(requireActivity(), ReplyActivity::class.java).putExtra("id", news?.id))
    }

    override fun onDelete(news: NewsViewData?) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Discussion")
            .setMessage("Are you sure you want to delete this discussion?")
            .setPositiveButton("Yes") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    news?.id?.let { voicesRepository.deleteNews(it) }
                    allNewsList.remove(news)
                    setData(allNewsList)
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onEdit(news: NewsViewData?) {
        NewsActions.showEditAlert(requireContext(), voicesRepository, teamsRepository, userRepository, news?.id, true, user) { updatedNews ->
            val index = allNewsList.indexOfFirst { it.id == updatedNews.id }
            if (index != -1) {
                allNewsList[index] = updatedNews
                setData(allNewsList)
            }
        }
    }
    //...
}