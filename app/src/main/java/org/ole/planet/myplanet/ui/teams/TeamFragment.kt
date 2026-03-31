package org.ole.planet.myplanet.ui.teams

import org.ole.planet.myplanet.utils.Utilities
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnTeamActionsListener
import org.ole.planet.myplanet.callback.OnTeamEditListener
import org.ole.planet.myplanet.callback.OnUpdateCompleteListener
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager

@AndroidEntryPoint
class TeamFragment : Fragment(), OnTeamEditListener, OnUpdateCompleteListener,
    OnTeamActionsListener {
    private var _binding: FragmentTeamBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertCreateTeamBinding: AlertCreateTeamBinding
    @Inject
    lateinit var teamsRepository: TeamsRepository
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private val viewModel: TeamViewModel by viewModels()
    var type: String? = null
    private var fromDashboard: Boolean = false
    var user: RealmUser? = null
    private var teamList: List<TeamSummary> = emptyList()
    private lateinit var teamListAdapter: TeamsAdapter
    private var conditionApplied: Boolean = false
    private var textWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            fromDashboard = requireArguments().getBoolean("fromDashboard")
            type = requireArguments().getString("type")
            if (TextUtils.isEmpty(type)) {
                type = "team"
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamBinding.inflate(inflater, container, false)
        binding.addTeam.setOnClickListener { createTeamAlert(null) }
        binding.tvFragmentInfo.text = if (TextUtils.equals(type, "enterprise")) {
            getString(R.string.enterprises)
        } else {
            getString(R.string.team)
        }
        return binding.root
    }

     fun createTeamAlert(team: TeamDetails?) {
        alertCreateTeamBinding = AlertCreateTeamBinding.inflate(LayoutInflater.from(context))
        setupTeamAlertUI(team)

        val builder = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(String.format(getString(R.string.enter) + "%s " + getString(R.string.detail), if (type == null) getString(R.string.team) else type))
            .setView(alertCreateTeamBinding.root).setPositiveButton(getString(R.string.save), null).setNegativeButton(getString(R.string.cancel), null)
        val dialog = builder.create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                handleTeamSave(team, dialog)
            }
        }
        dialog.show()
    }

    private fun setupTeamAlertUI(team: TeamDetails?) {
        if (TextUtils.equals(type, "enterprise")) {
            alertCreateTeamBinding.spnTeamType.visibility = View.GONE
            alertCreateTeamBinding.etDescription.hint = getString(R.string.entMission)
            alertCreateTeamBinding.etName.hint = getString(R.string.enter_enterprise_s_name)
        } else {
            alertCreateTeamBinding.etServices.visibility = View.GONE
            alertCreateTeamBinding.etRules.visibility = View.GONE
            alertCreateTeamBinding.etDescription.hint = getString(R.string.what_is_your_team_s_plan)
            alertCreateTeamBinding.etName.hint = getString(R.string.enter_team_s_name)
        }
        if (team != null) {
            alertCreateTeamBinding.etServices.setText(team.services)
            alertCreateTeamBinding.etRules.setText(team.rules)
            alertCreateTeamBinding.etDescription.setText(team.description)
            alertCreateTeamBinding.etName.setText(team.name)
        }
    }

    private fun handleTeamSave(team: TeamDetails?, dialog: AlertDialog) {
        val name = alertCreateTeamBinding.etName.text.toString().trim()
        val description = alertCreateTeamBinding.etDescription.text.toString()
        val services = alertCreateTeamBinding.etServices.text.toString()
        val rules = alertCreateTeamBinding.etRules.text.toString()
        val selectedTeamType =
            if (alertCreateTeamBinding.spnTeamType.selectedItemPosition == 0) {
                "local"
            } else {
                "sync"
            }
        val currentUser = user
        when {
            name.isEmpty() -> {
                Utilities.toast(activity, getString(R.string.name_is_required))
                alertCreateTeamBinding.etName.error = getString(R.string.please_enter_a_name)
            } else -> {
                val failureMessage = getString(R.string.request_failed_please_retry)
                val userModel = currentUser ?: run {
                    Utilities.toast(activity, failureMessage)
                    return
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    if (team == null) {
                        createNewTeam(name, description, services, rules, selectedTeamType, userModel, dialog, failureMessage)
                    } else {
                        updateExistingTeam(team, name, description, services, rules, userModel, dialog, failureMessage)
                    }
                }
            }
        }
    }

    private suspend fun createNewTeam(
        name: String, description: String, services: String, rules: String,
        selectedTeamType: String, userModel: RealmUser, dialog: AlertDialog, failureMessage: String
    ) {
        val result = viewModel.createTeam(
            name = name,
            description = description,
            services = services,
            rules = rules,
            teamType = selectedTeamType,
            isPublic = alertCreateTeamBinding.switchPublic.isChecked,
            category = type,
            userModel = userModel
        )
        when (result) {
            is TeamActionResult.NameExists -> {
                val duplicateMessage = if (type == "enterprise") {
                    getString(R.string.enterprise_name_already_exists)
                } else {
                    getString(R.string.team_name_already_exists)
                }
                Utilities.toast(activity, duplicateMessage)
                alertCreateTeamBinding.etName.error = duplicateMessage
            }
            is TeamActionResult.Success -> {
                binding.etSearch.visibility = View.VISIBLE
                binding.tableTitle.visibility = View.VISIBLE
                val successMessage = if (type == "enterprise") {
                    getString(R.string.enterprise_created)
                } else {
                    getString(R.string.team_created)
                }
                Utilities.toast(activity, successMessage)
                refreshTeamList()
                dialog.dismiss()
            }
            is TeamActionResult.Failure -> {
                Utilities.toast(activity, failureMessage)
            }
        }
    }

    private suspend fun updateExistingTeam(
        team: TeamDetails, name: String, description: String, services: String, rules: String,
        userModel: RealmUser, dialog: AlertDialog, failureMessage: String
    ) {
        val teamTypeForValidation = if (type == "enterprise") "enterprise" else "team"
        val excludeTeamId = team._id ?: team.teamId
        val nameExists = teamsRepository.isTeamNameExists(name, teamTypeForValidation, excludeTeamId)

        if (nameExists) {
            val duplicateMessage = if (type == "enterprise") {
                getString(R.string.enterprise_name_already_exists)
            } else {
                getString(R.string.team_name_already_exists)
            }
            Utilities.toast(activity, duplicateMessage)
            alertCreateTeamBinding.etName.error = duplicateMessage
            return
        }

        val targetTeamId = team._id ?: team.teamId
        if (targetTeamId.isNullOrBlank()) {
            Utilities.toast(activity, failureMessage)
            return
        }
        teamsRepository.updateTeam(
            teamId = targetTeamId,
            name = name,
            description = description,
            services = services,
            rules = rules,
            updatedBy = userModel._id,
        ).onSuccess { updated ->
            if (updated) {
                binding.etSearch.visibility = View.VISIBLE
                binding.tableTitle.visibility = View.VISIBLE
                Utilities.toast(activity, getString(R.string.team_created))
                refreshTeamList()
                dialog.dismiss()
            } else {
                Utilities.toast(activity, failureMessage)
            }
        }.onFailure {
            Utilities.toast(activity, failureMessage)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            user = userSessionManager.getUserModel()
            if (user?.isGuest() == true) {
                binding.addTeam.visibility = View.GONE
            } else {
                binding.addTeam.visibility = View.VISIBLE
            }
            setupRecyclerView()
            observeTeamData()
            refreshTeamList()
            setupTextWatcher()
        }
    }

    private fun setupRecyclerView() {
        binding.rvTeamList.layoutManager = LinearLayoutManager(activity)
        teamListAdapter = TeamsAdapter(
            requireActivity(),
            childFragmentManager,
            user,
            sharedPrefManager
        ).apply {
            setType(type)
            setTeamListener(this@TeamFragment)
            setUpdateCompleteListener(this@TeamFragment)
            setTeamActionsListener(this@TeamFragment)
        }
        binding.rvTeamList.adapter = teamListAdapter
    }

    private fun observeTeamData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.teamData.collectLatest { teamDataList ->
                teamListAdapter.submitList(teamDataList)
                onUpdateComplete(teamDataList.size)
                listContentDescription(conditionApplied)
            }
        }
    }

    private fun setupTextWatcher() {
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                val filteredList = if (TextUtils.isEmpty(charSequence)) {
                    teamList
                } else {
                    teamList.filter {
                        it.name?.contains(charSequence.toString(), ignoreCase = true) == true
                    }
                }
                viewModel.prepareTeamData(filteredList, user?.id)
            }
            override fun afterTextChanged(editable: Editable) {}
        }
        binding.etSearch.addTextChangedListener(textWatcher)
    }


    private fun setTeamList() {
        viewModel.prepareTeamData(teamList, user?.id)
        listContentDescription(conditionApplied)
    }

    private fun refreshTeamList() {
        viewLifecycleOwner.lifecycleScope.launch {
            when {
                fromDashboard -> {
                    user?._id?.let { userId ->
                        teamsRepository.getMyTeamsFlow(userId).collectLatest { list ->
                            teamList = list.mapNotNull {
                                val id = it._id ?: return@mapNotNull null
                                org.ole.planet.myplanet.model.TeamSummary(
                                    _id = id,
                                    name = it.name ?: "",
                                    teamType = it.teamType,
                                    teamPlanetCode = it.teamPlanetCode,
                                    createdDate = it.createdDate,
                                    type = it.type,
                                    status = it.status,
                                    teamId = it.teamId,
                                    description = it.description,
                                    services = it.services,
                                    rules = it.rules
                                )
                            }
                            setTeamList()
                        }
                    }
                }
                type == "enterprise" -> {
                    conditionApplied = true
                    teamList = teamsRepository.getShareableEnterpriseSummaries(null)
                    setTeamList()
                }
                else -> {
                    conditionApplied = false
                    teamList = teamsRepository.getTeamSummaries(null)
                    setTeamList()
                }
            }
        }
    }

    override fun onEditTeam(team: TeamDetails?) {
        team?.let { createTeamAlert(it) }
    }

    override fun onLeaveTeam(team: TeamDetails, user: RealmUser?) {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setMessage(R.string.confirm_exit)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.leaveTeam(team._id!!, user?.id)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onRequestToJoin(team: TeamDetails, user: RealmUser?) {
        viewModel.requestToJoin(team._id!!, user?.id, user?.planetCode, team.teamType)
    }

    override fun onUpdateComplete(itemCount: Int) {
        if (itemCount == 0) {
            showNoResultsMessage(true)
        } else {
            showNoResultsMessage(false)
        }
    }

    private fun listContentDescription(conditionApplied: Boolean) {
        if (conditionApplied) {
            binding.rvTeamList.contentDescription = getString(R.string.enterprise_list)
        } else {
            binding.rvTeamList.contentDescription = getString(R.string.list_of_teams)
        }
    }

    private fun showNoResultsMessage(show: Boolean, searchQuery: String = "") {
        if (show) {
            binding.tvMessage.text = if (searchQuery.isNotEmpty()) {
                if (TextUtils.equals(type, "enterprise")){
                    getString(R.string.no_enterprises_found_for_search, searchQuery)
                } else {
                    getString(R.string.no_teams_found_for_search, searchQuery)
                }
            } else {
                if (TextUtils.equals(type, "enterprise")) {
                    getString(R.string.no_enterprises_found)
                } else {
                    getString(R.string.no_teams_found)
                }
            }
            binding.tvMessage.visibility = View.VISIBLE
            binding.etSearch.visibility = View.VISIBLE
            binding.tableTitle.visibility = View.GONE
        } else {
            binding.tvMessage.visibility = View.GONE
            binding.etSearch.visibility = View.VISIBLE
            binding.tableTitle.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        _binding?.etSearch?.removeTextChangedListener(textWatcher)
        textWatcher = null
        _binding = null
        super.onDestroyView()
    }
}
