package org.ole.planet.myplanet.ui.chat

import android.content.Context.MODE_PRIVATE
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
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.FragmentChatHistoryListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.ChatViewModel
import org.ole.planet.myplanet.model.Conversation
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

class ChatHistoryListFragment : Fragment() {
    private lateinit var fragmentChatHistoryListBinding: FragmentChatHistoryListBinding
    private lateinit var sharedViewModel: ChatViewModel
    var user: RealmUserModel? = null
    private var isFullSearch: Boolean = false
    private var isQuestion: Boolean = false
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    lateinit var settings: SharedPreferences
    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        prefManager = SharedPrefManager(requireContext())
        settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        startChatHistorySync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentChatHistoryListBinding = FragmentChatHistoryListBinding.inflate(inflater, container, false)
        user = UserProfileDbHandler(requireContext()).userModel

        return fragmentChatHistoryListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val slidingPaneLayout = fragmentChatHistoryListBinding.slidingPaneLayout
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, ChatHistoryListOnBackPressedCallback(slidingPaneLayout))

        fragmentChatHistoryListBinding.toggleGroup.visibility = View.GONE
        fragmentChatHistoryListBinding.newChat.setOnClickListener {
            if (resources.getBoolean(R.bool.isLargeScreen)) {
                val chatHistoryListFragment = ChatHistoryListFragment()
                parentFragmentManager.beginTransaction().apply {
                    replace(R.id.fragment_container, chatHistoryListFragment)
                    addToBackStack("ChatHistoryList")
                    commit()
                }
            } else {
                val chatDetailFragment = ChatDetailFragment()
                parentFragmentManager.beginTransaction().apply {
                    replace(R.id.fragment_container, chatDetailFragment)
                    addToBackStack("ChatDetail")
                    commit()
                }
            }
        }

        refreshChatHistoryList()

        fragmentChatHistoryListBinding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (fragmentChatHistoryListBinding.recyclerView.adapter as? ChatHistoryListAdapter)?.search(s.toString(), isFullSearch, isQuestion)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        fragmentChatHistoryListBinding.fullSearch.setOnCheckedChangeListener { _, isChecked ->
            val density = Resources.getSystem().displayMetrics.density
            val params = fragmentChatHistoryListBinding.fullSearch.layoutParams as ViewGroup.MarginLayoutParams
            if (isChecked) {
                isFullSearch = true
                fragmentChatHistoryListBinding.toggleGroup.visibility = View.VISIBLE
                params.topMargin = (0 * density).toInt()
            } else {
                isFullSearch = false
                fragmentChatHistoryListBinding.toggleGroup.visibility = View.GONE
                params.topMargin = (20 * density).toInt()
            }
            fragmentChatHistoryListBinding.fullSearch.layoutParams = params
        }

        fragmentChatHistoryListBinding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if(isChecked){
                when (checkedId) {
                    R.id.btnQuestions -> {
                        isQuestion = true
                        fragmentChatHistoryListBinding.btnQuestions.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        fragmentChatHistoryListBinding.btnResponses.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.hint_color))

                        fragmentChatHistoryListBinding.btnQuestions.setTextColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        fragmentChatHistoryListBinding.btnResponses.setTextColor(ContextCompat.getColor(requireContext(), R.color.hint_color))
                    }
                    R.id.btnResponses -> {
                        isQuestion = false
                        fragmentChatHistoryListBinding.btnResponses.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        fragmentChatHistoryListBinding.btnQuestions.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.hint_color))

                        fragmentChatHistoryListBinding.btnResponses.setTextColor(ContextCompat.getColor(requireContext(), R.color.mainColor))
                        fragmentChatHistoryListBinding.btnQuestions.setTextColor(ContextCompat.getColor(requireContext(), R.color.hint_color))
                    }
                }
            }
        }
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
        SyncManager.instance?.start(object : SyncListener {
            override fun onSyncStarted() {
                activity?.runOnUiThread {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_chat_history))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        prefManager.setChatHistorySynced(true)

                        refreshChatHistoryList()
                    }
                }
            }

            override fun onSyncFailed(message: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshChatHistoryList()

                        Snackbar.make(fragmentChatHistoryListBinding.root, "Sync failed: ${message ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                            .setAction("Retry") { startChatHistorySync() }.show()
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
        val mRealm = DatabaseService(requireActivity()).realmInstance
        val list = mRealm.where(RealmChatHistory::class.java).equalTo("user", user?.name)
            .sort("id", Sort.DESCENDING)
            .findAll()

        val adapter = fragmentChatHistoryListBinding.recyclerView.adapter as? ChatHistoryListAdapter
        if (adapter == null) {
            val newAdapter = ChatHistoryListAdapter(requireContext(), list, this)
            newAdapter.setChatHistoryItemClickListener(object : ChatHistoryListAdapter.ChatHistoryItemClickListener {
                override fun onChatHistoryItemClicked(conversations: RealmList<Conversation>?, id: String, rev: String?, aiProvider: String?) {
                    conversations?.let { sharedViewModel.setSelectedChatHistory(it) }
                    sharedViewModel.setSelectedId(id)
                    rev?.let { sharedViewModel.setSelectedRev(it) }
                    aiProvider?.let { sharedViewModel.setSelectedAiProvider(it) }
                    fragmentChatHistoryListBinding.slidingPaneLayout.openPane()
                }
            })
            fragmentChatHistoryListBinding.recyclerView.adapter = newAdapter
        } else {
            adapter.updateChatHistory(list)
            fragmentChatHistoryListBinding.searchBar.visibility = View.VISIBLE
            fragmentChatHistoryListBinding.recyclerView.visibility = View.VISIBLE
        }

        showNoData(fragmentChatHistoryListBinding.noChats, list.size, "chatHistory")
        if (list.isEmpty()) {
            fragmentChatHistoryListBinding.searchBar.visibility = View.GONE
            fragmentChatHistoryListBinding.recyclerView.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        customProgressDialog?.dismiss()
        customProgressDialog = null
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