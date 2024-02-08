package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.Utilities

abstract class BaseTeamFragment : BaseNewsFragment() {
    lateinit var dbService: DatabaseService
    lateinit var user: RealmUserModel
    lateinit var teamId: String
    lateinit var team: RealmMyTeam
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (requireParentFragment().arguments != null) {
            teamId = requireParentFragment().requireArguments().getString("id", "")
        } else if (arguments != null) {
            teamId = requireArguments().getString("id", "")
        }
        dbService = DatabaseService(requireActivity())
        mRealm = dbService.realmInstance
        if (UserProfileDbHandler(activity).userModel != null) {
            user = mRealm.copyFromRealm(UserProfileDbHandler(activity).userModel)
        }
        Utilities.log("Team id $teamId")
        team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()!!
        settings = requireActivity().getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun setData(list: List<RealmNews?>?) {}

    companion object {
        var settings: SharedPreferences? = null
    }
}
