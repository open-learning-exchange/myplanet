package org.ole.planet.myplanet.ui.voices

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.databinding.FragmentNewsBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsViewData
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.textChanges

@AndroidEntryPoint
class NewsFragment : BaseNewsFragment() {
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!
    var user: RealmUserModel? = null

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var voicesRepository: VoicesRepository
    @Inject
    lateinit var teamsRepository: TeamsRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private var allNewsList: MutableList<NewsViewData> = mutableListOf()
    private var filteredNewsList: List<NewsViewData> = listOf()
    private lateinit var etSearch: EditText
    private var selectedLabel: String = "All"
    private var currentPage = 1
    private val pageSize = 20
    private var isLoading = false
    private var isLastPage = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        llImage = binding.llImages
        user = userProfileDbHandler.getUserModelCopy()
        setupUI(binding.newsFragmentParentLayout, requireActivity())
        if (user?.id?.startsWith("guest") == true) {
            binding.btnNewVoice.visibility = View.GONE
        }
        etSearch = binding.root.findViewById(R.id.et_search)
        binding.btnNewVoice.setOnClickListener {
            binding.llAddNews.visibility = if (binding.llAddNews.isVisible) {
                binding.etMessage.setText("")
                binding.tlMessage.error = null
                clearImages()
                View.GONE
            } else {
                View.VISIBLE
            }
            binding.btnNewVoice.text = if (binding.llAddNews.isVisible) {
                getString(R.string.hide_new_voice)
            } else {
                getString(R.string.new_voice)
            }
        }
        if (requireArguments().getBoolean("fromLogin")) {
            binding.btnNewVoice.visibility = View.GONE
            binding.llAddNews.visibility = View.GONE
        }

        setupSearchTextListener()
        setupLabelFilter()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupScrollListener()
        loadNews()
    }

    private fun loadNews() {
        if (isLoading || isLastPage) return
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            voicesRepository.getCommunityNews(getUserIdentifier(), currentPage, pageSize).collect { newsList ->
                if (newsList.isEmpty()) {
                    isLastPage = true
                    binding.progressBar.visibility = View.GONE
                    isLoading = false
                    return@collect
                }
                val newsViewDataList = newsList.map {
                    NewsMapper.toNewsViewData(it, requireContext(), user, teamsRepository, userRepository, voicesRepository)
                }
                allNewsList.addAll(newsViewDataList)
                filteredNewsList = applyLabelFilter(allNewsList)
                setData(applySearchFilter(filteredNewsList))
                updateLabelSpinner(collectAllLabels(allNewsList))
                currentPage++
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupScrollListener() {
        binding.rvNews.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0) {
                        loadNews()
                    }
                }
            }
        })
    }

    private fun getUserIdentifier(): String {
        val defaultUserIdentifier = "${user?.planetCode ?: ""}@${user?.parentCode ?: ""}"
        if (defaultUserIdentifier.isNotEmpty() && defaultUserIdentifier != "@") {
            return defaultUserIdentifier
        }
        val planetCode = requireActivity().getSharedPreferences(Constants.PREFS_NAME, requireContext().MODE_PRIVATE).getString("planetCode", "") ?: ""
        val parentCode = requireActivity().getSharedPreferences(Constants.PREFS_NAME, requireContext().MODE_PRIVATE).getString("parentCode", "") ?: ""
        return "$planetCode@$parentCode"
    }

    private fun setData(list: List<NewsViewData>?) {
        if (!isAdded || list == null) return
        if (binding.rvNews.adapter == null) {
            adapterNews = NewsAdapter(requireActivity())
            adapterNews.setListener(this)
            binding.rvNews.adapter = adapterNews
        }
        adapterNews.submitList(list)
        showNoData(binding.tvMessage, adapterNews.itemCount, "news")
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
            .setTitle("Delete News")
            .setMessage("Are you sure you want to delete this news?")
            .setPositiveButton("Yes") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    news?.id?.let { voicesRepository.deleteNews(it) }
                    allNewsList.remove(news)
                    filteredNewsList = applyLabelFilter(allNewsList)
                    setData(applySearchFilter(filteredNewsList))
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
                filteredNewsList = applyLabelFilter(allNewsList)
                setData(applySearchFilter(filteredNewsList))
            }
        }
    }
    //...
}