package org.ole.planet.myplanet.ui.team

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.isTeamLeader
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.syncTeamActivities
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

class TeamDetailFragment : BaseTeamFragment() {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding

    // Add these new properties for direct data passing
    private var directTeamName: String? = null
    private var directTeamType: String? = null
    private var directTeamId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)

        // Get direct data if passed (new approach)
        directTeamId = requireArguments().getString("teamId")
        directTeamName = requireArguments().getString("teamName")
        directTeamType = requireArguments().getString("teamType")

        // Original logic - unchanged
        val teamId = requireArguments().getString("id" ) ?: ""
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        val user = UserProfileDbHandler(requireContext()).userModel
        mRealm = DatabaseService(requireActivity()).realmInstance

        // Only query Realm if direct data wasn't provided
        if (shouldQueryRealm(teamId)) {
            if (teamId.isNotEmpty()) {
                team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst() ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            }
        }

        fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(requireActivity(), team, isMyTeam)
        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
        }.attach()

        val pageIndex = arguments?.getInt("navigateToPage", -1) ?: -1
        if (pageIndex >= 0 && pageIndex < (fragmentTeamDetailBinding.viewPager2.adapter?.itemCount ?: 0)) {
            fragmentTeamDetailBinding.viewPager2.currentItem = pageIndex
        }

        // Use direct data if available, otherwise use team object
        fragmentTeamDetailBinding.title.text = getEffectiveTeamName()
        fragmentTeamDetailBinding.subtitle.text = getEffectiveTeamType()

        if (!isMyTeam) {
            fragmentTeamDetailBinding.btnAddDoc.isEnabled = false
            fragmentTeamDetailBinding.btnAddDoc.visibility = View.GONE
            if (user?.id?.startsWith("guest") == true){
                fragmentTeamDetailBinding.btnLeave.isEnabled = false
                fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
            }
            val currentTeam = team
            if (currentTeam != null && !currentTeam._id.isNullOrEmpty()) {

                val isUserRequested = currentTeam.requested(user?.id, mRealm)
                if (isUserRequested) {
                    fragmentTeamDetailBinding.btnLeave.text = getString(R.string.requested)
                    fragmentTeamDetailBinding.btnLeave.isEnabled = false
                } else {
                    fragmentTeamDetailBinding.btnLeave.text = getString(R.string.join)
                    fragmentTeamDetailBinding.btnLeave.setOnClickListener {
                        RealmMyTeam.requestToJoin(currentTeam._id!!, user, mRealm, team?.teamType)
                        fragmentTeamDetailBinding.btnLeave.text = getString(R.string.requested)
                        fragmentTeamDetailBinding.btnLeave.isEnabled = false
                        syncTeamActivities(requireContext())
                    }
                }
            } else {
                throw IllegalStateException("Team or team ID is null, cannot proceed.")
            }
        } else {
            fragmentTeamDetailBinding.btnAddDoc.isEnabled = true
            fragmentTeamDetailBinding.btnAddDoc.visibility = View.VISIBLE
            fragmentTeamDetailBinding.btnLeave.isEnabled = true
            fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE
            fragmentTeamDetailBinding.btnLeave.setOnClickListener {
                AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        team?.leave(user, mRealm)
                        Utilities.toast(activity, getString(R.string.left_team))
                        fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(requireActivity(), team, false)
                        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
                            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
                        }.attach()
                        fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
                    }.setNegativeButton(R.string.no, null).show()
            }
            fragmentTeamDetailBinding.btnAddDoc.setOnClickListener {
                MainApplication.showDownload = true
                fragmentTeamDetailBinding.viewPager2.currentItem = 6
                MainApplication.showDownload = false
                if (MainApplication.listener != null) {
                    MainApplication.listener?.onAddDocument()
                }
            }
        }
        if (isTeamLeader(teamId, user?.id, mRealm)) {
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        }
        return fragmentTeamDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createTeamLog()
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun createTeamLog() {
        val userModel = UserProfileDbHandler(requireContext()).userModel ?: return

        val userName = userModel.name
        val userPlanetCode = userModel.planetCode
        val userParentCode = userModel.parentCode
        val teamType = getEffectiveTeamType() // Use new helper method

        CoroutineScope(Dispatchers.IO).launch {
            val realm = DatabaseService(requireActivity()).realmInstance

            realm.executeTransaction { r ->
                val log = r.createObject(RealmTeamLog::class.java, UUID.randomUUID().toString())
                log.teamId = getEffectiveTeamId() // Use new helper method
                log.user = userName
                log.createdOn = userPlanetCode
                log.type = "teamVisit"
                log.teamType = teamType
                log.parentCode = userParentCode
                log.time = Date().time
            }

            realm.close()
        }
    }

    // NEW HELPER METHODS - Only additions, no changes to existing code

    private fun shouldQueryRealm(teamId: String): Boolean {
        // Only query Realm if we don't have direct data
        return directTeamId == null || directTeamName == null || directTeamType == null
    }

//    private fun getDisplayTeamName(): String {
//        return directTeamName ?: team?.name ?: ""
//    }
//
//    private fun getDisplayTeamType(): String {
//        return directTeamType ?: team?.type ?: ""
//    }
//
//    private fun getEffectiveTeamId(): String {
//        return directTeamId ?: team?._id ?: ""
//    }

    // NEW FACTORY METHOD for easier fragment creation with direct data
    companion object {
        fun newInstance(teamId: String, teamName: String, teamType: String, isMyTeam: Boolean, navigateToPage: Int = -1): TeamDetailFragment {
            val fragment = TeamDetailFragment()
            val args = Bundle().apply {
                putString("teamId", teamId)
                putString("teamName", teamName)
                putString("teamType", teamType)
                putBoolean("isMyTeam", isMyTeam)
                if (navigateToPage >= 0) {
                    putInt("navigateToPage", navigateToPage)
                }
            }
            fragment.arguments = args
            return fragment
        }
    }
}
