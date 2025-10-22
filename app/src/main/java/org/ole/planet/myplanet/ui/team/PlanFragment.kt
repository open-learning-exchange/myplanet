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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.TeamUpdateListener
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.databinding.FragmentPlanBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities

class PlanFragment : BaseTeamFragment() {
    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!
    private var isEnterprise: Boolean = false
    private var teamUpdateListener: TeamUpdateListener? = null

    fun setTeamUpdateListener(listener: TeamUpdateListener) {
        teamUpdateListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            teamFlow.collect { updatedTeam ->
                if (updatedTeam != null) {
                    updateUIWithTeamData(updatedTeam)
                    updateButtonVisibility(updatedTeam)
                }
            }
        }
        
        if (team != null) {
            updateUIWithTeamData(team)
            updateButtonVisibility(team!!)
        }
    }

    private fun updateButtonVisibility(currentTeam: RealmMyTeam) {
        val isMyTeam = RealmMyTeam.isTeamLeader(currentTeam._id, user?.id, mRealm)
        isEnterprise = currentTeam.type?.equals("enterprise", ignoreCase = true) == true

        binding.btnAddPlan.text = if (isEnterprise) {
            getString(R.string.edit_mission_and_services)
        } else {
            getString(R.string.edit_plan)
        }

        binding.btnAddPlan.isVisible = isMyTeam
        binding.btnAddPlan.isEnabled = isMyTeam

        binding.btnAddPlan.setOnClickListener {
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
            showCreateTeamDialog(requireContext(), requireActivity(), it)
        }
    }

    private fun showCreateTeamDialog(context: Context, activity: FragmentActivity, team: RealmMyTeam) {
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
                handleSaveButtonClick(alertCreateTeamBinding, activity, context, team, dialog)
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

        val teamTypePosition = when (team.teamType) {
            "local" -> 0
            "sync" -> 1
            else -> 0
        }
        binding.spnTeamType.setSelection(teamTypePosition)
        binding.switchPublic.isChecked = team.isPublic
    }

    private fun handleSaveButtonClick(
        binding: AlertCreateTeamBinding,
        activity: FragmentActivity,
        context: Context,
        team: RealmMyTeam,
        dialog: AlertDialog,
    ) {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Utilities.toast(activity, context.getString(R.string.name_is_required))
            binding.etName.error = context.getString(R.string.please_enter_a_name)
            return
        }

        val userId = user?.id ?: return
        val createdBy = userId
        val teamIdentifier = team._id?.takeIf { it.isNotBlank() }
            ?: team.teamId?.takeIf { it.isNotBlank() }
        if (teamIdentifier == null) {
            Utilities.toast(activity, context.getString(R.string.failed_to_add_please_retry))
            return
        }
        val servicesToSave = binding.etServices.text.toString()
        val rulesToSave = binding.etRules.text.toString()
        val descriptionToSave = binding.etDescription.text.toString()
        val teamType = when (binding.spnTeamType.selectedItemPosition) {
            0 -> "local"
            1 -> "sync"
            else -> ""
        }
        val isPublic = binding.switchPublic.isChecked

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val wasUpdated = teamRepository.updateTeamDetails(
                    teamId = teamIdentifier,
                    name = name,
                    description = descriptionToSave,
                    services = servicesToSave,
                    rules = rulesToSave,
                    teamType = teamType,
                    isPublic = isPublic,
                    createdBy = createdBy,
                )

                if (wasUpdated) {
                    val refreshedTeam = teamRepository.getTeamByDocumentIdOrTeamId(teamIdentifier)
                        ?: (this@PlanFragment.team ?: team)

                    refreshedTeam.apply {
                        this.name = name
                        this.services = servicesToSave
                        this.rules = rulesToSave
                        this.description = descriptionToSave
                        this.teamType = teamType
                        this.isPublic = isPublic
                        this.createdBy = createdBy.takeIf { it.isNotBlank() } ?: this.createdBy
                        this.updated = true
                    }

                    this@PlanFragment.team = refreshedTeam
                    updateUIWithTeamData(refreshedTeam)
                    teamUpdateListener?.onTeamDetailsUpdated()
                    Utilities.toast(requireContext(), context.getString(R.string.added_successfully))
                    dialog.dismiss()
                } else {
                    Utilities.toast(requireContext(), context.getString(R.string.failed_to_add_please_retry))
                }
            } catch (e: Exception) {
                Utilities.toast(requireContext(), context.getString(R.string.failed_to_add_please_retry))
            }
        }
    }

    private fun updateUIWithTeamData(updatedTeam: RealmMyTeam?) {
        if (updatedTeam == null) return
        isEnterprise = updatedTeam.type?.equals("enterprise", ignoreCase = true) == true

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

        binding.tvDescription.text = Html.fromHtml(finalText, Html.FROM_HTML_MODE_LEGACY)
        binding.tvDate.text = getString(
            R.string.two_strings,
            getString(R.string.created_on),
            updatedTeam.createdDate?.let { formatDate(it) }
        )
    }

    private fun formatTeamDetail(detail: String?, title: String): String {
        if (detail?.trim().isNullOrEmpty()) return ""
        val formattedDetail = detail?.replace("\n", "<br/>")
        return "<b>$title</b><br/>$formattedDetail<br/><br/>"    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
