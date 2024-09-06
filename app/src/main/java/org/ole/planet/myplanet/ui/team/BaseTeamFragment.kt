package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

@RequiresApi(Build.VERSION_CODES.O)
abstract class BaseTeamFragment : BaseNewsFragment() {
    lateinit var dbService: DatabaseService
    var user: RealmUserModel? = null
    lateinit var teamId: String
    var team: RealmMyTeam? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sParentCode = settings?.getString("parentCode", "")
        val communityName = settings?.getString("communityName", "")
        teamId = requireArguments().getString("id", "") ?: "$communityName@$sParentCode"
        dbService = DatabaseService(requireActivity())
        mRealm = dbService.realmInstance
        user = profileDbHandler.userModel?.let { mRealm.copyFromRealm(it) }
        team = try {
            mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                ?: throw IllegalArgumentException("Team not found for ID: $teamId")
        } catch (e: IllegalArgumentException) {
            try {
                mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst()
                    ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            } catch (e: IllegalArgumentException) {
                return
            }
        }
    }

    override fun setData(list: List<RealmNews?>?) {}

    fun isMember(): Boolean {
        return mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", user?.id)
            .equalTo("teamId", teamId)
            .equalTo("docType", "membership")
            .count() > 0
    }

    companion object {
        var settings: SharedPreferences? = null
    }
}
