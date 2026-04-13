package org.ole.planet.myplanet.ui.surveys

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDialogFragment
import org.ole.planet.myplanet.databinding.FragmentSendSurveyBinding
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.ui.components.CheckboxAdapter
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class SendSurveyFragment : BaseDialogFragment() {
    private lateinit var fragmentSendSurveyBinding: FragmentSendSurveyBinding
    private var users: List<RealmUser> = emptyList()
    @Inject
    lateinit var submissionsRepository: SubmissionsRepository
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
                val selectedItems = (fragmentSendSurveyBinding.listUsers.adapter as CheckboxAdapter).selectedItemsList
                for (i in selectedItems) {
                    val u = users[i]
                    submissionsRepository.createSurveySubmission(id!!, u.id)
                }
                Utilities.toast(activity, getString(R.string.survey_sent_to_users))
                dismiss()
            }
        }
    }

    private fun initListView(users: List<RealmUser>) {
        val names = users.map { it.toString() }
        val adapter = CheckboxAdapter()
        fragmentSendSurveyBinding.listUsers.layoutManager = LinearLayoutManager(requireActivity())
        fragmentSendSurveyBinding.listUsers.adapter = adapter
        adapter.submitList(names)
    }

}
