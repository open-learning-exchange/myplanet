package org.ole.planet.myplanet.ui.team

import android.content.*
import android.os.Bundle
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

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
        dbService = DatabaseService()

        user = profileDbHandler.userModel?.let { mRealm.copyFromRealm(it) }

        team = try {
            mRealm.query<RealmMyTeam>(RealmMyTeam::class, "_id == $0", teamId).first().find() ?: throw IllegalArgumentException("Team not found for ID: $teamId")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            try {
                mRealm.query<RealmMyTeam>(RealmMyTeam::class, "teamId == $0", teamId).first().find() ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                return
            }
        }
    }

    override fun setData(list: List<RealmNews>?) {}

    fun isMember(): Boolean {
        return mRealm.query<RealmMyTeam>(RealmMyTeam::class, "userId == $0 AND teamId == $1 AND docType == $2", user?.id ?: "", teamId, "membership").count().find() > 0
    }

    companion object {
        var settings: SharedPreferences? = null
    }
}
