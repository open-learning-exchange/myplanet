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
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date

class TeamFragment : Fragment(), AdapterTeamList.OnClickTeamItem {
    private lateinit var fragmentTeamBinding: FragmentTeamBinding
    private lateinit var alertCreateTeamBinding: AlertCreateTeamBinding
    private lateinit var mRealm: Realm
    var type: String? = null
    var user: RealmUserModel? = null
    private var teamList: RealmResults<RealmMyTeam>? = null
    private lateinit var adapterTeamList: AdapterTeamList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            type = requireArguments().getString("type")
            if (TextUtils.isEmpty(type)) {
                type = "team"
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamBinding = FragmentTeamBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(requireContext()).realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel

        if (user?.isGuest() == true) {
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
        teamList = mRealm.where(RealmMyTeam::class.java).isEmpty("teamId")
            .notEqualTo("status", "archived").findAllAsync()

        teamList?.addChangeListener { _ ->
            updatedTeamList()
        }
        return fragmentTeamBinding.root
    }

    private fun createTeamAlert(team: RealmMyTeam?) {
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
                        fragmentTeamBinding.etSearch.visibility = View.VISIBLE
                        fragmentTeamBinding.tableTitle.visibility = View.VISIBLE
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
        val user = UserProfileDbHandler(requireContext()).userModel!!
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

    override fun onDestroy() {
        super.onDestroy()
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
    }

    override fun onResume() {
        super.onResume()
        setTeamList()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTeamBinding.rvTeamList.layoutManager = LinearLayoutManager(activity)
        setTeamList()
        fragmentTeamBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                val query = mRealm.where(RealmMyTeam::class.java).isEmpty("teamId")
                    .notEqualTo("status", "archived")
                    .contains("name", charSequence.toString(), Case.INSENSITIVE)
                val (list, conditionApplied) = getList(query)
                val sortedList = sortTeams(list)
                val adapterTeamList = AdapterTeamList(
                    activity as Context, sortedList, mRealm, childFragmentManager
                )
                adapterTeamList.setTeamListener(this@TeamFragment)
                fragmentTeamBinding.rvTeamList.adapter = adapterTeamList
                listContentDescription(conditionApplied)
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
        val query = mRealm.where(RealmMyTeam::class.java)
            .isEmpty("teamId")
            .notEqualTo("status", "archived")
        val (list, conditionApplied) = getList(query)
        adapterTeamList = activity?.let { AdapterTeamList(it, list, mRealm, childFragmentManager) } ?: return
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
        BaseRecyclerFragment.showNoData(fragmentTeamBinding.tvMessage, itemCount, "$type")
        if (itemCount == 0) {
            fragmentTeamBinding.etSearch.visibility = View.GONE
            fragmentTeamBinding.tableTitle.visibility = View.GONE
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
        createTeamAlert(team!!)
    }

    private fun updatedTeamList() {
        activity?.runOnUiThread {
            val query = mRealm.where(RealmMyTeam::class.java).isEmpty("teamId").notEqualTo("status", "archived")
            val (filteredList, conditionApplied) = getList(query)
            val sortedList = sortTeams(filteredList)
            val adapterTeamList = AdapterTeamList(activity as Context, sortedList, mRealm, childFragmentManager).apply {
                setType(type)
                setTeamListener(this@TeamFragment)
            }

            fragmentTeamBinding.rvTeamList.adapter = adapterTeamList
            listContentDescription(conditionApplied)
        }
    }

    private fun listContentDescription(conditionApplied: Boolean) {
        if (conditionApplied) {
            fragmentTeamBinding.rvTeamList.contentDescription = getString(R.string.enterprise_list)
        } else {
            fragmentTeamBinding.rvTeamList.contentDescription = getString(R.string.list_of_teams)
        }
    }
}
