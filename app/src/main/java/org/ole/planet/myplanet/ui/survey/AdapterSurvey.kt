package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.SurveyAdoptListener
import org.ole.planet.myplanet.databinding.RowSurveyBinding
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getNoOfSubmissionByTeam
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getNoOfSubmissionByUser
import org.ole.planet.myplanet.model.RealmSubmission.Companion.getRecentSubmissionDate
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterSurvey(
    private val context: Context,
    private val mRealm: Realm,
    private val userId: String?,
    private val isTeam: Boolean,
    val teamId: String?,
    private val surveyAdoptListener: SurveyAdoptListener
) : RecyclerView.Adapter<AdapterSurvey.ViewHolderSurvey>() {
    private var examList: List<RealmStepExam> = emptyList()
    private var listener: OnHomeItemClickListener? = null
    private var isTitleAscending = true
    private var sortType = SurveySortType.DATE_DESC

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    fun updateData(newList: List<RealmStepExam>) {
        val diffResult = DiffUtils.calculateDiff(
            examList,
            newList,
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new -> old == new }
        )
        examList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateDataAfterSearch(newList: List<RealmStepExam>) {
        if (examList.isEmpty()) {
            sortSurveyList(false, newList)
        } else {
            when (sortType) {
                SurveySortType.DATE_DESC -> sortSurveyList(false, newList)
                SurveySortType.DATE_ASC -> sortSurveyList(true, newList)
                SurveySortType.TITLE -> sortSurveyListByName(isTitleAscending, newList)
            }
        }
        notifyDataSetChanged()
    }

    private fun sortSurveyList(isAscend: Boolean, list: List<RealmStepExam> = examList) {
        examList = if (isAscend) {
            list.sortedBy { it.createdDate }
        } else {
            list.sortedByDescending { it.createdDate }
        }
    }

    fun sortByDate(isAscend: Boolean) {
        sortType = if (isAscend) SurveySortType.DATE_ASC else SurveySortType.DATE_DESC
        sortSurveyList(isAscend)
        notifyDataSetChanged()
    }

    private fun sortSurveyListByName(isAscend: Boolean, list: List<RealmStepExam> = examList) {
        examList = if (isAscend) {
            list.sortedBy { it.name?.lowercase() }
        } else {
            list.sortedByDescending { it.name?.lowercase() }
        }
    }

    fun toggleTitleSortOrder() {
        sortType = SurveySortType.TITLE
        isTitleAscending = !isTitleAscending
        sortSurveyListByName(isTitleAscending)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderSurvey {
        val binding = RowSurveyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderSurvey(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderSurvey, position: Int) {
        holder.bind(examList[position])
    }

    override fun getItemCount(): Int = examList.size

    inner class ViewHolderSurvey(private val binding: RowSurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.startSurvey.visibility = View.VISIBLE
            binding.sendSurvey.visibility = View.GONE
            binding.sendSurvey.setOnClickListener {
                val current = examList[bindingAdapterPosition]
                listener?.sendSurvey(current)
            }
        }

        fun bind(exam: RealmStepExam) {
            binding.apply {
                startSurvey.visibility = View.VISIBLE
                tvTitle.text = exam.name
                if (exam.description?.isNotEmpty() == true) {
                    tvDescription.visibility = View.VISIBLE
                    tvDescription.text = exam.description
                }
                startSurvey.setOnClickListener {
                    val isTeamSubmission = mRealm.where(RealmSubmission::class.java)
                        .equalTo("parentId", exam.id).equalTo("membershipDoc.teamId", teamId)
                        .findFirst() != null

                    val shouldAdopt = exam.isTeamShareAllowed && !isTeamSubmission

                    if (shouldAdopt) {
                        surveyAdoptListener.onAdoptRequested(exam, teamId)
                    } else {
                        AdapterMySubmission.openSurvey(listener, exam.id, false, isTeam, teamId)
                    }
                }

                val questions = mRealm.where(RealmExamQuestion::class.java).equalTo("examId", exam.id)
                    .findAll()

                if (questions.isEmpty()) {
                    sendSurvey.visibility = View.GONE
                    startSurvey.visibility = View.GONE
                }

                val isTeamSubmission = mRealm.where(RealmSubmission::class.java)
                    .equalTo("parentId", exam.id).equalTo("membershipDoc.teamId", teamId)
                    .findFirst() != null

                val shouldShowAdopt = exam.isTeamShareAllowed && !isTeamSubmission

                startSurvey.text = when {
                    shouldShowAdopt -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId?.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                tvNoSubmissions.text = when {
                    isTeam -> getNoOfSubmissionByTeam(teamId, exam.id, mRealm)
                    else -> getNoOfSubmissionByUser(exam.id, exam.courseId, userId, mRealm)
                }
                tvDateCompleted.text = getRecentSubmissionDate(exam.id, exam.courseId, userId, mRealm)
                val creationTime = exam.id?.let { RealmStepExam.getSurveyCreationTime(it, mRealm) }
                tvDate.text = creationTime?.let { formatDate(it, "MMM dd, yyyy") } ?: ""
            }
        }

    }
}

enum class SurveySortType {
    DATE_DESC,
    DATE_ASC,
    TITLE
}
