package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowMysurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMySubmission(
    private val context: Context,
) : ListAdapter<RealmSubmission, AdapterMySubmission.ViewHolderMySurvey>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem ->
            oldItem.id == newItem.id
        },
        areContentsTheSame = { oldItem, newItem ->
            oldItem.id == newItem.id &&
                oldItem.status == newItem.status &&
                oldItem.lastUpdateTime == newItem.lastUpdateTime
        }
    )
) {
    private var examHashMap: HashMap<String?, RealmStepExam> = hashMapOf()
    private var submissionCountMap: Map<String?, Int> = emptyMap()
    private var listener: OnHomeItemClickListener? = null
    private var type = ""

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    fun setExams(exams: HashMap<String?, RealmStepExam>) {
        this.examHashMap = exams
        notifyDataSetChanged()
    }

    fun setSubmissionCounts(counts: Map<String?, Int>) {
        this.submissionCountMap = counts
        notifyDataSetChanged()
    }

    fun setType(type: String?) {
        if (type != null) {
            this.type = type
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMySurvey {
        val binding = RowMysurveyBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMySurvey(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderMySurvey, position: Int) {
        val submission = getItem(position)
        val binding = holder.binding
        binding.status.text = submission.status
        binding.date.text = getFormattedDate(submission.startTime)
        showSubmittedBy(binding, submission)
        if (examHashMap.containsKey(submission.parentId)) {
            binding.title.text = examHashMap[submission.parentId]?.name
        }

        val count = submissionCountMap[submission.id] ?: 1
        if (count > 1) {
            binding.submissionCount.visibility = View.VISIBLE
            binding.submissionCount.text = "($count)"
        } else {
            binding.submissionCount.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (count > 1) {
                showAllSubmissions(submission)
            } else {
                if (type == "survey") {
                    openSurvey(listener, submission.id, true, false, "")
                } else {
                    openSubmissionDetail(listener, submission.id)
                }
            }
        }
    }

    private fun showSubmittedBy(binding: RowMysurveyBinding, submission: RealmSubmission) {
        val finalName = submission.submitterName
        if (finalName.isBlank()) {
            binding.submittedBy.visibility = View.GONE
            binding.submittedBy.text = ""
        } else {
            binding.submittedBy.visibility = View.VISIBLE
            binding.submittedBy.text = finalName
        }
    }

    private fun openSubmissionDetail(listener: OnHomeItemClickListener?, id: String?) {
        if (listener != null) {
            val b = Bundle()
            b.putString("id", id)
            val f: Fragment = SubmissionDetailFragment()
            f.arguments = b
            listener.openCallFragment(f)
        }
    }

    private fun showAllSubmissions(submission: RealmSubmission) {
        val examTitle = examHashMap[submission.parentId]?.name ?: "Submissions"

        val b = Bundle()
        b.putString("parentId", submission.parentId)
        b.putString("examTitle", examTitle)
        b.putString("userId", submission.userId)

        val fragment = SubmissionListFragment()
        fragment.arguments = b

        listener?.openCallFragment(fragment)
    }

    class ViewHolderMySurvey(val binding: RowMysurveyBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        @JvmStatic
        fun openSurvey(listener: OnHomeItemClickListener?, id: String?, isMySurvey: Boolean, isTeam: Boolean, teamId: String?) {
            if (listener != null) {
                val b = Bundle()
                b.putString("type", "survey")
                b.putString("id", id)
                b.putBoolean("isMySurvey", isMySurvey)
                b.putBoolean("isTeam", isTeam)
                b.putString("teamId", teamId)
                val f: Fragment = TakeExamFragment()
                f.arguments = b
                listener.openCallFragment(f)
            }
        }
    }
}
