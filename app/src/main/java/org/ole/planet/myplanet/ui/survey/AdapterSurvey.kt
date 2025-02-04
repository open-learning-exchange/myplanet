package org.ole.planet.myplanet.ui.survey

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterSurvey(private val context: Context, private val mRealm: Realm, private val userId: String, private val isTeam: Boolean, val teamId: String?) : RecyclerView.Adapter<AdapterSurvey.ViewHolderSurvey>() {
    private var examList: List<RealmStepExam> = emptyList()
    private var listener: OnHomeItemClickListener? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    fun updateData(newList: List<RealmStepExam>) {
        val diffCallback = SurveyDiffCallback(examList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        examList = newList
        diffResult.dispatchUpdatesTo(this)
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
                tvTitle.text = exam.name
                startSurvey.setOnClickListener {
                    AdapterMySubmission.openSurvey(listener, exam.id, false, isTeam, teamId)
                }

                val questions = mRealm.where(RealmExamQuestion::class.java)
                    .equalTo("examId", exam.id)
                    .findAll()

                if (questions.isEmpty()) {
                    sendSurvey.visibility = View.GONE
                    startSurvey.visibility = View.GONE
                }

                startSurvey.text = when {
                    exam.isTeamShareAllowed -> context.getString(R.string.adopt_survey)
                    exam.isFromNation -> context.getString(R.string.take_survey)
                    else -> context.getString(R.string.record_survey)
                }

                if (userId.startsWith("guest") == true) {
                    startSurvey.visibility = View.GONE
                }

                tvNoSubmissions.text = getNoOfSubmissionByUser(exam.id, exam.courseId, userId, mRealm)
                tvDateCompleted.text = getRecentSubmissionDate(exam.id, exam.courseId, userId, mRealm)
                tvDate.text = formatDate(RealmStepExam.getSurveyCreationTime(exam.id!!, mRealm)!!, "MMM dd, yyyy")
            }
        }
    }
}

class SurveyDiffCallback(private val oldList: List<RealmStepExam>, private val newList: List<RealmStepExam>) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
