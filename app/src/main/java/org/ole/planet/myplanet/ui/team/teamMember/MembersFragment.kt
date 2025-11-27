package org.ole.planet.myplanet.ui.team.teamMember

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.team.TeamViewModel

@AndroidEntryPoint
class MembersFragment : BaseMemberFragment() {
    private val viewModel: TeamViewModel by viewModels()
    private lateinit var adapterMemberRequest: AdapterMemberRequest
    private var memberChangeListener: MemberChangeListener = object : MemberChangeListener {
        override fun onMemberChanged() {
            teamId?.let { viewModel.loadMemberRequests(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapterMemberRequest = AdapterMemberRequest { userId, isAccepted ->
            teamId?.let {
                viewModel.respondToMemberRequest(it, userId, isAccepted)
            }
        }
        recyclerView.adapter = adapterMemberRequest

        teamId?.let { viewModel.loadMemberRequests(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.memberRequests.collect { memberRequests ->
                adapterMemberRequest.submitList(memberRequests)
            }
        }
    }

    fun setMemberChangeListener(listener: MemberChangeListener) {
        this.memberChangeListener = listener
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override val list: List<RealmUserModel>
        get() = emptyList()

    override val adapter: RecyclerView.Adapter<*>
        get() = adapterMemberRequest

    override val layoutManager: RecyclerView.LayoutManager
        get() {
            val columns =
                when (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
                    Configuration.SCREENLAYOUT_SIZE_LARGE -> 3
                    Configuration.SCREENLAYOUT_SIZE_NORMAL -> 2
                    Configuration.SCREENLAYOUT_SIZE_SMALL -> 1
                    else -> 1
                }
            return GridLayoutManager(activity, columns)
        }
}
