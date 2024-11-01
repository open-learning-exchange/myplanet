package org.ole.planet.myplanet.ui.team

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.tabs.TabLayoutMediator
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.isTeamLeader
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

class TeamDetailFragment : BaseTeamFragment() {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        val user = UserProfileDbHandler(requireContext()).userModel
        mRealm = DatabaseService(requireActivity()).realmInstance
        team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst() ?: throw IllegalArgumentException("Team not found for ID: $teamId")
        fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(requireActivity(), team, isMyTeam)
        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
        }.attach()

        val pageIndex = arguments?.getInt("navigateToPage", -1) ?: -1
        if (pageIndex >= 0 && pageIndex < (fragmentTeamDetailBinding.viewPager2.adapter?.itemCount ?: 0)) {
            fragmentTeamDetailBinding.viewPager2.currentItem = pageIndex
        }

        fragmentTeamDetailBinding.title.text = team?.name
        fragmentTeamDetailBinding.subtitle.text = team?.type

        if (!isMyTeam) {
            fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
        } else {
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
        val user = UserProfileDbHandler(requireContext()).userModel
        if (!mRealm.isInTransaction) {
            mRealm.beginTransaction()
        }
        val log = mRealm.createObject(RealmTeamLog::class.java, UUID.randomUUID().toString())
        log.teamId = teamId
        if (user != null) {
            log.user = user.name
            log.createdOn = user.planetCode
            log.type = "teamVisit"
            log.teamType = team?.teamType
            log.parentCode = user.parentCode
            log.time = Date().time
        }
        mRealm.commitTransaction()
    }
}
