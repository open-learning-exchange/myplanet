package org.ole.planet.myplanet.base

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider

@AndroidEntryPoint
abstract class BaseTeamFragment : BaseVoicesFragment() {
    var user: UserEntity? = null
    var teamId: String = ""
        set(value) {
            if (field != value) {
                field = value
                _isMemberFlow.value = false
            }
        }
    var team: MyTeam? = null
    @Inject
    lateinit var teamsRepository: TeamsRepository
    @Inject
    lateinit var teamsSyncRepository: TeamsSyncRepository
    @Inject
    open lateinit var dispatcherProvider: DispatcherProvider
    private val _teamFlow = MutableStateFlow<MyTeam?>(null)
    val teamFlow: StateFlow<MyTeam?> = _teamFlow.asStateFlow()
    private val _isMemberFlow = MutableStateFlow(false)
    val isMemberFlow: StateFlow<Boolean> = _isMemberFlow.asStateFlow()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        teamId = requireArguments().getString("id", "")

        lifecycleScope.launch {
            user = userRepository.getUserModel()
            loadTeamDetails()
        }
    }

    override fun setData(list: List<News?>?) {}

    private fun loadTeamDetails() {
        val shouldQueryTeam = shouldQueryTeamFromRealm()
        val existingTeam = team
        lifecycleScope.launch(dispatcherProvider.io) {
            val teamResult = if (shouldQueryTeam) {
                try {
                    teamsRepository.getTeamById(teamId)
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

            val membership = teamsRepository.isMember(user?.id, teamId)

            withContext(dispatcherProvider.main) {
                teamResult?.let {
                    team = it
                }
                _teamFlow.value = teamResult ?: team
                _isMemberFlow.value = membership
            }
        }
    }

    protected open fun shouldQueryTeamFromRealm(): Boolean {
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
        super.onDestroy()
    }
    
}
