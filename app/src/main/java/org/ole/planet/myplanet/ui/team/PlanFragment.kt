package org.ole.planet.myplanet.ui.team

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
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
    private var isEnterprise: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentPlanBinding = FragmentPlanBinding.inflate(inflater, container, false)
        return fragmentPlanBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUIWithTeamData(team)

        val isMyTeam = RealmMyTeam.isTeamLeader(team?._id, user?.id, mRealm)
        isEnterprise = team?.type?.equals("enterprise", ignoreCase = true) == true

        fragmentPlanBinding.btnAddPlan.text = if (isEnterprise) {
            getString(R.string.edit_mission_and_services)
        } else {
            getString(R.string.edit_plan)
        }

        fragmentPlanBinding.btnAddPlan.isVisible = isMyTeam
        fragmentPlanBinding.btnAddPlan.isEnabled = isMyTeam

        fragmentPlanBinding.btnAddPlan.setOnClickListener {
            if (isMyTeam) {
                editTeam()
            }
        }
    }

    private fun editTeam() {
        if (!isAdded) {
            return
        }
        team?.let {
            showCreateTeamDialog(requireContext(), requireActivity(), mRealm, it)
        }
    }

    private fun showCreateTeamDialog(context: Context, activity: FragmentActivity, realm: Realm, team: RealmMyTeam) {
        val alertCreateTeamBinding = AlertCreateTeamBinding.inflate(LayoutInflater.from(context))
        setupDialogFields(alertCreateTeamBinding, team)

        val dialog = AlertDialog.Builder(activity, R.style.AlertDialogTheme)
            .setTitle("${context.getString(R.string.enter)} ${team.type} ${context.getString(R.string.detail)}")
            .setView(alertCreateTeamBinding.root)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                handleSaveButtonClick(alertCreateTeamBinding, activity, context, realm, team, dialog)
            }
        }
        dialog.show()
    }

    private fun setupDialogFields(binding: AlertCreateTeamBinding, team: RealmMyTeam) {
        binding.spnTeamType.visibility = if (isEnterprise) View.GONE else View.VISIBLE
        binding.etServices.visibility = if (isEnterprise) View.VISIBLE else View.GONE
        binding.etRules.visibility = if (isEnterprise) View.VISIBLE else View.GONE
        binding.etDescription.hint = requireContext().getString(
            if (isEnterprise) R.string.entMission else R.string.what_is_your_team_s_plan
        )
        binding.etName.hint = requireContext().getString(
            if (isEnterprise) R.string.enter_enterprise_s_name else R.string.enter_team_s_name
        )

        binding.etServices.setText(team.services)
        binding.etRules.setText(team.rules)
        binding.etDescription.setText(team.description)
        binding.etName.setText(team.name)
    }

    private fun handleSaveButtonClick(binding: AlertCreateTeamBinding, activity: FragmentActivity, context: Context, realm: Realm, team: RealmMyTeam, dialog: AlertDialog) {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Utilities.toast(activity, context.getString(R.string.name_is_required))
            binding.etName.error = context.getString(R.string.please_enter_a_name)
            return
        }

        val userId = UserProfileDbHandler(activity).userModel?._id
        realm.executeTransaction {
            team.name = name
            team.services = binding.etServices.text.toString()
            team.rules = binding.etRules.text.toString()
            team.description = binding.etDescription.text.toString()
            team.createdBy = userId
            team.updated = true
        }
        updateUIWithTeamData(team)
        Utilities.toast(activity, context.getString(R.string.added_successfully))
        dialog.dismiss()
    }

    private fun updateUIWithTeamData(updatedTeam: RealmMyTeam?) {
        if (updatedTeam == null) return
        isEnterprise=  team?.type?.equals("enterprise", ignoreCase = true) == true

        val missionText = formatTeamDetail(updatedTeam.description,
            getString(if (isEnterprise) R.string.entMission else R.string.what_is_your_team_s_plan)
        )
        val servicesText = formatTeamDetail(updatedTeam.services,
            if (isEnterprise) getString(R.string.entServices) else ""
        )
        val rulesText = formatTeamDetail(updatedTeam.rules,
            if (isEnterprise) getString(R.string.entRules) else ""
        )

        val finalText = if (missionText.isEmpty() && servicesText.isEmpty() && rulesText.isEmpty()) {
            "<br/>" + (if (isEnterprise) getString(R.string.entEmptyDescription) else getString(R.string.this_team_has_no_description_defined)) + "<br/>"
        } else {
            missionText + servicesText + rulesText
        }

        fragmentPlanBinding.tvDescription.text = Html.fromHtml(finalText, Html.FROM_HTML_MODE_LEGACY)
        fragmentPlanBinding.tvDate.text = getString(
            R.string.two_strings,
            getString(R.string.created_on),
            updatedTeam.createdDate?.let { formatDate(it) }
        )
    }

    private fun formatTeamDetail(detail: String?, title: String): String {
        return if (detail?.trim().isNullOrEmpty()) "" else "<b>$title</b><br/>$detail<br/><br/>"
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}
