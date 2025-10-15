package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var teamId: String = ""
        set(value) {
            if (field != value) {
                field = value
                _isMemberFlow.value = false
            }
        }
    var team: RealmMyTeam? = null
    @Inject
    lateinit var teamRepository: TeamRepository
    private val _teamFlow = MutableStateFlow<RealmMyTeam?>(null)
    val teamFlow: StateFlow<RealmMyTeam?> = _teamFlow.asStateFlow()
    private val _isMemberFlow = MutableStateFlow(false)
    val isMemberFlow: StateFlow<Boolean> = _isMemberFlow.asStateFlow()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sParentCode = settings.getString("parentCode", "")
        val communityName = settings.getString("communityName", "")
        mRealm = databaseService.realmInstance
        user = profileDbHandler?.userModel?.let { mRealm.copyFromRealm(it) }
        teamId = requireArguments().getString("id", "") ?: "$communityName@$sParentCode"

        loadTeamData()
    }

    override fun setData(list: List<RealmNews?>?) {}

    private fun loadTeamData() {
        val shouldQueryTeam = shouldQueryTeamFromRealm()
        val existingTeam = team
        lifecycleScope.launch(Dispatchers.IO) {
            val teamResult = if (shouldQueryTeam) {
                try {
                    teamRepository.getTeamByDocumentIdOrTeamId(teamId)
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    null
                }
            } else {
                existingTeam
            }

            if (shouldQueryTeam && teamResult == null) {
                return@launch
            }

            val membership = teamRepository.isMember(user?.id, teamId)

            withContext(Dispatchers.Main) {
                teamResult?.let {
                    team = it
                }
                _teamFlow.value = teamResult ?: team
                _isMemberFlow.value = membership
            }
        }
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
        _isMemberFlow.value = false
        if (isRealmInitialized() && mRealm.isOpen) {
            mRealm.close()
        }
        super.onDestroy()
    }
    
}
