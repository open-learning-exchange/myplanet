package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamFragment : Fragment(), AdapterTeamList.OnClickTeamItem, AdapterTeamList.OnUpdateCompleteListener {
    private var _binding: FragmentTeamBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertCreateTeamBinding: AlertCreateTeamBinding
    private lateinit var mRealm: Realm
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var teamRepository: TeamRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    var type: String? = null
    private var fromDashboard: Boolean = false
    var user: RealmUserModel? = null
    private var teamList: RealmResults<RealmMyTeam>? = null
    private lateinit var adapterTeamList: AdapterTeamList
    private var conditionApplied: Boolean = false
    private var fragmentStartTime: Long = 0
    private var dataLoadStartTime: Long = 0

    companion object {
        private const val TAG = "Team.Fragment"
    }

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
        fragmentStartTime = System.currentTimeMillis()
        Log.d(TAG, "onCreateView: START - timestamp: $fragmentStartTime")

        _binding = FragmentTeamBinding.inflate(inflater, container, false)
        mRealm = databaseService.realmInstance
        user = userProfileDbHandler.getUserModelCopy()
        Log.d(TAG, "onCreateView: View binding and realm setup completed - elapsed: ${System.currentTimeMillis() - fragmentStartTime}ms")

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

        dataLoadStartTime = System.currentTimeMillis()
        Log.d(TAG, "onCreateView: Starting team list query - elapsed: ${dataLoadStartTime - fragmentStartTime}ms")

        if (fromDashboard) {
            teamList = getMyTeamsByUserId(mRealm, settings)
        } else {
            val query = mRealm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            teamList = if (TextUtils.isEmpty(type) || type == "team") {
                conditionApplied = false
                query.notEqualTo("type", "enterprise").findAllAsync()
            } else {
                conditionApplied = true
                query.equalTo("type", "enterprise").findAllAsync()
            }
        }
        Log.d(TAG, "onCreateView: Team list query created - elapsed: ${System.currentTimeMillis() - fragmentStartTime}ms")

        teamList?.addChangeListener { _ ->
            val changeListenerTime = System.currentTimeMillis()
            Log.d(TAG, "onCreateView: Team list change listener triggered - elapsed: ${changeListenerTime - fragmentStartTime}ms")
            updatedTeamList()
        }
        Log.d(TAG, "onCreateView: END - total elapsed: ${System.currentTimeMillis() - fragmentStartTime}ms")
        return binding.root
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
                            if (team == null) {
                                teamRepository.createTeam(
                                    category = type,
                                    name = name,
                                    description = description,
                                    services = services,
                                    rules = rules,
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
                            } else {
                                val targetTeamId = team._id ?: team.teamId
                                if (targetTeamId.isNullOrBlank()) {
                                    Utilities.toast(activity, failureMessage)
                                    return@launch
                                }
                                teamRepository.updateTeam(
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

    override fun onResume() {
        super.onResume()
        setTeamList()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvTeamList.layoutManager = LinearLayoutManager(activity)
        setTeamList()
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                if (TextUtils.isEmpty(charSequence)) {
                    showNoResultsMessage(false)
                    updatedTeamList()
                    return
                }
                var list: List<RealmMyTeam>
                var conditionApplied = false
                if(fromDashboard){
                    list = teamList?.filter {
                        it.name?.contains(charSequence.toString(), ignoreCase = true) == true
                    } ?: emptyList()
                } else {
                    val query = mRealm.where(RealmMyTeam::class.java).isEmpty("teamId")
                        .notEqualTo("status", "archived")
                        .contains("name", charSequence.toString(), Case.INSENSITIVE)
                    val result = getList(query)
                    list = result.first
                    conditionApplied = result.second
                }

                if (list.isEmpty()) {
                    showNoResultsMessage(true, charSequence.toString())
                    binding.rvTeamList.adapter = null
                } else {
                    showNoResultsMessage(false)
                    val sortedList = list.sortedWith(compareByDescending<RealmMyTeam> {
                        it.name?.startsWith(charSequence.toString(), ignoreCase = true)
                    }.thenBy { it.name })

                    val adapterTeamList = AdapterTeamList(
                        activity as Context,
                        sortedList,
                        childFragmentManager,
                        teamRepository,
                        user,
                    )
                    adapterTeamList.setTeamListener(this@TeamFragment)
                    adapterTeamList.setUpdateCompleteListener(this@TeamFragment)
                    binding.rvTeamList.adapter = adapterTeamList
                    listContentDescription(conditionApplied)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private fun getList(query: RealmQuery<RealmMyTeam>): Pair<List<RealmMyTeam>, Boolean> {
        var queried = query
        val conditionApplied: Boolean
        queried = if (TextUtils.isEmpty(type) || type == "team") {
            conditionApplied = false
            queried.notEqualTo("type", "enterprise")
        } else {
            conditionApplied = true
            queried.equalTo("type", "enterprise")
        }

        return Pair(queried.findAll(), conditionApplied)
    }

    private fun setTeamList() {
        val setTeamListStartTime = System.currentTimeMillis()
        Log.d(TAG, "setTeamList: START - elapsed from fragment start: ${setTeamListStartTime - fragmentStartTime}ms")

        val list = teamList
        if (list == null) {
            Log.d(TAG, "setTeamList: Team list is null, returning")
            return
        }

        val adapterStartTime = System.currentTimeMillis()
        adapterTeamList = activity?.let {
            AdapterTeamList(it, list, childFragmentManager, teamRepository, user)
        } ?: return
        Log.d(TAG, "setTeamList: Adapter created - elapsed: ${System.currentTimeMillis() - adapterStartTime}ms")

        adapterTeamList.setType(type)
        adapterTeamList.setTeamListener(this@TeamFragment)
        adapterTeamList.setUpdateCompleteListener(this@TeamFragment)
        requireView().findViewById<View>(R.id.type).visibility =
            if (type == null) {
                View.GONE
            } else {
                View.VISIBLE
            }

        val setAdapterStartTime = System.currentTimeMillis()
        binding.rvTeamList.adapter = adapterTeamList
        Log.d(TAG, "setTeamList: Adapter set to RecyclerView - elapsed: ${System.currentTimeMillis() - setAdapterStartTime}ms")

        listContentDescription(conditionApplied)
        Log.d(TAG, "setTeamList: END - total elapsed: ${System.currentTimeMillis() - setTeamListStartTime}ms")
    }

    private fun refreshTeamList() {
        mRealm.refresh()
        teamList?.removeAllChangeListeners()

        if (fromDashboard) {
            teamList = getMyTeamsByUserId(mRealm, settings)
        } else {
            val query = mRealm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            teamList = if (TextUtils.isEmpty(type) || type == "team") {
                conditionApplied = false
                query.notEqualTo("type", "enterprise").findAllAsync()
            } else {
                conditionApplied = true
                query.equalTo("type", "enterprise").findAllAsync()
            }
        }

        teamList?.addChangeListener { _ ->
            updatedTeamList()
        }
        setTeamList()
    }

    override fun onEditTeam(team: RealmMyTeam?) {
        team?.let { createTeamAlert(it) }
    }

    override fun onUpdateComplete(itemCount: Int) {
        val totalElapsed = System.currentTimeMillis() - fragmentStartTime
        Log.d(TAG, "onUpdateComplete: Adapter update complete with $itemCount items - total elapsed from fragment start: ${totalElapsed}ms")

        if (itemCount == 0) {
            showNoResultsMessage(true)
        } else {
            showNoResultsMessage(false)
        }
    }

    private fun updatedTeamList() {
        val updateStartTime = System.currentTimeMillis()
        Log.d(TAG, "updatedTeamList: START - elapsed from fragment start: ${updateStartTime - fragmentStartTime}ms")

        viewLifecycleOwner.lifecycleScope.launch {
            if (!::adapterTeamList.isInitialized || binding.rvTeamList.adapter == null) {
                Log.d(TAG, "updatedTeamList: Adapter not initialized, calling setTeamList()")
                setTeamList()
            } else {
                Log.d(TAG, "updatedTeamList: Calling adapter.updateList()")
                adapterTeamList.updateList()
            }
            listContentDescription(conditionApplied)
            Log.d(TAG, "updatedTeamList: END - elapsed: ${System.currentTimeMillis() - updateStartTime}ms")
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
        teamList?.removeAllChangeListeners()
        if (this::adapterTeamList.isInitialized) {
            adapterTeamList.cleanup()
        }
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        _binding = null
        super.onDestroyView()
    }
}
