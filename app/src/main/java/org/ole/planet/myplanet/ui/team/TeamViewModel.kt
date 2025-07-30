package org.ole.planet.myplanet.ui.team

import android.content.SharedPreferences
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.CourseRepository
import org.ole.planet.myplanet.di.LibraryRepository
import org.ole.planet.myplanet.di.UserRepository
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getMyTeamsByUserId
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.utilities.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Utilities

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val databaseService: DatabaseService,
    private val userRepository: UserRepository,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    val uploadManager: UploadManager
) : ViewModel() {

    val realm: Realm
        get() = databaseService.realmInstance

    fun queryTeams(
        fromDashboard: Boolean,
        type: String?,
        settings: SharedPreferences
    ): Pair<RealmResults<RealmMyTeam>, Boolean> {
        val mRealm = realm
        return if (fromDashboard) {
            Pair(getMyTeamsByUserId(mRealm, settings), false)
        } else {
            val query = mRealm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .notEqualTo("status", "archived")
            val conditionApplied: Boolean
            val list = if (TextUtils.isEmpty(type) || type == "team") {
                conditionApplied = false
                query.notEqualTo("type", "enterprise").findAllAsync()
            } else {
                conditionApplied = true
                query.equalTo("type", "enterprise").findAllAsync()
            }
            Pair(list, conditionApplied)
        }
    }

    fun searchTeams(
        search: String,
        fromDashboard: Boolean,
        type: String?,
        teamList: RealmResults<RealmMyTeam>
    ): Pair<List<RealmMyTeam>, Boolean> {
        return if (fromDashboard) {
            Pair(teamList.filter { it.name?.contains(search, ignoreCase = true) == true }, false)
        } else {
            val query = realm.where(RealmMyTeam::class.java).isEmpty("teamId")
                .notEqualTo("status", "archived")
                .contains("name", search, Case.INSENSITIVE)
            getList(query, type)
        }
    }

    fun getList(query: RealmQuery<RealmMyTeam>, type: String?): Pair<List<RealmMyTeam>, Boolean> {
        var q = query
        val conditionApplied: Boolean
        q = if (TextUtils.isEmpty(type) || type == "team") {
            conditionApplied = false
            q.notEqualTo("type", "enterprise")
        } else {
            conditionApplied = true
            q.equalTo("type", "enterprise")
        }
        return Pair(q.findAll(), conditionApplied)
    }

    fun createTeamAlert(
        fragment: Fragment,
        type: String?,
        team: RealmMyTeam?,
        onSuccess: () -> Unit
    ) {
        val context = fragment.requireContext()
        val binding = AlertCreateTeamBinding.inflate(LayoutInflater.from(context))
        if (TextUtils.equals(type, "enterprise")) {
            binding.spnTeamType.visibility = View.GONE
            binding.etDescription.hint = context.getString(R.string.entMission)
            binding.etName.hint = context.getString(R.string.enter_enterprise_s_name)
        } else {
            binding.etServices.visibility = View.GONE
            binding.etRules.visibility = View.GONE
            binding.etDescription.hint = context.getString(R.string.what_is_your_team_s_plan)
            binding.etName.hint = context.getString(R.string.enter_team_s_name)
        }
        team?.let {
            binding.etServices.setText(it.services)
            binding.etRules.setText(it.rules)
            binding.etDescription.setText(it.description)
            binding.etName.setText(it.name)
        }
        val builder = AlertDialog.Builder(fragment.requireActivity(), R.style.AlertDialogTheme)
            .setTitle(
                String.format(
                    context.getString(R.string.enter) + "%s " + context.getString(R.string.detail),
                    type ?: context.getString(R.string.team)
                )
            )
            .setView(binding.root)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                val map = HashMap<String, String>()
                val name = binding.etName.text.toString().trim()
                map["desc"] = binding.etDescription.text.toString()
                map["services"] = binding.etServices.text.toString()
                map["rules"] = binding.etRules.text.toString()
                if (name.isEmpty()) {
                    Utilities.toast(fragment.activity, context.getString(R.string.name_is_required))
                    binding.etName.error = context.getString(R.string.please_enter_a_name)
                } else {
                    if (team == null) {
                        createTeam(
                            type,
                            name,
                            if (binding.spnTeamType.selectedItemPosition == 0) "local" else "sync",
                            map,
                            binding.switchPublic.isChecked
                        )
                    } else {
                        if (!team.realm.isInTransaction) {
                            team.realm.beginTransaction()
                        }
                        team.name = name
                        team.services = "${binding.etServices.text}"
                        team.rules = "${binding.etRules.text}"
                        team.limit = 12
                        team.description = "${binding.etDescription.text}"
                        team.createdBy = userRepository.getCurrentUser()?._id
                        team.updated = true
                        team.realm.commitTransaction()
                    }
                    Utilities.toast(fragment.activity, context.getString(R.string.team_created))
                    onSuccess()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun createTeam(
        fragmentType: String?,
        name: String?,
        teamType: String?,
        map: HashMap<String, String>,
        isPublic: Boolean
    ) {
        val user = userRepository.getCurrentUser() ?: return
        val mRealm = databaseService.realmInstance
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        val teamId = AndroidDecrypter.generateIv()
        val team = mRealm.createObject(RealmMyTeam::class.java, teamId)
        team.status = "active"
        team.createdDate = Date().time
        if (TextUtils.equals(fragmentType, "enterprise")) {
            team.type = "enterprise"
            team.services = map["services"]
            team.rules = map["rules"]
        } else {
            team.type = "team"
            team.teamType = teamType
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
        val teamMemberObj =
            mRealm.createObject(RealmMyTeam::class.java, AndroidDecrypter.generateIv())
        teamMemberObj.userId = user.id
        teamMemberObj.teamId = teamId
        teamMemberObj.teamPlanetCode = user.planetCode
        teamMemberObj.userPlanetCode = user.planetCode
        teamMemberObj.docType = "membership"
        teamMemberObj.isLeader = true
        teamMemberObj.teamType = teamType
        teamMemberObj.updated = true
        mRealm.commitTransaction()
    }
}

