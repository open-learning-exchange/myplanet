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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.ui.team.TeamViewModel

class TeamFragment : Fragment(), AdapterTeamList.OnClickTeamItem {
    private lateinit var fragmentTeamBinding: FragmentTeamBinding
    private lateinit var alertCreateTeamBinding: AlertCreateTeamBinding
    var type: String? = null
    private var fromDashboard: Boolean = false
    private lateinit var adapterTeamList: AdapterTeamList
    private var conditionApplied: Boolean = false
    private var teamList: List<RealmMyTeam> = emptyList()
    private val teamViewModel: TeamViewModel by viewModels()

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
        fragmentTeamBinding = FragmentTeamBinding.inflate(inflater, container, false)

        if (teamViewModel.isGuest()) {
            fragmentTeamBinding.addTeam.visibility = View.GONE
        } else {
            fragmentTeamBinding.addTeam.visibility = View.VISIBLE
        }

        fragmentTeamBinding.addTeam.setOnClickListener { createTeamAlert(null) }
        fragmentTeamBinding.tvFragmentInfo.text = if (TextUtils.equals(type, "enterprise")) {
            getString(R.string.enterprises)
        } else {
            getString(R.string.team)
        }
        teamViewModel.init(fromDashboard, type)
        teamViewModel.toastMessage.observe(viewLifecycleOwner) {
            Utilities.toast(activity, it)
        }
        return fragmentTeamBinding.root
    }

     fun createTeamAlert(team: RealmMyTeam?) {
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
                val map = HashMap<String, String>()
                val name = alertCreateTeamBinding.etName.text.toString().trim()
                map["desc"] = alertCreateTeamBinding.etDescription.text.toString()
                map["services"] = alertCreateTeamBinding.etServices.text.toString()
                map["rules"] = alertCreateTeamBinding.etRules.text.toString()
                when {
                    name.isEmpty() -> {
                        Utilities.toast(activity, getString(R.string.name_is_required))
                        alertCreateTeamBinding.etName.error = getString(R.string.please_enter_a_name)
                    } else -> {
                        if (team == null) {
                            teamViewModel.createTeam(
                                name,
                                map,
                                alertCreateTeamBinding.switchPublic.isChecked
                            )
                        } else {
                            if (!team.realm.isInTransaction) {
                                team.realm.beginTransaction()
                            }
                            team.name = name
                            team.services = "${alertCreateTeamBinding.etServices.text}"
                            team.rules = "${alertCreateTeamBinding.etRules.text}"
                            team.limit = 12
                            team.description = "${alertCreateTeamBinding.etDescription.text}"
                            team.updated = true
                            team.realm.commitTransaction()
                        }
                        fragmentTeamBinding.etSearch.visibility = View.VISIBLE
                        fragmentTeamBinding.tableTitle.visibility = View.VISIBLE
                        Utilities.toast(activity, getString(R.string.team_created))
                        // dialog won't close by default
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        teamViewModel.loadTeams()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTeamBinding.rvTeamList.layoutManager = LinearLayoutManager(activity)
        observeTeams()
        fragmentTeamBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                teamViewModel.searchTeams(s.toString())
            }
            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun setTeamList(list: List<RealmMyTeam>) {
        adapterTeamList = activity?.let { AdapterTeamList(it, list, teamViewModel.realmInstance, childFragmentManager) } ?: return
        adapterTeamList.setType(type)
        adapterTeamList.setTeamListener(this@TeamFragment)
        requireView().findViewById<View>(R.id.type).visibility =
            if (type == null) {
                View.GONE
            } else {
                View.VISIBLE
            }
        fragmentTeamBinding.rvTeamList.adapter = adapterTeamList
        listContentDescription(conditionApplied)
        val itemCount = adapterTeamList.itemCount

        if (itemCount == 0) {
            showNoResultsMessage(true)
        } else {
            showNoResultsMessage(false)
        }
    }

    private fun sortTeams(list: List<RealmMyTeam>): List<RealmMyTeam> {
        val userId = teamViewModel.getUserId()
        val realm = teamViewModel.realmInstance
        return list.sortedWith(compareByDescending<RealmMyTeam> { team ->
            when {
                RealmMyTeam.isTeamLeader(team.teamId, userId, realm) -> 3
                team.isMyTeam(userId, realm) -> 2
                else -> 1
            }
        })
    }

    override fun onEditTeam(team: RealmMyTeam?) {
        createTeamAlert(team!!)
    }

    private fun observeTeams() {
        teamViewModel.teams.observe(viewLifecycleOwner, Observer { list ->
            teamList = list
            conditionApplied = !(type.isNullOrEmpty() || type == "team")
            if (list.isEmpty()) {
                showNoResultsMessage(true)
                fragmentTeamBinding.rvTeamList.adapter = null
            } else {
                showNoResultsMessage(false)
                setTeamList(list)
            }
        })
    }

    private fun listContentDescription(conditionApplied: Boolean) {
        if (conditionApplied) {
            fragmentTeamBinding.rvTeamList.contentDescription = getString(R.string.enterprise_list)
        } else {
            fragmentTeamBinding.rvTeamList.contentDescription = getString(R.string.list_of_teams)
        }
    }

    private fun showNoResultsMessage(show: Boolean, searchQuery: String = "") {
        if (show) {
            fragmentTeamBinding.tvMessage.text = if (searchQuery.isNotEmpty()) {
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
            fragmentTeamBinding.tvMessage.visibility = View.VISIBLE
            fragmentTeamBinding.etSearch.visibility = View.VISIBLE
            fragmentTeamBinding.tableTitle.visibility = View.GONE
        } else {
            fragmentTeamBinding.tvMessage.visibility = View.GONE
            fragmentTeamBinding.etSearch.visibility = View.VISIBLE
            fragmentTeamBinding.tableTitle.visibility = View.VISIBLE
        }
    }
}
