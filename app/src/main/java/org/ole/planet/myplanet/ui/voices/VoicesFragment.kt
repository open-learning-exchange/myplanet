package org.ole.planet.myplanet.ui.voices

import android.content.res.Configuration
import android.os.Bundle
import android.os.Trace
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseVoicesFragment
import org.ole.planet.myplanet.databinding.FragmentVoicesBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.VoicesLabelManager
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utils.textChanges

@AndroidEntryPoint
class VoicesFragment : BaseVoicesFragment() {
    private var _binding: FragmentVoicesBinding? = null
    private val binding get() = _binding!!
    var user: RealmUser? = null
    private val voicesViewModel: VoicesViewModel by viewModels()
    
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var voicesRepository: VoicesRepository
    @Inject
    lateinit var dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider
    private lateinit var etSearch: EditText
    private var labelAdapter: VoicesLabelAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVoicesBinding.inflate(inflater, container, false)
        llImage = binding.llImages
        setupUI(binding.voicesFragmentParentLayout, requireActivity())
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

        viewLifecycleOwner.lifecycleScope.launch {
            user = userSessionManager.getUserModel()
            if (user?.id?.startsWith("guest") == true) {
                binding.btnNewVoice.visibility = View.GONE
            }

            voicesViewModel.observeCommunityNews(getUserIdentifier())

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    voicesViewModel.filteredNews.collectLatest { searchFiltered ->
                        if (_binding != null) {
                            setData(searchFiltered)
                        }
                    }
                }
                launch {
                    combine(
                        voicesViewModel.labels,
                        voicesViewModel.selectedLabel
                    ) { labels, selected -> labels to selected }
                        .collectLatest { (labels, selected) ->
                            if (_binding != null) {
                                updateLabelSpinner(labels, selected)
                            }
                        }
                }
                launch {
                    voicesViewModel.createNewsSuccess.collectLatest { n ->
                        binding.btnSubmit.isEnabled = true
                        if (n != null) {
                            binding.etMessage.setText(R.string.empty_text)
                            binding.llAddNews.visibility = View.GONE
                            binding.btnNewVoice.text = getString(R.string.new_voice)
                            imageList.clear()
                            llImage?.removeAllViews()
                            scrollToTop()
                        } else {
                            org.ole.planet.myplanet.utils.Utilities.toast(requireContext(), getString(R.string.error, "Failed to create news"))
                        }
                    }
                }
            }
        }
        binding.btnSubmit.setOnClickListener {
            val message = binding.etMessage.text.toString().trim { it <= ' ' }
            if (message.isEmpty()) {
                binding.tlMessage.error = getString(R.string.please_enter_message)
                return@setOnClickListener
            }
            val map = HashMap<String?, String>()
            map["message"] = message
            map["viewInId"] = "${user?.planetCode ?: ""}@${user?.parentCode ?: ""}"
            map["viewInSection"] = "community"
            map["messageType"] = "sync"
            map["messagePlanetCode"] = user?.planetCode ?: ""

            binding.btnSubmit.isEnabled = false

            user?.let { it1 -> voicesViewModel.createNews(map, it1, imageList.toList()) }
        }

        binding.addNewsImage.setOnClickListener {
            llImage = binding.llImages
            val openFolderIntent = FileUtils.openOleFolder(requireContext())
            openFolderLauncher.launch(openFolderIntent)
        }
    }

    private fun getUserIdentifier(): String {
        val defaultUserIdentifier = "${user?.planetCode ?: ""}@${user?.parentCode ?: ""}"
        if (defaultUserIdentifier.isNotEmpty() && defaultUserIdentifier != "@") {
            return defaultUserIdentifier
        }
        val planetCode = sharedPrefManager.getPlanetCode()
        val parentCode = sharedPrefManager.getParentCode()
        return "$planetCode@$parentCode"
    }

    private val currentEmptyStateSource: String
        get() = if (etSearch.text.isNotEmpty() || voicesViewModel.selectedLabel.value != "All") "news_filtered" else "news"

    override fun setData(list: List<RealmNews?>?) {
        if (!isAdded || list == null) return

        if (binding.rvNews.adapter == null) {
            changeLayoutManager(resources.configuration.orientation, binding.rvNews)
            downloadResourcesForNews(list)
            val sortedList = sortNews(list)
            setupVoicesAdapter(sortedList.filterNotNull())
        } else {
            (binding.rvNews.adapter as? VoicesAdapter)?.submitList(list.filterNotNull())
        }
        showNoData(binding.tvMessage, list.filterNotNull().size, currentEmptyStateSource)
    }

    private fun downloadResourcesForNews(list: List<RealmNews?>) {
        val resourceIds = mutableSetOf<String>()
        list.forEach { news ->
            if ((news?.imagesArray?.size() ?: 0) > 0) {
                val ob = news?.imagesArray?.get(0)?.asJsonObject
                val resourceId = getString("resourceId", ob?.asJsonObject)
                if (!resourceId.isNullOrBlank()) {
                    resourceIds.add(resourceId)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (resourceIds.isNotEmpty()) {
                val libraries = resourcesRepository.getLibraryItemsByIds(resourceIds)
                resourcesRepository.downloadResources(libraries)
            }
        }
    }

    private fun sortNews(list: List<RealmNews?>): List<RealmNews?> {
        val updatedListAsMutable: MutableList<RealmNews?> = list.toMutableList()
        Trace.beginSection("VoicesFragment.sort")
        return try {
            updatedListAsMutable.sortedWith(compareByDescending { news ->
                news?.sortDate ?: 0L
            })
        } finally {
            Trace.endSection()
        }
    }

    private fun setupVoicesAdapter(sortedList: List<RealmNews>) {
        val labelManager = VoicesLabelManager(
            context = requireActivity(),
            scope = viewLifecycleOwner.lifecycleScope,
            dispatcherProvider = dispatcherProvider,
            addLabelFn = { newsId, label -> voicesViewModel.addLabel(newsId, label) },
            removeLabelFn = { newsId, label -> voicesViewModel.removeLabel(newsId, label) }
        )
        adapterNews = VoicesAdapter(
            context = requireActivity(),
            currentUser = user,
            parentNews = null,
            teamName = "",
            teamId = null,
            isTeamLeaderFn = { onResult -> onResult(false) },
            getUserFn = { userId, onResult ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = voicesViewModel.getUserById(userId)
                    onResult(result)
                }
            },
            getReplyCountFn = { newsId, onResult ->
                val job = viewLifecycleOwner.lifecycleScope.launch {
                    val result = voicesViewModel.getReplyCount(newsId)
                    onResult(result)
                }
                return@VoicesAdapter { job.cancel() }
            },
            deletePostFn = { newsId ->
                voicesViewModel.deletePost(newsId, "") {
                    adapterNews?.removePost(newsId)
                }
            },
            shareNewsFn = { newsId, userId, planetCode, parentCode, teamName ->
                voicesViewModel.shareNewsToCommunity(newsId, userId, planetCode, parentCode, teamName) { result ->
                    VoicesAdapterHelper.handleShareNewsResult(requireContext(), result)
                }
            },
            getLibraryResourceFn = { resourceId, onResult ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = voicesViewModel.getLibraryResource(resourceId)
                    onResult(result)
                }
            },
            onEditAction = { action ->
                viewLifecycleOwner.lifecycleScope.launch { action() }
            },
            onAnimateTyping = VoicesAdapterHelper.createOnAnimateTyping(viewLifecycleOwner.lifecycleScope),
            labelManager = labelManager,
            voicesRepository = voicesRepository,
            userRepository = userRepository,
            getCommunityLeadersFn = { sharedPrefManager.getCommunityLeaders() },
            setRepliedNewsIdFn = { sharedPrefManager.setRepliedNewsId(it) }
        )
        adapterNews?.setFromLogin(requireArguments().getBoolean("fromLogin"))
        adapterNews?.setListener(this)
        adapterNews?.registerAdapterDataObserver(observer)
        adapterNews?.submitList(sortedList)
        binding.rvNews.adapter = adapterNews
    }

    override fun onNewsItemClick(news: RealmNews?) {
        val fromLogin = arguments?.getBoolean("fromLogin")
        if (fromLogin == false) {
            val bundle = Bundle()
            bundle.putString("newsId", news?.newsId)
            bundle.putString("newsRev", news?.newsRev)
            bundle.putString("conversations", news?.conversations)

            val chatDetailFragment = ChatDetailFragment()
            chatDetailFragment.arguments = bundle

            FragmentNavigator.replaceFragment(
                parentFragmentManager,
                R.id.fragment_container,
                chatDetailFragment,
                addToBackStack = true
            )
        }
    }

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig.orientation
        changeLayoutManager(orientation, binding.rvNews)
    }

    private fun scrollToTop() {
        binding.rvNews.post {
            binding.rvNews.scrollToPosition(0)
        }
    }

    private val observer: AdapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            adapterNews?.let { showNoData(binding.tvMessage, it.itemCount, currentEmptyStateSource) }
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            adapterNews?.let { showNoData(binding.tvMessage, it.itemCount, currentEmptyStateSource) }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            adapterNews?.let { showNoData(binding.tvMessage, it.itemCount, currentEmptyStateSource) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchTextListener() {
        etSearch.textChanges()
            .debounce(300)
            .onEach { text ->
                val searchQuery = text.toString().trim()
                voicesViewModel.updateSearchQuery(searchQuery)
                scrollToTop()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }
    
    private fun setupLabelFilter() {
        val binding = _binding ?: return
        if (labelAdapter == null) {
            labelAdapter = VoicesLabelAdapter(
                onItemClick = { label ->
                    voicesViewModel.updateSelectedLabel(label)
                    scrollToTop()
                }
            )
            binding.filterByLabel.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
            binding.filterByLabel.adapter = labelAdapter
        }
    }

    private fun updateLabelSpinner(labels: List<String>, selectedLabel: String) {
        labelAdapter?.submitList(labels.map { VoicesLabelItem(it, it == selectedLabel) })

        val position = labels.indexOf(selectedLabel)
        if (position >= 0) {
            _binding?.filterByLabel?.scrollToPosition(position)
        }
    }

    override fun onDestroyView() {
        adapterNews?.unregisterAdapterDataObserver(observer)
        labelAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
