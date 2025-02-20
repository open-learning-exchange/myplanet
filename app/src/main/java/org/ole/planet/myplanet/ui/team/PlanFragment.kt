package org.ole.planet.myplanet.ui.team

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentPlanBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class PlanFragment : BaseTeamFragment() {
    private lateinit var fragmentPlanBinding: FragmentPlanBinding
    private var missionText: String? = null
    private var servicesText: String? = null
    private var rulesText = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentPlanBinding = FragmentPlanBinding.inflate(inflater, container, false)
        return fragmentPlanBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (TextUtils.equals(team?.type, "enterprise")) {
            missionText = if (team?.description?.trim { it <= ' ' }?.isEmpty() == true) {
                ""
            } else {
                "<b>" + getString(R.string.entMission) + "</b><br/>" + team?.description + "<br/><br/>"
            }
            servicesText = if (team?.services?.trim { it <= ' ' }?.isEmpty() == true) {
                ""
            } else {
                "<b>" + getString(R.string.entServices) + "</b><br/>" + team?.services + "<br/><br/>"
            }
            rulesText = if (team?.rules?.trim { it <= ' ' }?.isEmpty() == true) {
                ""
            } else {
                "<b>" + getString(R.string.entRules) + "</b><br/>" + team?.rules
            }
            fragmentPlanBinding.tvDescription.text = Html.fromHtml(missionText + servicesText + rulesText, Html.FROM_HTML_MODE_LEGACY)
            if (fragmentPlanBinding.tvDescription.text.toString().isEmpty()) {
                fragmentPlanBinding.tvDescription.text = Html.fromHtml("<br/>" + getString(R.string.entEmptyDescription) + "<br/>", Html.FROM_HTML_MODE_LEGACY)
            }
        } else {
            fragmentPlanBinding.tvDescription.text = team?.description
        }
        fragmentPlanBinding.tvDate.text = getString(R.string.two_strings, getString(R.string.created_on), team?.createdDate?.let { formatDate(it) })

        fragmentPlanBinding.btnAddPlan.setOnClickListener {
            editTeam()
        }
    }
    private fun editTeam() {
        if (!isAdded) {
            Log.d("EditTeam", "Fragment is not added, returning.")
            return
        }
        Log.d("EditTeam", "Existing TeamFragment found, invoking createTeamAlert.")
        team?.let { showCreateTeamDialog(requireContext(), requireActivity(), mRealm, it) }

    }

    fun showCreateTeamDialog(context: Context, activity: FragmentActivity, realm: Realm, team: RealmMyTeam?) {
        Log.d("ShowCreateTeamDialog", "Opening dialog for team: ${team?.name}")

        val alertCreateTeamBinding = AlertCreateTeamBinding.inflate(LayoutInflater.from(context))
        val type = if (team?.type == "enterprise") "enterprise" else "team"

        if (type == "enterprise") {
            alertCreateTeamBinding.spnTeamType.visibility = View.GONE
            alertCreateTeamBinding.etDescription.hint = context.getString(R.string.entMission)
            alertCreateTeamBinding.etName.hint = context.getString(R.string.enter_enterprise_s_name)
        } else {
            alertCreateTeamBinding.etServices.visibility = View.GONE
            alertCreateTeamBinding.etRules.visibility = View.GONE
            alertCreateTeamBinding.etDescription.hint = context.getString(R.string.what_is_your_team_s_plan)
            alertCreateTeamBinding.etName.hint = context.getString(R.string.enter_team_s_name)
        }

        team?.let {
            Log.d("ShowCreateTeamDialog", "Populating fields with existing team data.")
            alertCreateTeamBinding.etServices.setText(it.services)
            alertCreateTeamBinding.etRules.setText(it.rules)
            alertCreateTeamBinding.etDescription.setText(it.description)
            alertCreateTeamBinding.etName.setText(it.name)
        }

        val builder = AlertDialog.Builder(activity, R.style.AlertDialogTheme)
            .setTitle(String.format(context.getString(R.string.enter) + "%s " + context.getString(R.string.detail), type))
            .setView(alertCreateTeamBinding.root)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)

        val dialog = builder.create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                val name = alertCreateTeamBinding.etName.text.toString().trim()
                if (name.isEmpty()) {
                    Log.d("ShowCreateTeamDialog", "Validation failed: Team name is empty.")
                    Utilities.toast(activity, context.getString(R.string.name_is_required))
                    alertCreateTeamBinding.etName.error = context.getString(R.string.please_enter_a_name)
                } else {
                    val userId = UserProfileDbHandler(activity).userModel?._id
                    if (team != null) {
                        Log.d("ShowCreateTeamDialog", "Updating team details in Realm.")
                        realm.executeTransaction {
                            team.name = name
                            team.services = alertCreateTeamBinding.etServices.text.toString()
                            team.rules = alertCreateTeamBinding.etRules.text.toString()
                            team.description = alertCreateTeamBinding.etDescription.text.toString()
                            team.createdBy = userId
                            team.updated = true
                        }
                        updateUIWithTeamData(team)
                    }
                    Log.d("ShowCreateTeamDialog", "Team details updated successfully.")
                    Utilities.toast(activity, context.getString(R.string.added_successfully))
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    fun updateUIWithTeamData(updatedTeam: RealmMyTeam) {
        Log.d("TeamFragment", "Updating UI with new team data: ${updatedTeam.name}")
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, this)
            .setReorderingAllowed(true)  // Optionally allow reordering of fragments
            .commit()
    }



    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}