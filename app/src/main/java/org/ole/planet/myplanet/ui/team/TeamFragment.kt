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
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentTeamBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class TeamFragment : Fragment(), AdapterTeamList.OnClickTeamItem {
    private var mBinding: FragmentTeamBinding? = null
    private var mRealm: Realm? = null
    private var type: String? = null
    private var user: RealmUserModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            type = requireArguments().getString("type")
            Utilities.log("Team fragment")
            if (TextUtils.isEmpty(type)) {
                type = "team"
            }
        }
        Utilities.log("Team fragment")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mBinding = FragmentTeamBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(activity).realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel

        mBinding?.addTeam?.setOnClickListener { view: View? -> createTeamAlert(null) }

        return mBinding?.root
    }

    private fun createTeamAlert(team: RealmMyTeam?) {
        val binding = AlertCreateTeamBinding.inflate(layoutInflater)
        if (TextUtils.equals(type, "enterprise")) {
            binding.spnTeamType.visibility = View.GONE
            binding.etDescription.hint = getString(R.string.entMission)
            binding.etName.hint = "Enter enterprise's name"
        } else {
            binding.etServices.visibility = View.GONE
            binding.etRules.visibility = View.GONE
            binding.etDescription.hint = "What is your team's plan?"
            binding.etName.hint = "Enter team's name"
        }
        if (team != null) {
            binding.etServices.setText(team.services)
            binding.etRules.setText(team.rules)
            binding.etDescription.setText(team.description)
            binding.etName.setText(team.name)
        }


        val builder = AlertDialog.Builder(requireActivity())
            .setTitle(String.format("Enter %s detail", if (type == null) "Team" else type))
            .setView(binding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                val map = HashMap<String, String>()
                val userId = user?._id
                val name = binding.etName.text.toString().trim()
                map["desc"] = binding.etDescription.text.toString()
                map["services"] = binding.etServices.text.toString()
                map["rules"] = binding.etRules.text.toString()

                when {
                    name.isEmpty() -> {
                        Utilities.toast(activity, "Name is required")
                        binding.etName.error = "Please enter a name"
                    }
                    else -> {
                        if (team == null) {
                            createTeam(
                                name,
                                if (binding.spnTeamType.selectedItemPosition == 0) "local" else "sync",
                                map,
                                binding.switchPublic.isChecked
                            )
                        } else {
                            if (!team.realm.isInTransaction)
                                team.realm.beginTransaction()
                            team.name = name
                            team.services = binding.etServices.text.toString()
                            team.rules = binding.etRules.text.toString()
                            team.limit = 12
                            team.description = binding.etDescription.text.toString()
                            team.createdBy = userId
                            team.isUpdated = true
                            team.realm.commitTransaction()
                        }
                        Utilities.toast(activity, "Team Created")
                        setTeamList()
                        // dialog won't close by default
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    fun createTeam(name: String?, type: String?, map: HashMap<String, String>, isPublic: Boolean) {
        val user = UserProfileDbHandler(activity).userModel

        if (!mRealm!!.isInTransaction) mRealm!!.beginTransaction()
        var teamId = AndroidDecrypter.generateIv()
        val team = mRealm!!.createObject(RealmMyTeam::class.java, teamId)
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
        team.user_id = user.id
        team.parentCode = user.parentCode
        team.teamPlanetCode = user.planetCode
        team.isUpdated = true

        //create member ship
        val teamMemberObj =
            mRealm!!.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
        teamMemberObj.userId = user._id
        teamMemberObj.teamId = teamId
        teamMemberObj.teamPlanetCode = user.planetCode
        teamMemberObj.userPlanetCode = user.planetCode
        teamMemberObj.docType = "membership"
        teamMemberObj.isLeader = true
        teamMemberObj.teamType = type
        teamMemberObj.isUpdated = true

        mRealm!!.commitTransaction()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mRealm != null && !mRealm!!.isClosed) mRealm!!.close()
    }

    override fun onResume() {
        super.onResume()
        setTeamList()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mBinding?.rvTeamList!!.layoutManager = LinearLayoutManager(activity)
        setTeamList()
        mBinding?.etSearch!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                val query = mRealm!!.where(RealmMyTeam::class.java).isEmpty("teamId")
                    .notEqualTo("status", "archived")
                    .contains("name", charSequence.toString(), Case.INSENSITIVE)
                val adapterTeamList = AdapterTeamList(
                    activity as Context,
                    getList(query),
                    mRealm!!,
                    childFragmentManager
                )
                adapterTeamList.setTeamListener(this@TeamFragment)
                mBinding?.rvTeamList!!.adapter = adapterTeamList
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private fun getList(query: RealmQuery<RealmMyTeam>): List<RealmMyTeam> {
        var query = query
        query = if (TextUtils.isEmpty(type) || type == "team") {
            query.notEqualTo("type", "enterprise")
        } else {
            query.equalTo("type", "enterprise")
        }
        return query.findAll()
    }

    private fun setTeamList() {
        val query = mRealm!!.where(RealmMyTeam::class.java).isEmpty("teamId")
            .notEqualTo("status", "archived")
        val adapterTeamList =
            activity?.let { AdapterTeamList(it, getList(query), mRealm!!, childFragmentManager) }
        adapterTeamList?.setType(type)
        adapterTeamList?.setTeamListener(this@TeamFragment)
        requireView().findViewById<View>(R.id.type).visibility =
            if (type == null) View.VISIBLE else View.GONE
        mBinding?.rvTeamList!!.adapter = adapterTeamList
    }

    override fun onEditTeam(team: RealmMyTeam?) {
        createTeamAlert(team!!)
    }
}