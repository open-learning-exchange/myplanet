package org.ole.planet.myplanet.ui.team

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.isTeamLeader
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

class TeamDetailFragment : Fragment() {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    private lateinit var mRealm: Realm
    var team: RealmMyTeam? = null
    var teamId: String? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        teamId = requireArguments().getString("id")
        val user = UserProfileDbHandler(activity).userModel
        mRealm = DatabaseService(requireActivity()).realmInstance
        team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", requireArguments().getString("id")).findFirst()
        fragmentTeamDetailBinding.viewPager.adapter = TeamPagerAdapter(childFragmentManager, team!!, isMyTeam)
        fragmentTeamDetailBinding.tabLayout.setupWithViewPager(fragmentTeamDetailBinding.viewPager)
        if (!isMyTeam) {
            fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
        } else {
            fragmentTeamDetailBinding.btnLeave.setOnClickListener {
                AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        team!!.leave(user, mRealm)
                        Utilities.toast(activity, getString(R.string.left_team))
                        fragmentTeamDetailBinding.viewPager.adapter = TeamPagerAdapter(childFragmentManager, team!!, false)
                        fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
                    }.setNegativeButton(R.string.no, null).show()
            }
            fragmentTeamDetailBinding.btnAddDoc.setOnClickListener {
                MainApplication.showDownload = true
                fragmentTeamDetailBinding.viewPager.currentItem = 6
                MainApplication.showDownload = false
                if (MainApplication.listener != null) {
                    MainApplication.listener.onAddDocument()
                }
            }
        }
        if (isTeamLeader(teamId, user.id!!, mRealm)) {
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        }
        return fragmentTeamDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createTeamLog()
    }

    private fun createTeamLog() {
        val user = UserProfileDbHandler(activity).userModel
        if (team == null) return
        if (!mRealm.isInTransaction) {
            mRealm.beginTransaction()
        }
        Utilities.log("Crete team log")
        val log = mRealm.createObject(RealmTeamLog::class.java, UUID.randomUUID().toString())
        log.teamId = teamId
        log.user = user.name
        log.createdOn = user.planetCode
        log.type = "teamVisit"
        log.teamType = team!!.teamType
        log.parentCode = user.parentCode
        log.time = Date().time
        mRealm.commitTransaction()
    }
}
