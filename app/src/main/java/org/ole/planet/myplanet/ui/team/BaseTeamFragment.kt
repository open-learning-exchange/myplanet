package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities

abstract class BaseTeamFragment : BaseNewsFragment() {
    lateinit var dbService: DatabaseService
    var user: RealmUserModel? = null
    lateinit var teamId: String
    lateinit var team: RealmMyTeam
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        teamId = parentFragment?.arguments?.getString("id", "") ?: arguments?.getString("id", "") ?: ""
        dbService = DatabaseService(requireActivity())
        mRealm = dbService.realmInstance
        user = profileDbHandler.userModel?.let { mRealm.copyFromRealm(it) }
        Utilities.log("Team id $teamId")

        team = try {
            mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst() ?: throw IllegalArgumentException("Team not found for ID: $teamId")
        } catch (e: IllegalArgumentException) {
            try {
                mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst() ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            } catch (e: IllegalArgumentException) {
                throw e
            }
        }

        settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun setData(list: List<RealmNews?>?) {}

    companion object {
        var settings: SharedPreferences? = null
    }
}
