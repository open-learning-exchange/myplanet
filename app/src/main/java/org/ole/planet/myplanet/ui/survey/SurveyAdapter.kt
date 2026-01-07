package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnSurveyAdoptListener
import org.ole.planet.myplanet.databinding.RowSurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.ui.survey.SurveyFormState

class SurveyAdapter(
    private val context: Context,
    private val userId: String?,
    private val isTeam: Boolean,
    val teamId: String?,
    private val onAdoptSurveyListener: OnSurveyAdoptListener,
    private val surveyInfoMap: Map<String, SurveyInfo>,
    private val bindingDataMap: Map<String, SurveyFormState>
) : ListAdapter<RealmStepExam, SurveyAdapter.ViewHolderSurvey>(SurveyDiffCallback()) {
    private var listener: OnHomeItemClickListener? = null
    private var isTitleAscending = true
    private var sortStrategy: (List<RealmStepExam>) -> List<RealmStepExam> = { list ->
        sortSurveyList(false, list)
    }

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    private fun sortSurveyList(
        isAscend: Boolean,
        list: List<RealmStepExam>
    ): List<RealmStepExam> {
        return if (isAscend) {
            list.sortedBy { it.createdDate }
        } else {
            list.sortedByDescending { it.createdDate }
        }
    }

    fun sortByDate(isAscend: Boolean) {
        sortStrategy = { list -> sortSurveyList(isAscend, list) }
        val sortedList = sortStrategy(currentList)
        submitList(sortedList)
    }

    private fun sortSurveyListByName(
        isAscend: Boolean,
        list: List<RealmStepExam>
    ): List<RealmStepExam> {
        return if (isAscend) {
            list.sortedBy { it.name?.lowercase() }
        } else {
            list.sortedByDescending { it.name?.lowercase() }
        }
    }

    fun toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending
        sortStrategy = { list -> sortSurveyListByName(isTitleAscending, list) }
        val sortedList = sortStrategy(currentList)
        submitList(sortedList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderSurvey {
        val binding = RowSurveyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderSurvey(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderSurvey, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolderSurvey(private val binding: RowSurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.startSurvey.visibility = View.VISIBLE
            binding.sendSurvey.visibility = View.GONE
            binding.sendSurvey.setOnClickListener {
                val current = getItem(bindingAdapterPosition)
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

                val bindingData = bindingDataMap[exam.id]
                val teamSubmission = bindingData?.teamSubmission
                val questionCount = bindingData?.questionCount ?: 0

                startSurvey.setOnClickListener {
                    val shouldAdopt = exam.isTeamShareAllowed && teamSubmission?.isValid != true
                    if (shouldAdopt) {
                        onAdoptSurveyListener.onAdoptSurvey(exam.id.orEmpty())
                    } else {
                        SubmissionsAdapter.openSurvey(listener, exam.id, false, isTeam, teamId)
                    }
                }

                if (questionCount == 0) {
                    sendSurvey.visibility = View.GONE
                    startSurvey.visibility = View.GONE
                }

                val shouldShowAdopt = exam.isTeamShareAllowed && teamSubmission?.isValid != true

                startSurvey.text = when {
                    shouldShowAdopt -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId?.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                val surveyInfo = surveyInfoMap[exam.id]
                tvNoSubmissions.text = surveyInfo?.submissionCount ?: ""
                tvDateCompleted.text = surveyInfo?.lastSubmissionDate ?: ""
                tvDate.text = surveyInfo?.creationDate ?: ""
            }
        }
    }
}

class SurveyDiffCallback : DiffUtil.ItemCallback<RealmStepExam>() {
    override fun areItemsTheSame(oldItem: RealmStepExam, newItem: RealmStepExam): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: RealmStepExam, newItem: RealmStepExam): Boolean {
        return oldItem == newItem
    }
}
