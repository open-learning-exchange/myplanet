package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository

private val Realm.isOpen: Boolean
    get() = !isClosed

@AndroidEntryPoint
abstract class BaseTeamFragment : BaseNewsFragment() {
    var user: RealmUserModel? = null
    lateinit var teamId: String
    var team: RealmMyTeam? = null
    @Inject
    lateinit var teamRepository: TeamRepository


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sParentCode = settings.getString("parentCode", "")
        val communityName = settings.getString("communityName", "")
        teamId = requireArguments().getString("id", "") ?: "$communityName@$sParentCode"
        mRealm = databaseService.realmInstance
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

    fun isMember(): Boolean = runBlocking {
        teamRepository.isMember(user?.id, teamId)
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

    override fun onDestroy() {
        if (isRealmInitialized() && mRealm.isOpen) {
            mRealm.close()
        }
        super.onDestroy()
    }
    
}
