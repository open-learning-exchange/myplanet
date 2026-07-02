package org.ole.planet.myplanet.ui.submissions

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowMysurveyBinding
import org.ole.planet.myplanet.ui.exam.ExamTakingFragment
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDate

class SubmissionsAdapter(
    private val context: Context,
) : ListAdapter<SubmissionUiModel, SubmissionsAdapter.SubmissionsViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem ->
            oldItem.id == newItem.id
        },
        areContentsTheSame = { oldItem, newItem ->
            oldItem == newItem
        }
    )
) {
    private var listener: OnHomeItemClickListener? = null
    private var type = ""

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    fun setType(type: String?) {
        if (type != null) {
            this.type = type
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubmissionsViewHolder {
        val binding = RowMysurveyBinding.inflate(LayoutInflater.from(context), parent, false)
        return SubmissionsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubmissionsViewHolder, position: Int) {
        holder.bind(getItem(position))
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

    private fun showAllSubmissions(submission: SubmissionUiModel) {
        val b = Bundle()
        b.putString("parentId", submission.parentId)
        b.putString("examTitle", submission.examTitle)
        b.putString("userId", submission.userId)

        val fragment = SubmissionListFragment()
        fragment.arguments = b

        listener?.openCallFragment(fragment)
    }

    inner class SubmissionsViewHolder(val binding: RowMysurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(submission: SubmissionUiModel) {
            binding.status.text = submission.status
            binding.date.text = getFormattedDate(submission.startTime)
            showSubmittedBy(submission)
            binding.title.text = submission.examTitle
            updateSubmissionCount(submission)
        }

        fun updateSubmissionCount(submission: SubmissionUiModel) {
            val count = submission.submissionCount
            if (count > 1) {
                binding.submissionCount.visibility = View.VISIBLE
                binding.submissionCount.text = "($count)"
            } else {
                binding.submissionCount.visibility = View.GONE
            }

            itemView.setOnClickListener {
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

        private fun showSubmittedBy(submission: SubmissionUiModel) {
            val finalName = submission.submitterName
            if (finalName.isBlank()) {
                binding.submittedBy.visibility = View.GONE
                binding.submittedBy.text = ""
            } else {
                binding.submittedBy.visibility = View.VISIBLE
                binding.submittedBy.text = finalName
            }
        }
    }

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
                val f: Fragment = ExamTakingFragment()
                f.arguments = b
                listener.openCallFragment(f)
            }
        }
    }
}
