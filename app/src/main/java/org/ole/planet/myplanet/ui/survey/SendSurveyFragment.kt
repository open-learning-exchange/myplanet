package org.ole.planet.myplanet.ui.survey

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import io.realm.Realm
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDialogFragment
import org.ole.planet.myplanet.databinding.FragmentSendSurveyBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date

class SendSurveyFragment : BaseDialogFragment() {
    private lateinit var fragmentSendSurveyBinding: FragmentSendSurveyBinding
    lateinit var mRealm: Realm
    lateinit var dbService: DatabaseService
    override val key: String
        get() = "surveyId"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSendSurveyBinding = FragmentSendSurveyBinding.inflate(inflater, container, false)
        dbService = DatabaseService(requireActivity())
        mRealm = dbService.realmInstance
        if (TextUtils.isEmpty(id)) {
            dismiss()
            return fragmentSendSurveyBinding.root
        }
        fragmentSendSurveyBinding.btnCancel.setOnClickListener { dismiss() }
        return fragmentSendSurveyBinding.root
    }

    private fun createSurveySubmission(userId: String?) {
        val mRealm = DatabaseService(requireActivity()).realmInstance
        val exam = mRealm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
        mRealm.beginTransaction()
        var sub = mRealm.where(RealmSubmission::class.java).equalTo("userId", userId)
            .equalTo("parentId", if (!TextUtils.isEmpty(exam?.courseId)) id + "@" + exam?.courseId else id)
            .sort("lastUpdateTime", Sort.DESCENDING).equalTo("status", "pending").findFirst()
        sub = RealmSubmission.createSubmission(sub, mRealm)
        sub.parentId = if (!TextUtils.isEmpty(exam?.courseId)) id + "@" + exam?.courseId else id
        sub.userId = userId
        sub.type = "survey"
        sub.status = "pending"
        sub.startTime = Date().time
        mRealm.commitTransaction()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val users: List<RealmUserModel> = mRealm.where(RealmUserModel::class.java).findAll()
        initListView(users)
        fragmentSendSurveyBinding.sendSurvey.setOnClickListener {
            for (i in fragmentSendSurveyBinding.listUsers.selectedItemsList.indices) {
                val u = users[i]
                createSurveySubmission(u.id)
            }
            Utilities.toast(activity, getString(R.string.survey_sent_to_users))
            dismiss()
        }
    }

    private fun initListView(users: List<RealmUserModel>) {
        val adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, users)
        fragmentSendSurveyBinding.listUsers.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        fragmentSendSurveyBinding.listUsers.adapter = adapter
    }
}
