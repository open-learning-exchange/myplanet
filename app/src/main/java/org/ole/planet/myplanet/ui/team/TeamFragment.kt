package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.ui.team.TeamViewModel

@AndroidEntryPoint
class TeamFragment : Fragment(), AdapterTeamList.OnClickTeamItem {
    private lateinit var fragmentTeamBinding: FragmentTeamBinding
    private lateinit var mRealm: Realm
    private val viewModel: TeamViewModel by viewModels()
    var type: String? = null
    private var fromDashboard: Boolean = false
    var user: RealmUserModel? = null
    private var teamList: RealmResults<RealmMyTeam>? = null
    private lateinit var adapterTeamList: AdapterTeamList
    private var conditionApplied: Boolean = false
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
        fragmentTeamBinding = FragmentTeamBinding.inflate(inflater, container, false)
        mRealm = viewModel.realm
        user = UserProfileDbHandler(requireActivity()).userModel

        if (user?.isGuest() == true) {
            fragmentTeamBinding.addTeam.visibility = View.GONE
        } else {
            fragmentTeamBinding.addTeam.visibility = View.VISIBLE
        }

        fragmentTeamBinding.addTeam.setOnClickListener {
            viewModel.createTeamAlert(this, type, null) {
                fragmentTeamBinding.etSearch.visibility = View.VISIBLE
                fragmentTeamBinding.tableTitle.visibility = View.VISIBLE
                setTeamList()
            }
        }
        fragmentTeamBinding.tvFragmentInfo.text = if (TextUtils.equals(type, "enterprise")) {
            getString(R.string.enterprises)
        } else {
            getString(R.string.team)
        }
        val pair = viewModel.queryTeams(fromDashboard, type, settings)
        teamList = pair.first
        conditionApplied = pair.second

        teamList?.addChangeListener { _ ->
            updatedTeamList()
        }
        return fragmentTeamBinding.root
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
                if (TextUtils.isEmpty(charSequence)) {
                    showNoResultsMessage(false)
                    updatedTeamList()
                    return
                }
                val result = viewModel.searchTeams(
                    charSequence.toString(),
                    fromDashboard,
                    type,
                    teamList!!
                )
                val list = result.first
                val conditionApplied = result.second

                if (list.isEmpty()) {
                    showNoResultsMessage(true, charSequence.toString())
                    fragmentTeamBinding.rvTeamList.adapter = null
                } else {
                    showNoResultsMessage(false)
                    val sortedList = list.sortedWith(compareByDescending<RealmMyTeam> {
                        it.name?.startsWith(charSequence.toString(), ignoreCase = true)
                    }.thenBy { it.name })

                    val adapterTeamList = AdapterTeamList(
                        activity as Context, sortedList, mRealm, childFragmentManager, viewModel.uploadManager
                    )
                    adapterTeamList.setTeamListener(this@TeamFragment)
                    fragmentTeamBinding.rvTeamList.adapter = adapterTeamList
                    listContentDescription(conditionApplied)
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }


    private fun setTeamList() {
        val list = teamList!!
        adapterTeamList = activity?.let { AdapterTeamList(it, list, mRealm, childFragmentManager, viewModel.uploadManager) } ?: return
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
        viewModel.createTeamAlert(this, type, team!!) {
            fragmentTeamBinding.etSearch.visibility = View.VISIBLE
            fragmentTeamBinding.tableTitle.visibility = View.VISIBLE
            setTeamList()
        }
    }

    private fun updatedTeamList() {
        activity?.runOnUiThread {
            val sortedList = sortTeams(teamList!!)
            val adapterTeamList = AdapterTeamList(activity as Context, sortedList, mRealm, childFragmentManager, viewModel.uploadManager).apply {
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
