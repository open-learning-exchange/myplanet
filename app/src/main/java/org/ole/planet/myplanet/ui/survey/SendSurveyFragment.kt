package org.ole.planet.myplanet.ui.survey

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.Sort
import java.util.Date
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDialogFragment
import org.ole.planet.myplanet.databinding.FragmentSendSurveyBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.createSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class SendSurveyFragment : BaseDialogFragment() {
    private lateinit var fragmentSendSurveyBinding: FragmentSendSurveyBinding
    private var mRealm: Realm? = null
    private lateinit var users: List<RealmUserModel>
    @Inject
    lateinit var databaseService: DatabaseService
    override val key: String
        get() = "surveyId"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSendSurveyBinding = FragmentSendSurveyBinding.inflate(inflater, container, false)
        databaseService.withRealm { realm ->
            users = realm.where(RealmUserModel::class.java).findAll().let { realm.copyFromRealm(it) }
        }
        if (TextUtils.isEmpty(id)) {
            dismiss()
            return fragmentSendSurveyBinding.root
        }
        fragmentSendSurveyBinding.btnCancel.setOnClickListener { dismiss() }
        return fragmentSendSurveyBinding.root
    }

    private fun createSurveySubmission(userId: String?) {
        databaseService.withRealm { realm ->
            val exam = realm.where(RealmStepExam::class.java).equalTo("id", id).findFirst()
            realm.executeTransaction { r ->
                var sub = r.where(RealmSubmission::class.java).equalTo("userId", userId)
                    .equalTo(
                        "parentId",
                        if (!TextUtils.isEmpty(exam?.courseId)) id + "@" + exam?.courseId else id,
                    )
                    .sort("lastUpdateTime", Sort.DESCENDING).equalTo("status", "pending").findFirst()
                sub = createSubmission(sub, r)
                sub.parentId = if (!TextUtils.isEmpty(exam?.courseId)) id + "@" + exam?.courseId else id
                sub.userId = userId
                sub.type = "survey"
                sub.status = "pending"
                sub.startTime = Date().time
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

    override fun onDestroy() {
        mRealm?.let {
            if (!it.isClosed) {
                it.close()
            }
        }
        super.onDestroy()
    }
}
