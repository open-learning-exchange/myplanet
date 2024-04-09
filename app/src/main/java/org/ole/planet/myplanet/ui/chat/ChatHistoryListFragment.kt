package org.ole.planet.myplanet.ui.chat

import android.os.Bundle
import android.text.*
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import io.realm.RealmList
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentChatHistoryListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*

class ChatHistoryListFragment : Fragment() {
    private lateinit var fragmentChatHistoryListBinding: FragmentChatHistoryListBinding
    private lateinit var sharedViewModel: ChatViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentChatHistoryListBinding = FragmentChatHistoryListBinding.inflate(inflater, container, false)
        return fragmentChatHistoryListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val slidingPaneLayout = fragmentChatHistoryListBinding.slidingPaneLayout
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, ChatHistoryListOnBackPressedCallback(slidingPaneLayout))

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

        val mRealm = DatabaseService(requireActivity()).realmInstance
        val chats = mRealm.where(RealmChatHistory::class.java).findAll()

        val list = ArrayList<RealmChatHistory>()
        for (chat in chats) {
            val model = mRealm.where(RealmChatHistory::class.java).equalTo("id", chat.id).findFirst()
            if (model != null && !list.contains(model)) list.add(model)
        }
        val adapter = ChatHistoryListAdapter(requireContext(), list)
        adapter.setChatHistoryItemClickListener(object : ChatHistoryListAdapter.ChatHistoryItemClickListener {
            override fun onChatHistoryItemClicked(conversations: RealmList<Conversation>?, _id: String, _rev:String?) {
                conversations?.let { sharedViewModel.setSelectedChatHistory(it) }
                sharedViewModel.setSelected_id(_id)
                _rev?.let { sharedViewModel.setSelected_rev(it) }

                fragmentChatHistoryListBinding.slidingPaneLayout.openPane()
            }
        })
        fragmentChatHistoryListBinding.recyclerView.adapter = adapter

        fragmentChatHistoryListBinding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
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