package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

@AndroidEntryPoint
abstract class BaseTeamFragment : BaseNewsFragment() {
    var user: RealmUserModel? = null
    lateinit var teamId: String
    var team: RealmMyTeam? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sParentCode = settings.getString("parentCode", "")
        val communityName = settings.getString("communityName", "")
        teamId = requireArguments().getString("id", "") ?: "$communityName@$sParentCode"
        mRealm = userRepository.getRealm()
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

}
