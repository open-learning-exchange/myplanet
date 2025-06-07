package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
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
        dbService = DatabaseService(requireActivity())
        mRealm = dbService.realmInstance
        user = profileDbHandler.userModel?.let { mRealm.copyFromRealm(it) }

        if (shouldQueryTeamFromRealm()) {
            team = try {
                mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                    ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                try {
                    mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst()
                        ?: throw IllegalArgumentException("Team not found for ID: $teamId")
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    return
                }
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

    private fun shouldQueryTeamFromRealm(): Boolean {
        val hasDirectData = requireArguments().containsKey("teamName") &&
                requireArguments().containsKey("teamType") &&
                requireArguments().containsKey("teamId")
        return !hasDirectData
    }

    protected fun getEffectiveTeamName(): String {
        return requireArguments().getString("teamName") ?: team?.name ?: ""
    }

    protected fun getEffectiveTeamType(): String {
        return requireArguments().getString("teamType") ?: team?.type ?: ""
    }

    protected fun getEffectiveTeamId(): String {
        return requireArguments().getString("teamId") ?: teamId
    }

    companion object {
        var settings: SharedPreferences? = null
    }
}