package org.ole.planet.myplanet.ui.surveys

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDialogFragment
import org.ole.planet.myplanet.databinding.FragmentSendSurveyBinding
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.ui.components.CheckboxAdapter
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class SendSurveyFragment : BaseDialogFragment() {
    private lateinit var fragmentSendSurveyBinding: FragmentSendSurveyBinding
    private var users: List<UserEntity> = emptyList()
    private val viewModel: SurveysViewModel by viewModels()
    override val key: String
        get() = "surveyId"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSendSurveyBinding = FragmentSendSurveyBinding.inflate(inflater, container, false)
        if (id.isNullOrEmpty()) {
            dismiss()
            return fragmentSendSurveyBinding.root
        }
        fragmentSendSurveyBinding.btnCancel.setOnClickListener { dismiss() }
        return fragmentSendSurveyBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectWhenStarted(viewModel.users) { u ->
            users = u
            initListView(u)
        }
        collectWhenStarted(viewModel.surveySent) { sent ->
            if (sent) {
                Utilities.toast(activity, getString(R.string.survey_sent_to_users))
                dismiss()
            }
        }
        viewModel.loadUsers()

        fragmentSendSurveyBinding.sendSurvey.setOnClickListener {
            val surveyId = id ?: return@setOnClickListener
            val selectedItems = (fragmentSendSurveyBinding.listUsers.adapter as CheckboxAdapter).selectedItemsList
            val selectedUserIds = selectedItems.mapNotNull { users.getOrNull(it)?.id }
            viewModel.sendSurveyToUsers(surveyId, selectedUserIds)
        }
    }

    private fun initListView(users: List<UserEntity>) {
        val names = users.map { it.toString() }
        val adapter = CheckboxAdapter()
        adapter.submitList(names)
        fragmentSendSurveyBinding.listUsers.layoutManager = LinearLayoutManager(requireActivity())
        fragmentSendSurveyBinding.listUsers.adapter = adapter
    }

}
