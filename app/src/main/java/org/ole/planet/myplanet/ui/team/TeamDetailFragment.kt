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
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID
import androidx.viewpager2.widget.ViewPager2
import org.ole.planet.myplanet.ui.team.teamResource.ResourceUpdateListner

class TeamDetailFragment : BaseTeamFragment() {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    private var resourcePosition: Int = -1
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        val teamId = requireArguments().getString("id" ) ?: ""
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        val user = UserProfileDbHandler(requireContext()).userModel
        mRealm = DatabaseService(requireActivity()).realmInstance
        if (teamId.isNotEmpty()) {
            team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst() ?: throw IllegalArgumentException("Team not found for ID: $teamId")
        }

        fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(requireActivity(), team, isMyTeam)
        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
        }.attach()

        (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).let { adapter ->
            val titleResources = getString(R.string.resources)
            val titleDocuments = getString(R.string.documents)
            resourcePosition = (0 until adapter.itemCount)
                .firstOrNull { i ->
                    val title = adapter.getPageTitle(i)
                    title == titleResources || title == titleDocuments
                } ?: -1
        }

        val pageIndex = arguments?.getInt("navigateToPage", -1) ?: -1
        if (pageIndex >= 0 && pageIndex < (fragmentTeamDetailBinding.viewPager2.adapter?.itemCount ?: 0)) {
            fragmentTeamDetailBinding.viewPager2.currentItem = pageIndex
        }

        fragmentTeamDetailBinding.title.text = team?.name
        fragmentTeamDetailBinding.subtitle.text = team?.type

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
                        RealmMyTeam.requestToJoin(currentTeam._id!!, user, mRealm)
                        fragmentTeamDetailBinding.btnLeave.text = getString(R.string.requested)
                        fragmentTeamDetailBinding.btnLeave.isEnabled = false
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
            fragmentTeamDetailBinding.viewPager2.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(pos: Int) {
                        super.onPageSelected(pos)
                        if (pos == resourcePosition && MainApplication.showDownload) {
                            MainApplication.showDownload = false
                            val adapter = fragmentTeamDetailBinding
                                .viewPager2
                                .adapter as TeamPagerAdapter
                            val frag = adapter.getFragmentAt(pos) as? ResourceUpdateListner
                            frag?.onAddDocument()
                        }
                    }
                }
            )
            val vp = fragmentTeamDetailBinding.viewPager2
            val adapter = vp.adapter as TeamPagerAdapter
            fragmentTeamDetailBinding.btnAddDoc.setOnClickListener {
                if (vp.currentItem == resourcePosition) {
                    (adapter.getFragmentAt(resourcePosition) as? ResourceUpdateListner)
                        ?.onAddDocument()
                } else {
                    MainApplication.showDownload = true
                    vp.setCurrentItem(resourcePosition, false)
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
        val teamType = team?.teamType

        CoroutineScope(Dispatchers.IO).launch {
            val realm = DatabaseService(requireActivity()).realmInstance

            realm.executeTransaction { r ->
                val log = r.createObject(RealmTeamLog::class.java, UUID.randomUUID().toString())
                log.teamId = teamId
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
}
