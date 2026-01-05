package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.realm.Realm
import java.util.UUID
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.SurveyAdoptListener
import org.ole.planet.myplanet.databinding.RowSurveyBinding
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.ui.survey.SurveyFormState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.SurveysRepository

class SurveyAdapter(
    private val context: Context,
    private val surveysRepository: SurveysRepository,
    private val userId: String?,
    private val isTeam: Boolean,
    val teamId: String?,
    private val surveyAdoptListener: SurveyAdoptListener,
    private val settings: SharedPreferences,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val surveyInfoMap: Map<String, SurveyInfo>,
    private val bindingDataMap: Map<String, SurveyFormState>,
    private val coroutineScope: CoroutineScope
) : ListAdapter<RealmStepExam, SurveyAdapter.ViewHolderSurvey>(SurveyDiffCallback()) {
    private var listener: OnHomeItemClickListener? = null
    private val adoptedSurveyIds = mutableSetOf<String>()
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
                        adoptSurvey(exam, teamId)
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

        private fun adoptSurvey(exam: RealmStepExam, teamId: String?) {
            coroutineScope.launch {
                val success = withContext(Dispatchers.IO) {
                    surveysRepository.adoptSurvey(exam.id!!, teamId, isTeam)
                }
                if (success) {
                    adoptedSurveyIds.add(exam.id!!)
                    val position = currentList.indexOfFirst { it.id == exam.id }
                    if (position != -1) {
                        notifyItemChanged(position)
                    }
                    Snackbar.make(binding.root, context.getString(R.string.survey_adopted_successfully), Snackbar.LENGTH_LONG).show()
                    surveyAdoptListener.onSurveyAdopted()
                } else {
                    Snackbar.make(binding.root, context.getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
                }
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
