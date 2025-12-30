package org.ole.planet.myplanet.ui.survey

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDialogFragment
import org.ole.planet.myplanet.databinding.FragmentSendSurveyBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.ui.components.CheckboxListView
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class SendSurveyFragment : BaseDialogFragment() {
    private lateinit var fragmentSendSurveyBinding: FragmentSendSurveyBinding
    private var users: List<RealmUserModel> = emptyList()
    @Inject
    lateinit var submissionRepository: SubmissionsRepository
    @Inject
    lateinit var userRepository: UserRepository
    override val key: String
        get() = "surveyId"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSendSurveyBinding = FragmentSendSurveyBinding.inflate(inflater, container, false)
        if (TextUtils.isEmpty(id)) {
            dismiss()
            return fragmentSendSurveyBinding.root
        }
        fragmentSendSurveyBinding.btnCancel.setOnClickListener { dismiss() }
        return fragmentSendSurveyBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            users = userRepository.getAllUsers()
            initListView(users)
        }
        fragmentSendSurveyBinding.sendSurvey.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                for (i in fragmentSendSurveyBinding.listUsers.selectedItemsList.indices) {
                    val u = users[i]
                    submissionRepository.createSurveySubmission(id!!, u.id)
                }
                Utilities.toast(activity, getString(R.string.survey_sent_to_users))
                dismiss()
            }
        }
    }

    private fun initListView(users: List<RealmUserModel>) {
        val adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, users)
        fragmentSendSurveyBinding.listUsers.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        fragmentSendSurveyBinding.listUsers.adapter = adapter
    }

}
