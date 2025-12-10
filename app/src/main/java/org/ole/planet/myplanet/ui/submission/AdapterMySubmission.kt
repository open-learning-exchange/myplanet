package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowMysurveyBinding
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMySubmission(
    private val context: Context,
    private val lifecycleScope: CoroutineScope,
) : ListAdapter<SubmissionItem, AdapterMySubmission.ViewHolderMySurvey>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem ->
            oldItem.submission.id == newItem.submission.id
        },
        areContentsTheSame = { oldItem, newItem ->
            oldItem.submission.id == newItem.submission.id &&
                oldItem.submission.status == newItem.submission.status &&
                oldItem.submission.lastUpdateTime == newItem.submission.lastUpdateTime &&
                oldItem.examName == newItem.examName &&
                oldItem.submissionCount == newItem.submissionCount &&
                oldItem.userName == newItem.userName
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMySurvey {
        val binding = RowMysurveyBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMySurvey(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderMySurvey, position: Int) {
        val submissionItem = getItem(position)
        val submission = submissionItem.submission
        val binding = holder.binding
        binding.status.text = submission.status
        binding.date.text = getFormattedDate(submission.lastUpdateTime)
        showSubmittedBy(holder, binding, submission, submissionItem.userName)
        binding.title.text = submissionItem.examName

        val count = submissionItem.submissionCount
        if (count > 1) {
            binding.submissionCount.visibility = View.VISIBLE
            binding.submissionCount.text = "($count)"
        } else {
            binding.submissionCount.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (count > 1) {
                showAllSubmissions(submission, submissionItem.examName)
            } else {
                if (type == "survey") {
                    openSurvey(listener, submission.id, true, false, "")
                } else {
                    openSubmissionDetail(listener, submission.id)
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolderMySurvey) {
        super.onViewRecycled(holder)
        holder.job?.cancel()
    }

    private fun showSubmittedBy(
        holder: ViewHolderMySurvey,
        binding: RowMysurveyBinding,
        submission: RealmSubmission,
        userName: String?
    ) {
        holder.job?.cancel()
        holder.job = lifecycleScope.launch {
            val resolvedName = withContext(Dispatchers.Default) {
                runCatching {
                    submission.user?.takeIf { it.isNotBlank() }?.let { userJson ->
                        org.json.JSONObject(userJson).optString("name").takeIf { name -> name.isNotBlank() }
                    }
                }.getOrNull()
            }

            val finalName = resolvedName ?: userName

            withContext(Dispatchers.Main) {
                if (finalName.isNullOrBlank()) {
                    binding.submittedBy.visibility = View.GONE
                    binding.submittedBy.text = ""
                } else {
                    binding.submittedBy.visibility = View.VISIBLE
                    binding.submittedBy.text = finalName
                }
            }
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

    private fun showAllSubmissions(submission: RealmSubmission, examName: String?) {
        val b = Bundle()
        b.putString("parentId", submission.parentId)
        b.putString("examTitle", examName ?: "Submissions")
        b.putString("userId", submission.userId)

        val fragment = SubmissionListFragment()
        fragment.arguments = b

        listener?.openCallFragment(fragment)
    }

    class ViewHolderMySurvey(val binding: RowMysurveyBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
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
                val f: Fragment = TakeExamFragment()
                f.arguments = b
                listener.openCallFragment(f)
            }
        }
    }
}
