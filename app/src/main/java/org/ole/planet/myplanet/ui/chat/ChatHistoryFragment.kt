package org.ole.planet.myplanet.ui.chat

import android.content.res.ColorStateList
import android.content.res.Resources
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.OnBaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.OnChatHistoryItemClickListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.databinding.FragmentChatHistoryBinding
import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.utils.DialogUtils

private data class Quartet<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


@AndroidEntryPoint
class ChatHistoryFragment : Fragment() {
    private var _binding: FragmentChatHistoryBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: ChatViewModel by activityViewModels()
    var user: RealmUser? = null
    private var isFullSearch: Boolean = false
    private var isQuestion: Boolean = false
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var serverUrlMapper: ServerUrlMapper
    private var sharedNewsMessages: List<RealmNews> = emptyList()
    private var shareTargets = ChatShareTargets(null, emptyList(), emptyList())
    private var memoizedShareTargets: ChatShareTargets? = null
    private var searchBarWatcher: TextWatcher? = null
    
    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var chatRepository: ChatRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var teamsRepository: TeamsRepository
    @Inject
    lateinit var voicesRepository: VoicesRepository
    private val syncManagerInstance = RealtimeSyncManager.getInstance()
    private lateinit var onRealtimeSyncListener: OnBaseRealtimeSyncListener
    private val serverUrl: String
        get() = sharedPrefManager.getServerUrl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startChatHistorySync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val slidingPaneLayout = binding.slidingPaneLayout
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, ChatHistoryOnBackPressedCallback(slidingPaneLayout))

        setupRealtimeSync()
        checkAiProvidersIfNeeded()
        binding.toggleGroup.visibility = View.GONE
        binding.newChat.setOnClickListener {
            sharedViewModel.clearChatState()
            if (resources.getBoolean(R.bool.isLargeScreen)) {
                val chatHistoryFragment = ChatHistoryFragment()
                FragmentNavigator.replaceFragment(
                    parentFragmentManager,
                    R.id.fragment_container,
                    chatHistoryFragment,
                    addToBackStack = true,
                    tag = "ChatHistory"
                )
            } else {
                val chatDetailFragment = ChatDetailFragment()
                FragmentNavigator.replaceFragment(
                    parentFragmentManager,
                    R.id.fragment_container,
                    chatDetailFragment,
                    addToBackStack = true,
                    tag = "ChatDetail"
                )
            }
        }

        refreshChatHistory()

        searchBarWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (binding.recyclerView.adapter as? ChatHistoryAdapter)?.search(s.toString(), isFullSearch, isQuestion)
            }

            override fun afterTextChanged(s: Editable?) {}
        }
        binding.searchBar.addTextChangedListener(searchBarWatcher)

        binding.fullSearch.setOnCheckedChangeListener { _, isChecked ->
            val density = Resources.getSystem().displayMetrics.density
            val params = binding.fullSearch.layoutParams as ViewGroup.MarginLayoutParams
            if (isChecked) {
                isFullSearch = true
                binding.toggleGroup.visibility = View.VISIBLE
                params.topMargin = (0 * density).toInt()
            } else {
                isFullSearch = false
                binding.toggleGroup.visibility = View.GONE
                params.topMargin = (20 * density).toInt()
            }
            binding.fullSearch.layoutParams = params
        }

        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if(isChecked){
                when (checkedId) {
                    R.id.btnQuestions -> {
                        isQuestion = true
                        binding.btnQuestions.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        binding.btnResponses.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.hint_color))

                        binding.btnQuestions.setTextColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        binding.btnResponses.setTextColor(ContextCompat.getColor(requireContext(), R.color.hint_color))
                    }
                    R.id.btnResponses -> {
                        isQuestion = false
                        binding.btnResponses.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        binding.btnQuestions.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.hint_color))

                        binding.btnResponses.setTextColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        binding.btnQuestions.setTextColor(ContextCompat.getColor(requireContext(), R.color.hint_color))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshChatHistory()
    }

    private fun startChatHistorySync() {
        val isFastSync = sharedPrefManager.getFastSync()
        if (isFastSync && !sharedPrefManager.isChatHistorySynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch {
            updateServerIfNecessary(mapping)
            startSyncManager()
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : OnSyncListener {
            override fun onSyncStarted() {
                if (view != null && isAdded) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (isAdded && !requireActivity().isFinishing) {
                            customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                            customProgressDialog?.setText(getString(R.string.syncing_chat_history))
                            customProgressDialog?.show()
                        }
                    }
                }
            }

            override fun onSyncComplete() {
                if (view != null && isAdded) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (isAdded) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                            sharedPrefManager.setChatHistorySynced(true)

                            refreshChatHistory()
                        }
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                if (view != null && isAdded) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (isAdded) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                            refreshChatHistory()

                            Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                                .setAction("Retry") { startChatHistorySync() }.show()
                        }
                    }
                }
            } }, "full", listOf("chat_history"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }

    fun refreshChatHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cachedUser = user
            val cachedTargets = memoizedShareTargets

            val currentUser = cachedUser ?: loadCurrentUser(sharedPrefManager.getUserId())
            val newsMessages = voicesRepository.getPlanetNewsMessages(currentUser?.planetCode)
            val chatHistory = chatRepository.getChatHistoryForUser(currentUser?.name)
            val targets = cachedTargets ?: loadShareTargets(
                sharedPrefManager.getParentCode(),
                sharedPrefManager.getCommunityName(),
                currentUser?._id
            )

            user = currentUser
            sharedNewsMessages = newsMessages
            shareTargets = targets
            memoizedShareTargets = targets

            val adapter = binding.recyclerView.adapter as? ChatHistoryAdapter
            if (adapter == null) {
                val newAdapter = ChatHistoryAdapter(
                    requireContext(),
                    chatHistory,
                    currentUser,
                    sharedNewsMessages,
                    shareTargets
                ) { map, chat ->
                    if (!isAdded || _binding == null) {
                        return@ChatHistoryAdapter
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        val currentUser = user
                        val chatId = chat._id ?: ""
                        val viewInId = map["viewInId"] ?: ""
                        if (chatId.isNotEmpty() && viewInId.isNotEmpty() &&
                            voicesRepository.isAlreadyShared(chatId, viewInId)) {
                            Snackbar.make(binding.root, getString(R.string.chat_already_shared_to_destination), Snackbar.LENGTH_SHORT).show()
                            return@launch
                        }
                        val createdNews = voicesRepository.createNews(map, currentUser, null)
                        if (currentUser?.planetCode != null) {
                            sharedNewsMessages = sharedNewsMessages + createdNews
                        }
                        (binding.recyclerView.adapter as? ChatHistoryAdapter)?.let { adapter ->
                            adapter.updateCachedData(currentUser, sharedNewsMessages)
                            adapter.notifyChatShared(chat._id)
                        }
                    }
                }
                newAdapter.setChatHistoryItemClickListener(object : OnChatHistoryItemClickListener {
                    override fun onChatHistoryItemClicked(conversations: List<RealmConversation>?, id: String, rev: String?, aiProvider: String?) {
                        conversations?.let { sharedViewModel.setSelectedChatHistory(it) }
                        sharedViewModel.setSelectedId(id)
                        rev?.let { sharedViewModel.setSelectedRev(it) }
                        aiProvider?.let { sharedViewModel.setSelectedAiProvider(it) }
                        binding.slidingPaneLayout.openPane()
                    }
                })
                binding.recyclerView.adapter = newAdapter
            } else {
                adapter.updateCachedData(currentUser, sharedNewsMessages)
                adapter.updateShareTargets(shareTargets)
                adapter.updateChatHistory(chatHistory)
                binding.searchBar.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
            }

            showNoData(binding.noChats, chatHistory.size, "chatHistory")
            if (chatHistory.isEmpty()) {
                binding.searchBar.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }

    private suspend fun loadCurrentUser(userId: String?): RealmUser? {
        if (userId.isNullOrEmpty()) {
            return null
        }
        return userRepository.getUserById(userId)
    }

    private suspend fun loadShareTargets(parentCode: String?, communityName: String?, userId: String?): ChatShareTargets {
        val teams = teamsRepository.getTeamSummaries(userId)
        val enterprises = teamsRepository.getShareableEnterpriseSummaries(userId)
        val communityId = if (!communityName.isNullOrBlank() && !parentCode.isNullOrBlank()) {
            "$communityName@$parentCode"
        } else {
            null
        }
        val community = communityId?.let { id ->
            teamsRepository.getTeamSummaryById(id) ?: TeamSummary(
                _id = id,
                name = communityName ?: "",
                teamType = null,
                teamPlanetCode = null,
                createdDate = null,
                type = null,
                status = null,
                teamId = null,
                description = null,
                services = null,
                rules = null
            )
        }
        return ChatShareTargets(community, teams, enterprises)
    }

    private fun checkAiProvidersIfNeeded() {
        if (!sharedViewModel.shouldFetchAiProviders()) {
            return
        }

        sharedViewModel.setAiProvidersLoading(true)
        sharedViewModel.setAiProvidersError(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val providers = chatRepository.fetchAiProviders(serverUrl) { url -> org.ole.planet.myplanet.MainApplication.isServerReachable(url) }
            sharedViewModel.setAiProvidersLoading(false)
            if (providers == null || providers.values.all { !it }) {
                sharedViewModel.setAiProvidersError(true)
                sharedViewModel.setAiProviders(null)
            } else {
                sharedViewModel.setAiProviders(providers)
            }
        }
    }

    private fun setupRealtimeSync() {
        onRealtimeSyncListener = object : OnBaseRealtimeSyncListener() {
            override fun onTableDataUpdated(update: TableDataUpdate) {
                if (update.table == "chats" && update.shouldRefreshUI) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        refreshChatHistory()
                    }
                }
            }
        }
        syncManagerInstance.addListener(onRealtimeSyncListener)
    }

    override fun onDestroyView() {
        if (::onRealtimeSyncListener.isInitialized) {
            syncManagerInstance.removeListener(onRealtimeSyncListener)
        }
        searchBarWatcher?.let { binding.searchBar.removeTextChangedListener(it) }
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }
}

class ChatHistoryOnBackPressedCallback(private val slidingPaneLayout: SlidingPaneLayout) :
    OnBackPressedCallback(slidingPaneLayout.isSlideable && slidingPaneLayout.isOpen),
    SlidingPaneLayout.PanelSlideListener {
    init {
        slidingPaneLayout.addPanelSlideListener(this)
    }
    override fun handleOnBackPressed() {
        slidingPaneLayout.closePane()
    }

    override fun onPanelSlide(panel: View, slideOffset: Float) {}

    override fun onPanelOpened(panel: View) {
        isEnabled = true
    }

    override fun onPanelClosed(panel: View) {
        isEnabled = false
    }
}
