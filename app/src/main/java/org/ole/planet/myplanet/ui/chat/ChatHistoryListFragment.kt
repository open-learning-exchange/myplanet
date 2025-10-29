package org.ole.planet.myplanet.ui.chat

import android.content.SharedPreferences
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.BaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.databinding.FragmentChatHistoryListBinding
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@AndroidEntryPoint
class ChatHistoryListFragment : Fragment() {
    private var _binding: FragmentChatHistoryListBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedViewModel: ChatViewModel
    var user: RealmUserModel? = null
    private var isFullSearch: Boolean = false
    private var isQuestion: Boolean = false
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    private val serverUrlMapper = ServerUrlMapper()
    private var sharedNewsMessages: List<RealmNews> = emptyList()
    private var shareTargets = ChatShareTargets(null, emptyList(), emptyList())
    
    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var chatRepository: ChatRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var teamRepository: TeamRepository
    @Inject
    lateinit var newsRepository: NewsRepository
    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    private lateinit var realtimeSyncListener: BaseRealtimeSyncListener
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        prefManager = SharedPrefManager(requireContext())
        startChatHistorySync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatHistoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val slidingPaneLayout = binding.slidingPaneLayout
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, ChatHistoryListOnBackPressedCallback(slidingPaneLayout))

        setupRealtimeSync()
        binding.toggleGroup.visibility = View.GONE
        binding.newChat.setOnClickListener {
            sharedViewModel.clearChatState()
            if (resources.getBoolean(R.bool.isLargeScreen)) {
                val chatHistoryListFragment = ChatHistoryListFragment()
                NavigationHelper.replaceFragment(
                    parentFragmentManager,
                    R.id.fragment_container,
                    chatHistoryListFragment,
                    addToBackStack = true,
                    tag = "ChatHistoryList"
                )
            } else {
                val chatDetailFragment = ChatDetailFragment()
                NavigationHelper.replaceFragment(
                    parentFragmentManager,
                    R.id.fragment_container,
                    chatDetailFragment,
                    addToBackStack = true,
                    tag = "ChatDetail"
                )
            }
        }

        refreshChatHistoryList()

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (binding.recyclerView.adapter as? ChatHistoryListAdapter)?.search(s.toString(), isFullSearch, isQuestion)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

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
        refreshChatHistoryList()
    }

    private fun startChatHistorySync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isChatHistorySynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
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
                            prefManager.setChatHistorySynced(true)

                            refreshChatHistoryList()
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
                            refreshChatHistoryList()

                            Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                                .setAction("Retry") { startChatHistorySync() }.show()
                        }
                    }
                }
            } }, "full", listOf("chat_history"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    fun refreshChatHistoryList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentUser = loadCurrentUser()
            sharedNewsMessages = chatRepository.getPlanetNewsMessages(currentUser?.planetCode)
            val ownerId = currentUser?.id ?: currentUser?._id ?: settings.getString("userId", "")
            val list = chatRepository.getChatHistoryForUser(ownerId, currentUser?.name)
            shareTargets = loadShareTargets()

            val adapter = binding.recyclerView.adapter as? ChatHistoryListAdapter
            if (adapter == null) {
                val newAdapter = ChatHistoryListAdapter(
                    requireContext(),
                    list,
                    currentUser,
                    sharedNewsMessages,
                    shareTargets,
                    ::shareChat,
                )
                newAdapter.setChatHistoryItemClickListener(object : ChatHistoryListAdapter.ChatHistoryItemClickListener {
                    override fun onChatHistoryItemClicked(conversations: List<Conversation>?, id: String, rev: String?, aiProvider: String?) {
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
                adapter.updateChatHistory(list)
                binding.searchBar.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
            }

            showNoData(binding.noChats, list.size, "chatHistory")
            if (list.isEmpty()) {
                binding.searchBar.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }

    private suspend fun loadCurrentUser(): RealmUserModel? {
        val cachedUser = user
        if (cachedUser != null) {
            return cachedUser
        }
        val userId = settings.getString("userId", "")
        if (userId.isNullOrEmpty()) {
            return null
        }
        val fetchedUser = userRepository.getUserById(userId)
        user = fetchedUser
        return fetchedUser
    }

    private suspend fun loadShareTargets(): ChatShareTargets {
        val teams = teamRepository.getShareableTeams()
        val enterprises = teamRepository.getShareableEnterprises()
        val parentCode = settings.getString("parentCode", "")
        val communityName = settings.getString("communityName", "")
        val communityId = if (!communityName.isNullOrBlank() && !parentCode.isNullOrBlank()) {
            "$communityName@$parentCode"
        } else {
            null
        }
        val community = communityId?.let { teamRepository.getTeamById(it) }
        return ChatShareTargets(community, teams, enterprises)
    }

    private fun shareChat(map: HashMap<String?, String>, chatHistory: RealmChatHistory) {
        if (!isAdded || _binding == null) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val currentUser = loadCurrentUser()
            val createdNews = newsRepository.createNews(map, currentUser)
            if (currentUser?.planetCode != null) {
                sharedNewsMessages = sharedNewsMessages + createdNews
            }
            (binding.recyclerView.adapter as? ChatHistoryListAdapter)?.let { adapter ->
                adapter.updateCachedData(currentUser, sharedNewsMessages)
                adapter.notifyChatShared(chatHistory._id)
            }
        }
    }

    private fun setupRealtimeSync() {
        realtimeSyncListener = object : BaseRealtimeSyncListener() {
            override fun onTableDataUpdated(update: TableDataUpdate) {
                if (update.table == "chats" && update.shouldRefreshUI) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        refreshChatHistoryList()
                    }
                }
            }
        }
        syncCoordinator.addListener(realtimeSyncListener)
    }

    override fun onDestroyView() {
        if (::realtimeSyncListener.isInitialized) {
            syncCoordinator.removeListener(realtimeSyncListener)
        }
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }
}

class ChatHistoryListOnBackPressedCallback(private val slidingPaneLayout: SlidingPaneLayout) :
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
