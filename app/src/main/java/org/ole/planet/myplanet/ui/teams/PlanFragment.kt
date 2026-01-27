package org.ole.planet.myplanet.ui.teams

import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.callback.OnTeamUpdateListener
import org.ole.planet.myplanet.databinding.FragmentPlanBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utils.TimeUtils.formatDate
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class PlanFragment : BaseTeamFragment() {
    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!
    private var teamUpdateListener: OnTeamUpdateListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (teamId.isNotEmpty()) {
            val description = if (TextUtils.isEmpty(team?.description)) getString(R.string.no_description_available) else team?.description
            binding.tvDescription.text = description
            binding.tvDate.text = "${getString(R.string.created_on)} ${formatDate(team?.createdDate ?: 0)}"
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            val isMember = teamId.let { teamsRepository.isMember(user?.id, it) }
            val isLeader = user?.let { teamsRepository.isTeamLeader(teamId, it.id) } == true

            if (isMember && isLeader) {
                binding.btnAddPlan.visibility = View.VISIBLE
                binding.btnAddPlan.setOnClickListener {
                    showCreateTeamDialog()
                }
            } else {
                binding.btnAddPlan.visibility = View.GONE
            }
        }
    }

    private fun showCreateTeamDialog() {
        val view = LayoutInflater.from(activity).inflate(R.layout.alert_create_team, null)
        val etName = view.findViewById<EditText>(R.id.et_name)
        val etDescription = view.findViewById<EditText>(R.id.et_description)
        val spnType = view.findViewById<Spinner>(R.id.spn_team_type)
        if (team != null) {
            etName.setText(team?.name)
            etDescription.setText(team?.description)
            spnType.visibility = View.GONE
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_team)
            .setView(view)
            .setPositiveButton(R.string.save) { _: DialogInterface?, _: Int ->
                val name = etName.text.toString()
                val description = etDescription.text.toString()
                if (name.isEmpty()) {
                    Utilities.toast(activity, getString(R.string.name_is_required))
                } else {
                    saveTeamDetails(name, description)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveTeamDetails(name: String, description: String) {
        lifecycleScope.launch {
            if (team != null) {
                val success = teamsRepository.updateTeam(teamId, name, description, team?.services ?: "", team?.rules ?: "", null)
                if (success.isSuccess) {
                    Utilities.toast(activity, getString(R.string.team_updated))
                    teamUpdateListener?.onTeamDetailsUpdated()
                } else {
                    Utilities.toast(activity, getString(R.string.unable_to_update_team))
                }
            }
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    fun setTeamUpdateListener(listener: OnTeamUpdateListener) {
        this.teamUpdateListener = listener
    }
}
