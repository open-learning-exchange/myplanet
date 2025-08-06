package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMemberCount
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getRequestedMember
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class MembersFragment : BaseMemberFragment() {

    private lateinit var currentUser: RealmUserModel
    private lateinit var memberChangeListener: MemberChangeListener

    private val viewModel: MemberRequestViewModel by viewModels()

    fun setMemberChangeListener(listener: MemberChangeListener) {
        this.memberChangeListener = listener
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentUser = UserProfileDbHandler(context).userModel!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.result.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                memberChangeListener.onMemberChanged()
            }
            result.onFailure {
                Utilities.toast(requireContext(), getString(R.string.request_failed_please_retry))
            }
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override val list: List<RealmUserModel>
        get() = getRequestedMember(teamId, mRealm)

    override val adapter: RecyclerView.Adapter<*>
        get() {
            val memberCount = getJoinedMemberCount(teamId, mRealm)
            return AdapterMemberRequest(
                requireActivity(),
                list.toMutableList(),
                teamId,
                currentUser,
                isTeamLeader(),
                memberCount,
                viewModel
            )
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

    private fun isTeamLeader(): Boolean {
        val currentUserId = getCurrentUserId()
        val team = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("userId", currentUserId)
            .findFirst()
        return team?.isLeader == true
    }

    private fun getCurrentUserId(): String? {
        return currentUser.id
    }
}
