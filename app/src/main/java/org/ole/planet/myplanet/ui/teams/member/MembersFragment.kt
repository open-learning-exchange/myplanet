package org.ole.planet.myplanet.ui.teams.member

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileService

@AndroidEntryPoint
class MembersFragment : BaseMemberFragment() {

    @Inject
    lateinit var userProfileDbHandler: UserProfileService

    private val viewModel: MembersViewModel by viewModels()
    private lateinit var currentUser: RealmUserModel
    private var memberChangeListener: MemberChangeListener? = null
    fun setMemberChangeListener(listener: MemberChangeListener) {
        this.memberChangeListener = listener
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentUser = userProfileDbHandler.userModel ?: RealmUserModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        teamId?.let { viewModel.fetchMembers(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        (adapter as? RequestsAdapter)?.setData(
                            uiState.members,
                            uiState.isLeader,
                            uiState.memberCount
                        )
                        showNoData(binding.tvNodata, uiState.members.size, "members")
                    }
                }
                launch {
                    viewModel.successAction.collect {
                        memberChangeListener?.onMemberChanged()
                    }
                }
            }
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override val list: List<RealmUserModel>
        get() = emptyList()

    override val adapter: RecyclerView.Adapter<*> by lazy {
        RequestsAdapter(
            requireActivity(),
            currentUser,
        ) { user, isAccepted ->
            viewModel.respondToRequest(teamId, user, isAccepted)
        }.apply { setTeamId(teamId) }
    }

    override val layoutManager: RecyclerView.LayoutManager
        get() {
            val columns = when (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
                Configuration.SCREENLAYOUT_SIZE_LARGE -> 3
                Configuration.SCREENLAYOUT_SIZE_NORMAL -> 2
                Configuration.SCREENLAYOUT_SIZE_SMALL -> 1
                else -> 1
            }
            return GridLayoutManager(activity, columns)
        }

}
