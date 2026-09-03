package org.ole.planet.myplanet.ui.surveys

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnSurveyAdoptListener
import org.ole.planet.myplanet.databinding.RowSurveyBinding
import org.ole.planet.myplanet.model.SurveyRow
import org.ole.planet.myplanet.ui.submissions.SubmissionsAdapter
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.StableIdGenerator

class SurveysAdapter(
    private val context: Context,
    private val userId: String?,
    private val isTeam: Boolean,
    val teamId: String?,
    private val onAdoptSurveyListener: OnSurveyAdoptListener
) : ListAdapter<SurveyRow, SurveysAdapter.SurveysViewHolder>(DiffUtils.itemCallback(
    { oldItem, newItem -> oldItem.exam.id == newItem.exam.id },
    { oldItem, newItem ->
        oldItem.exam.name == newItem.exam.name &&
                oldItem.exam.description == newItem.exam.description &&
                oldItem.exam.isTeamShareAllowed == newItem.exam.isTeamShareAllowed &&
                oldItem.exam.isFromNation == newItem.exam.isFromNation &&
                oldItem.surveyInfo == newItem.surveyInfo &&
                oldItem.formState == newItem.formState
    }
)) {
    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        val id = StableIdGenerator.generateStringId(item.exam.id)
        return if (id != RecyclerView.NO_ID) id else StableIdGenerator.generateFallbackId(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveysViewHolder {
        val binding = RowSurveyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SurveysViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SurveysViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SurveysViewHolder(private val binding: RowSurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.startSurvey.visibility = View.VISIBLE
            binding.sendSurvey.visibility = View.GONE
            binding.sendSurvey.setOnClickListener {
                val current = getItem(bindingAdapterPosition)
                listener?.sendSurvey(current.exam)
            }
        }

        fun bind(row: SurveyRow) {
            val exam = row.exam
            binding.apply {
                startSurvey.visibility = View.VISIBLE
                tvTitle.text = exam.name
                if (exam.description?.isNotEmpty() == true) {
                    tvDescription.visibility = View.VISIBLE
                    tvDescription.text = exam.description
                }

                val bindingData = row.formState
                val teamSubmission = bindingData?.teamSubmission
                val questionCount = bindingData?.questionCount ?: 0

                startSurvey.setOnClickListener {
                    val shouldAdopt = exam.isTeamShareAllowed && teamSubmission == null
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

                val shouldShowAdopt = exam.isTeamShareAllowed && teamSubmission == null

                startSurvey.text = when {
                    shouldShowAdopt -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId?.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                val surveyInfo = row.surveyInfo
                tvNoSubmissions.text = surveyInfo?.submissionCount ?: ""
                tvDateCompleted.text = surveyInfo?.lastSubmissionDate ?: ""
                tvDate.text = surveyInfo?.creationDate ?: ""
            }
        }
    }
}
