package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class MembersFragment : BaseMemberFragment() {

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    private lateinit var currentUser: RealmUserModel
    private var internalMemberChangeListener: MemberChangeListener = object : MemberChangeListener {
        override fun onMemberChanged() {
            loadData()
        }
    }
    private var externalMemberChangeListener: MemberChangeListener? = null
    fun setMemberChangeListener(listener: MemberChangeListener) {
        this.externalMemberChangeListener = listener
    }
    private val memberRequestAdapter by lazy {
        AdapterMemberRequest { user, isAccepted ->
            respondToRequest(user, isAccepted)
        }
    }

    override val adapter: RecyclerView.Adapter<*>
        get() = memberRequestAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentUser = userProfileDbHandler.userModel ?: RealmUserModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result: Pair<List<RealmUserModel>, TeamUiInfo> = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    val team = realm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                    val requestIds = team?.requests?.let {
                        Gson().fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type)
                    }?.toTypedArray() ?: emptyArray()

                    val requestedMembers = if (requestIds.isEmpty()) {
                        realm.copyFromRealm(emptyList<RealmUserModel>())
                    } else {
                        realm.copyFromRealm(
                            realm.where(RealmUserModel::class.java)
                                .`in`("_id", requestIds)
                                .findAll()
                        )
                    }

                    val isLeader = team?.isLeader ?: false
                    val memberCount = team?.let {
                        realm.where(RealmMyTeam::class.java)
                            .equalTo("teamId", it._id)
                            .equalTo("docType", "membership")
                            .count()
                    } ?: 0

                    val uiInfo = TeamUiInfo(isLeader, memberCount.toInt(), currentUser.id)
                    Pair(requestedMembers, uiInfo)
                }
            }
            memberRequestAdapter.setTeamUiInfo(result.second)
            memberRequestAdapter.submitList(result.first)
        }
    }

    private fun respondToRequest(user: RealmUserModel, isAccepted: Boolean) {
        val currentTeamId = teamId
        val userId = user.id
        if (currentTeamId.isNullOrBlank() || userId.isNullOrBlank()) {
            Utilities.toast(requireContext(), getString(R.string.request_failed_please_retry))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = teamRepository.respondToMemberRequest(currentTeamId, userId, isAccepted)
            if (result.isSuccess) {
                teamRepository.syncTeamActivities()
                loadData()
                externalMemberChangeListener?.onMemberChanged()
            } else {
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
        get() = emptyList()

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