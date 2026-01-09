package org.ole.planet.myplanet.ui.teams

import android.content.SharedPreferences
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
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.callback.OnTeamActionsListener
import org.ole.planet.myplanet.callback.OnUpdateCompleteListener
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamFragment : Fragment(), TeamsAdapter.OnClickTeamItem, OnUpdateCompleteListener,
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
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    private val viewModel: TeamViewModel by viewModels()
    var type: String? = null
    private var fromDashboard: Boolean = false
    var user: RealmUserModel? = null
    private var teamList: List<RealmMyTeam> = emptyList()
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
        user = userSessionManager.getUserModelCopy()

        if (user?.isGuest() == true) {
            binding.addTeam.visibility = View.GONE
        } else {
            binding.addTeam.visibility = View.VISIBLE
        }

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

        val builder = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(String.format(getString(R.string.enter) + "%s " + getString(R.string.detail), if (type == null) getString(R.string.team) else type))
            .setView(alertCreateTeamBinding.root).setPositiveButton(getString(R.string.save), null).setNegativeButton(getString(R.string.cancel), null)
        val dialog = builder.create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
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
                            return@setOnClickListener
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            val teamTypeForValidation = if (type == "enterprise") "enterprise" else "team"
                            val excludeTeamId = if (team != null) (team._id ?: team.teamId) else null
                            val nameExists = teamsRepository.isTeamNameExists(name, teamTypeForValidation, excludeTeamId)

                            if (nameExists) {
                                val duplicateMessage = if (type == "enterprise") {
                                    getString(R.string.enterprise_name_already_exists)
                                } else {
                                    getString(R.string.team_name_already_exists)
                                }
                                Utilities.toast(activity, duplicateMessage)
                                alertCreateTeamBinding.etName.error = duplicateMessage
                                return@launch
                            }

                            if (team == null) {
                                if (type == "enterprise") {
                                    teamsRepository.createEnterprise(
                                        name = name,
                                        description = description,
                                        services = services,
                                        rules = rules,
                                        isPublic = alertCreateTeamBinding.switchPublic.isChecked,
                                        user = userModel,
                                    ).onSuccess {
                                        binding.etSearch.visibility = View.VISIBLE
                                        binding.tableTitle.visibility = View.VISIBLE
                                        Utilities.toast(activity, getString(R.string.enterprise_created))
                                        refreshTeamList()
                                        dialog.dismiss()
                                    }.onFailure {
                                        Utilities.toast(activity, failureMessage)
                                    }
                                } else {
                                    teamsRepository.createTeam(
                                        category = type,
                                        name = name,
                                        description = description,
                                        teamType = selectedTeamType,
                                        isPublic = alertCreateTeamBinding.switchPublic.isChecked,
                                        user = userModel,
                                    ).onSuccess {
                                        binding.etSearch.visibility = View.VISIBLE
                                    binding.tableTitle.visibility = View.VISIBLE
                                    Utilities.toast(activity, getString(R.string.team_created))
                                    refreshTeamList()
                                    dialog.dismiss()
                                    }.onFailure {
                                        Utilities.toast(activity, failureMessage)
                                    }
                                }
                            } else {
                                val targetTeamId = team._id ?: team.teamId
                                if (targetTeamId.isNullOrBlank()) {
                                    Utilities.toast(activity, failureMessage)
                                    return@launch
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
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeTeamData()
        refreshTeamList()
        setupTextWatcher()
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


    private fun getList(searchText: String): Pair<List<RealmMyTeam>, Boolean> {
        val nameFilteredList = teamList.filter {
            it.name?.contains(searchText, ignoreCase = true) == true
        }

        val typeFilteredList: List<RealmMyTeam>
        val newConditionApplied: Boolean

        if (TextUtils.isEmpty(type) || type == "team") {
            typeFilteredList = nameFilteredList.filter { it.type != "enterprise" }
            newConditionApplied = false
        } else {
            typeFilteredList = nameFilteredList.filter { it.type == "enterprise" }
            newConditionApplied = true
        }

        return Pair(typeFilteredList, newConditionApplied)
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
                        teamsRepository.getMyTeamsFlow(userId).collectLatest {
                            teamList = it
                            setTeamList()
                        }
                    }
                }
                type == "enterprise" -> {
                    conditionApplied = true
                    teamList = teamsRepository.getShareableEnterprises()
                    setTeamList()
                }
                else -> {
                    conditionApplied = false
                    teamList = teamsRepository.getShareableTeams()
                    setTeamList()
                }
            }
        }
    }

    override fun onEditTeam(team: TeamDetails?) {
        team?.let { createTeamAlert(it) }
    }

    override fun onLeaveTeam(team: TeamDetails, user: RealmUserModel?) {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setMessage(R.string.confirm_exit)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.leaveTeam(team._id!!, user?.id)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onRequestToJoin(team: TeamDetails, user: RealmUserModel?) {
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
