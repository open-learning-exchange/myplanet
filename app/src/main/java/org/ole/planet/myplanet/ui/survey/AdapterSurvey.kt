package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowSurveyBinding
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getNoOfSubmissionByUser
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getRecentSubmissionDate
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.survey.AdapterSurvey.ViewHolderSurvey

class AdapterSurvey(private val context: Context, private val examList: List<RealmStepExam>, private val mRealm: Realm, private val userId: String) : RecyclerView.Adapter<ViewHolderSurvey>() {
    private lateinit var rowSurveyBinding: RowSurveyBinding
    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderSurvey {
        rowSurveyBinding = RowSurveyBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderSurvey(rowSurveyBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderSurvey, position: Int) {
        rowSurveyBinding.tvTitle.text = examList[position].name
        rowSurveyBinding.startSurvey.setOnClickListener {
            AdapterMySubmission.openSurvey(listener, examList[position].id, false)
        }
        val questions: List<RealmExamQuestion> = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", examList[position].id).findAll()
        if (questions.isEmpty()) {
            rowSurveyBinding.sendSurvey.visibility = View.GONE
            rowSurveyBinding.startSurvey.visibility = View.GONE
        }
        rowSurveyBinding.startSurvey.text = if (examList[position].isFromNation) context.getString(R.string.take_survey) else context.getString(
                R.string.record_survey
            )
        val noOfSubmission = getNoOfSubmissionByUser(examList[position].id, userId, mRealm)
        val subDate = getRecentSubmissionDate(examList[position].id, userId, mRealm)
        rowSurveyBinding.tvNoSubmissions.text = noOfSubmission
        rowSurveyBinding.tvDate.text = subDate
    }

    override fun getItemCount(): Int {
        return examList.size
    }

    inner class ViewHolderSurvey(rowSurveyBinding: RowSurveyBinding) : RecyclerView.ViewHolder(rowSurveyBinding.root) {
        init {
            rowSurveyBinding.startSurvey.visibility = View.VISIBLE
            rowSurveyBinding.sendSurvey.visibility = View.GONE
            rowSurveyBinding.sendSurvey.setOnClickListener {
                val current = examList[bindingAdapterPosition]
                listener?.sendSurvey(current)
            }
        }
    }
}
