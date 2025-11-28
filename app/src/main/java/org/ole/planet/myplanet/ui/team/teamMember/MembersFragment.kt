package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import javax.inject.Inject

@AndroidEntryPoint
class MembersFragment : BaseMemberFragment() {

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    private lateinit var currentUser: RealmUserModel
    private var memberChangeListener: MemberChangeListener = object : MemberChangeListener {
        override fun onMemberChanged() {
            loadMemberRequests()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentUser = userProfileDbHandler.userModel ?: RealmUserModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadMemberRequests()
    }

    private fun loadMemberRequests() {
        viewLifecycleOwner.lifecycleScope.launch {
            val members = teamRepository.getMemberRequests(teamId, currentUser.id ?: "")
            (adapter as? AdapterMemberRequest)?.submitList(members)
            showNoData(binding.tvNodata, members.size, "members")
        }
    }

    override val adapter: RecyclerView.Adapter<*>
        get() = AdapterMemberRequest(
            requireActivity(),
            memberChangeListener,
            teamRepository,
            teamId,
        )

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

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}
