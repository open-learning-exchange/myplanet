package org.ole.planet.myplanet.ui.team

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnMemberSelected
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.isTeamLeader
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

class TeamDetailFragment : BaseTeamFragment(), OnMemberSelected {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    private lateinit var selectedItems: MutableList<RealmUserModel?>
    private lateinit var adapterAddMember: AdapterAddMember

   fun getAdapter(memberList: List<RealmUserModel>): RecyclerView.Adapter<*> {
        adapterAddMember = AdapterAddMember(memberList)
        adapterAddMember.setListener(this)
        return adapterAddMember
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        selectedItems = mutableListOf()
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

        val pageIndex = arguments?.getInt("navigateToPage", -1) ?: -1
        if (pageIndex >= 0 && pageIndex < (fragmentTeamDetailBinding.viewPager2.adapter?.itemCount ?: 0)) {
            fragmentTeamDetailBinding.viewPager2.currentItem = pageIndex
        }

        fragmentTeamDetailBinding.title.text = team?.name
        fragmentTeamDetailBinding.subtitle.text = team?.type

        if (!isMyTeam) {
            fragmentTeamDetailBinding.btnAddMember?.isEnabled = false
            fragmentTeamDetailBinding.btnAddMember?.visibility = View.GONE
            fragmentTeamDetailBinding.btnAddDoc.isEnabled = false
            fragmentTeamDetailBinding.btnAddDoc.visibility = View.GONE
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
            if (isTeamLeader(teamId, user?.id, mRealm)) {
                fragmentTeamDetailBinding.btnAddMember?.isEnabled = true
                fragmentTeamDetailBinding.btnAddMember?.visibility = View.VISIBLE
            }
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
        val allMembers = mRealm.where(RealmUserModel::class.java).findAll()
        println(allMembers)
        fragmentTeamDetailBinding.btnAddMember?.setOnClickListener {
            showAddMemberDialog(allMembers)
        }
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

    private fun addMember(userModel: RealmUserModel?){
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val newMember = mRealm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
        newMember.teamType = "sync"
        newMember.userId = userModel?.id
        newMember.teamId = teamId
        newMember.docType = "membership"
        newMember.updated = true
        newMember.teamPlanetCode = userModel?.planetCode
        mRealm.commitTransaction()
    }

    private fun showAddMemberDialog(memberList: List<RealmUserModel>) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.add_member_dialog, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvMembers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = getAdapter(memberList)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btnadd).setOnClickListener {
            selectedItems.forEach { member ->
                addMember(member)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onSelectedListChange(list: MutableList<RealmUserModel?>) {
        selectedItems = list
    }
}
