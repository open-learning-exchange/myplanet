package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamFragment : Fragment(), AdapterTeamList.OnClickTeamItem {
    private lateinit var fragmentTeamBinding: FragmentTeamBinding
    private lateinit var alertCreateTeamBinding: AlertCreateTeamBinding
    @Inject
    lateinit var teamRepository: TeamRepository
    var type: String? = null
    private var fromDashboard: Boolean = false
    var user: RealmUserModel? = null
    private var teamList: List<RealmMyTeam> = emptyList()
    private lateinit var adapterTeamList: AdapterTeamList
    private var conditionApplied: Boolean = false
    @Inject
    lateinit var uploadManager: UploadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            fromDashboard = it.getBoolean("fromDashboard")
            type = it.getString("type", "team")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamBinding = FragmentTeamBinding.inflate(inflater, container, false)
        user = UserProfileDbHandler(requireActivity()).userModel
        fragmentTeamBinding.addTeam.visibility = if (user?.isGuest() == true) View.GONE else View.VISIBLE
        fragmentTeamBinding.addTeam.setOnClickListener { createTeamAlert(null) }
        fragmentTeamBinding.tvFragmentInfo.text = if (type == "enterprise") getString(R.string.enterprises) else getString(R.string.team)
        loadTeams()
        return fragmentTeamBinding.root
    }

    private fun loadTeams() {
        lifecycleScope.launch {
            teamList = if (fromDashboard) {
                teamRepository.getMyTeamsByUserId(user?.id ?: "", type)
            } else {
                teamRepository.getTeams(user?.id ?: "", type)
            }
            conditionApplied = type == "enterprise"
            sortAndSetAdapter()
        }
    }

    fun createTeamAlert(team: RealmMyTeam?) {
        alertCreateTeamBinding = AlertCreateTeamBinding.inflate(LayoutInflater.from(context))
        if (type == "enterprise") {
            alertCreateTeamBinding.spnTeamType.visibility = View.GONE
            alertCreateTeamBinding.etDescription.hint = getString(R.string.entMission)
            alertCreateTeamBinding.etName.hint = getString(R.string.enter_enterprise_s_name)
        } else {
            alertCreateTeamBinding.etServices.visibility = View.GONE
            alertCreateTeamBinding.etRules.visibility = View.GONE
            alertCreateTeamBinding.etDescription.hint = getString(R.string.what_is_your_team_s_plan)
            alertCreateTeamBinding.etName.hint = getString(R.string.enter_team_s_name)
        }
        team?.let {
            alertCreateTeamBinding.etServices.setText(it.services)
            alertCreateTeamBinding.etRules.setText(it.rules)
            alertCreateTeamBinding.etDescription.setText(it.description)
            alertCreateTeamBinding.etName.setText(it.name)
        }

        val dialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(String.format(getString(R.string.enter) + "%s " + getString(R.string.detail), type ?: getString(R.string.team)))
            .setView(alertCreateTeamBinding.root)
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = alertCreateTeamBinding.etName.text.toString().trim()
                if (name.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.name_is_required))
                    alertCreateTeamBinding.etName.error = getString(R.string.please_enter_a_name)
                } else {
                    val description = alertCreateTeamBinding.etDescription.text.toString()
                    val services = alertCreateTeamBinding.etServices.text.toString()
                    val rules = alertCreateTeamBinding.etRules.text.toString()
                    if (team == null) {
                        createTeam(
                            name,
                            description,
                            if (alertCreateTeamBinding.spnTeamType.selectedItemPosition == 0) "local" else "sync",
                            services,
                            rules,
                            alertCreateTeamBinding.switchPublic.isChecked
                        )
                    } else {
                        updateTeam(team._id, name, description, services, rules)
                    }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun createTeam(name: String, description: String, teamType: String, services: String, rules: String, isPublic: Boolean) {
        lifecycleScope.launch {
            user?.let {
                teamRepository.createTeam(name, description, type ?: "team", teamType, services, rules, isPublic, it._id, it.parentCode, it.planetCode, it.id)
                Utilities.toast(activity, getString(R.string.team_created))
                loadTeams()
            }
        }
    }

    private fun updateTeam(teamId: String, name: String, description: String, services: String, rules: String) {
        lifecycleScope.launch {
            user?._id?.let {
                teamRepository.updateTeam(teamId, name, description, services, rules, it)
                Utilities.toast(activity, getString(R.string.team_updated))
                loadTeams()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTeams()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTeamBinding.rvTeamList.layoutManager = LinearLayoutManager(activity)
        setTeamList()
        fragmentTeamBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                lifecycleScope.launch {
                    val searchResult = if (TextUtils.isEmpty(charSequence)) {
                        teamList
                    } else {
                        teamRepository.searchTeams(charSequence.toString(), type)
                    }
                    updateAdapter(searchResult)
                }
            }
            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private fun setTeamList() {
        adapterTeamList = AdapterTeamList(requireActivity(), teamList, childFragmentManager, uploadManager)
        adapterTeamList.setType(type)
        adapterTeamList.setTeamListener(this)
        fragmentTeamBinding.rvTeamList.adapter = adapterTeamList
        listContentDescription(conditionApplied)
        showNoResultsMessage(teamList.isEmpty())
    }

    private fun sortAndSetAdapter() {
        lifecycleScope.launch {
            val sortedList = teamList.sortedWith(compareByDescending<RealmMyTeam> { team ->
                user?.id?.let { userId ->
                    when {
                        teamRepository.isTeamLeader(team._id, userId) -> 3
                        teamRepository.isMyTeam(team._id, userId) -> 2
                        else -> 1
                    }
                }
            }.thenByDescending { team ->
                teamRepository.getVisitCountForTeam(team._id)
            })
            updateAdapter(sortedList)
        }
    }

    private fun updateAdapter(list: List<RealmMyTeam>) {
        adapterTeamList = AdapterTeamList(requireActivity(), list, childFragmentManager, uploadManager)
        adapterTeamList.setTeamListener(this)
        fragmentTeamBinding.rvTeamList.adapter = adapterTeamList
        listContentDescription(conditionApplied)
        showNoResultsMessage(list.isEmpty(), fragmentTeamBinding.etSearch.text.toString())
    }

    override fun onEditTeam(team: RealmMyTeam) {
        createTeamAlert(team)
    }

    private fun listContentDescription(conditionApplied: Boolean) {
        fragmentTeamBinding.rvTeamList.contentDescription = if (conditionApplied) getString(R.string.enterprise_list) else getString(R.string.list_of_teams)
    }

    private fun showNoResultsMessage(show: Boolean, searchQuery: String = "") {
        fragmentTeamBinding.tvMessage.visibility = if (show) View.VISIBLE else View.GONE
        fragmentTeamBinding.tableTitle.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            fragmentTeamBinding.tvMessage.text = if (searchQuery.isNotEmpty()) {
                if (type == "enterprise") getString(R.string.no_enterprises_found_for_search, searchQuery)
                else getString(R.string.no_teams_found_for_search, searchQuery)
            } else {
                if (type == "enterprise") getString(R.string.no_enterprises_found)
                else getString(R.string.no_teams_found)
            }
        }
    }
}
