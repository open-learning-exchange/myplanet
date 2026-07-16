package org.ole.planet.myplanet.ui.chat

import android.content.res.ColorStateList
import android.content.res.Resources
import android.os.Bundle
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.OnChatHistoryItemClickListener
import org.ole.planet.myplanet.databinding.FragmentChatHistoryBinding
import org.ole.planet.myplanet.model.ChatShareTargets
import org.ole.planet.myplanet.model.RealmConversation
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.utils.collectLatestWhenStarted
import org.ole.planet.myplanet.utils.collectWhenStarted
import org.ole.planet.myplanet.utils.textChanges

private data class Quartet<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


@AndroidEntryPoint
class ChatHistoryFragment : Fragment() {
    private var _binding: FragmentChatHistoryBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: ChatViewModel by activityViewModels()
    var user: RealmUser? = null
    private var isFullSearch: Boolean = false
    private var isQuestion: Boolean = false
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private var sharedNewsMessages: List<RealmNews> = emptyList()
    private var shareTargets = ChatShareTargets(null, emptyList(), emptyList())
    
    @Inject
    lateinit var chatRepository: ChatRepository
    @Inject
    lateinit var voicesRepository: VoicesRepository
    private val serverUrl: String
        get() = sharedPrefManager.getServerUrl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        binding.searchBar.textChanges()
            .debounce(300)
            .onEach { text -> sharedViewModel.searchChats(text?.toString() ?: "", isFullSearch, isQuestion) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

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
            sharedViewModel.searchChats(binding.searchBar.text.toString(), isFullSearch, isQuestion)
        }
        observeScreenData()

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
                sharedViewModel.searchChats(binding.searchBar.text.toString(), isFullSearch, isQuestion)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshChatHistory()
    }

    fun refreshChatHistory() {
        sharedViewModel.loadChatHistoryScreenData(
            sharedPrefManager.getUserId(),
            sharedPrefManager.getParentCode(),
            sharedPrefManager.getCommunityName()
        )
    }

    private fun observeScreenData() {
        val newAdapter = ChatHistoryAdapter(
            requireContext(),
            emptyList(),
            user,
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

        collectLatestWhenStarted(sharedViewModel.screenData) { data ->
            if (data == null) return@collectLatestWhenStarted

            user = data.currentUser
            sharedNewsMessages = data.newsMessages
            shareTargets = data.shareTargets

            newAdapter.updateCachedData(user, sharedNewsMessages)
            newAdapter.updateShareTargets(shareTargets)

            if (data.chatHistory.isNotEmpty()) {
                binding.searchBar.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
            } else {
                binding.searchBar.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            }
        }

        collectLatestWhenStarted(sharedViewModel.filteredChats) { filteredHistory ->
            newAdapter.updateChatHistory(filteredHistory)
            showNoData(binding.noChats, filteredHistory.size, "chatHistory")
        }
    }

    private fun checkAiProvidersIfNeeded() {
        if (!sharedViewModel.shouldFetchAiProviders()) {
            return
        }

        sharedViewModel.setAiProvidersLoading(true)
        sharedViewModel.setAiProvidersError(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val providers = chatRepository.fetchAiProviders(serverUrl)
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
        collectWhenStarted(sharedViewModel.refreshChatSignal) {
            refreshChatHistory()
        }
    }

    override fun onDestroyView() {
        sharedNewsMessages = emptyList()
        shareTargets = ChatShareTargets(null, emptyList(), emptyList())
        user = null
        _binding = null
        super.onDestroyView()
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
