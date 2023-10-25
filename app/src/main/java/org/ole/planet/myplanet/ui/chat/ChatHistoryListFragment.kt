package org.ole.planet.myplanet.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.ole.planet.myplanet.databinding.FragmentChatHistoryListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.community.AdapterLeader

class ChatHistoryListFragment : Fragment() {
    private lateinit var fragmentChatHistoryListBinding: FragmentChatHistoryListBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentChatHistoryListBinding = FragmentChatHistoryListBinding.inflate(inflater, container, false)
        return fragmentChatHistoryListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val slidingPaneLayout = fragmentChatHistoryListBinding.slidingPaneLayout
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, ChatHistoryListOnBackPressedCallback(slidingPaneLayout))


//        val chatAdapter = ChatAdapter{
//        val adapterJoinedMember = AdapterLeader {
            fragmentChatHistoryListBinding.slidingPaneLayout.openPane()
//        }
        var mRealm = DatabaseService(requireActivity()).realmInstance;
        val leaders = mRealm.where(RealmMyTeam::class.java).equalTo("isLeader", true).findAll()
        val list = ArrayList<RealmUserModel>()
        for (team in leaders) {
            val model =
                    mRealm.where(RealmUserModel::class.java).equalTo("id", team.user_id).findFirst()
            if (model != null && !list.contains(model)) list.add(model)
        }
        fragmentChatHistoryListBinding.recyclerView.adapter = AdapterLeader(requireActivity(), list)
    }
}

class ChatHistoryListOnBackPressedCallback(private val slidingPaneLayout: SlidingPaneLayout)
    : OnBackPressedCallback(
        slidingPaneLayout.isSlideable && slidingPaneLayout.isOpen
    ), SlidingPaneLayout.PanelSlideListener {
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