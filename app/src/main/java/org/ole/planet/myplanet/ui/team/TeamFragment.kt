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
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class TeamFragment : Fragment(), AdapterTeamList.OnClickTeamItem {
    private var _binding: FragmentTeamBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertCreateTeamBinding: AlertCreateTeamBinding
    private lateinit var mRealm: Realm
    @Inject
    lateinit var databaseService: DatabaseService
    var type: String? = null
    private var fromDashboard: Boolean = false
    var user: RealmUserModel? = null
    private var teamList: RealmResults<RealmMyTeam>? = null
    private lateinit var adapterTeamList: AdapterTeamList
    private var conditionApplied: Boolean = false
    @Inject
    lateinit var teamRepository: TeamRepository
    private val settings by lazy {
        requireActivity().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
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
        _binding = FragmentTeamBinding.inflate(inflater, container, false)
        mRealm = databaseService.realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel

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
                val map = HashMap<String, String>()
                val userId = user?._id
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
                            createTeam(name,
                                if (alertCreateTeamBinding.spnTeamType.selectedItemPosition == 0) "local" else "sync", map,
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
                            team.createdBy = userId
                            team.updated = true
                            team.realm.commitTransaction()
                        }
                        binding.etSearch.visibility = View.VISIBLE
                        binding.tableTitle.visibility = View.VISIBLE
                        Utilities.toast(activity, getString(R.string.team_created))
                        setTeamList()
                        // dialog won't close by default
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun createTeam(name: String?, type: String?, map: HashMap<String, String>, isPublic: Boolean) {
        val user = UserProfileDbHandler(requireContext()).userModel ?: return
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val teamId = AndroidDecrypter.generateIv()
        val team = mRealm.createObject(RealmMyTeam::class.java, teamId)
        team.status = "active"
        team.createdDate = Date().time
        if (TextUtils.equals(this.type, "enterprise")) {
            team.type = "enterprise"
            team.services = map["services"]
            team.rules = map["rules"]
        } else {
            team.type = "team"
            team.teamType = type
        }
        team.name = name
        team.description = map["desc"]
        team.createdBy = user._id
        team.teamId = ""
        team.isPublic = isPublic
        team.userId = user.id
        team.parentCode = user.parentCode
        team.teamPlanetCode = user.planetCode
        team.updated = true

        //create member ship
        val teamMemberObj = mRealm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
        teamMemberObj.userId = user._id
        teamMemberObj.teamId = teamId
        teamMemberObj.teamPlanetCode = user.planetCode
        teamMemberObj.userPlanetCode = user.planetCode
        teamMemberObj.docType = "membership"
        teamMemberObj.isLeader = true
        teamMemberObj.teamType = type
        teamMemberObj.updated = true

        mRealm.commitTransaction()
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
                        activity as Context, sortedList, mRealm, childFragmentManager, teamRepository
                    )
                    adapterTeamList.setTeamListener(this@TeamFragment)
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
        val list = teamList ?: return
        adapterTeamList = activity?.let { AdapterTeamList(it, list, mRealm, childFragmentManager, teamRepository) } ?: return
        adapterTeamList.setType(type)
        adapterTeamList.setTeamListener(this@TeamFragment)
        requireView().findViewById<View>(R.id.type).visibility =
            if (type == null) {
                View.GONE
            } else {
                View.VISIBLE
            }
        binding.rvTeamList.adapter = adapterTeamList
        listContentDescription(conditionApplied)
        val itemCount = adapterTeamList.itemCount

        if (itemCount == 0) {
            showNoResultsMessage(true)
        } else {
            showNoResultsMessage(false)
        }
    }

    private fun sortTeams(list: List<RealmMyTeam>): List<RealmMyTeam> {
        val user = user?.id
        return list.sortedWith(compareByDescending<RealmMyTeam> { team ->
            when {
                RealmMyTeam.isTeamLeader(team.teamId, user, mRealm) -> 3
                team.isMyTeam(user, mRealm) -> 2
                else -> 1
            }
        })
    }

    override fun onEditTeam(team: RealmMyTeam?) {
        team?.let { createTeamAlert(it) }
    }

    private fun updatedTeamList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = teamList ?: return@launch
            val sortedList = sortTeams(list)
            val adapterTeamList = AdapterTeamList(activity as Context, sortedList, mRealm, childFragmentManager, teamRepository).apply {
                setType(type)
                setTeamListener(this@TeamFragment)
            }

            binding.rvTeamList.adapter = adapterTeamList
            listContentDescription(conditionApplied)
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
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        _binding = null
        super.onDestroyView()
    }
}
