package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowMysurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission.ViewHolderMySurvey
import org.ole.planet.myplanet.utilities.DiffUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMySubmission(
    private val context: Context,
    list: List<RealmSubmission>?,
    private val examHashMap: HashMap<String?, RealmStepExam>?,
    private val nameResolver: (String?) -> String?,
    private val lifecycleScope: CoroutineScope,
) : ListAdapter<RealmSubmission, ViewHolderMySurvey>(
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
    private var listener: OnHomeItemClickListener? = null
    private var type = ""

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        if (list != null && list.isEmpty()) {
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.no_items),
                Toast.LENGTH_SHORT
            ).show()
        }
        submitList(list)
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
        showSubmittedBy(holder, binding, submission)
        if (examHashMap?.containsKey(submission.parentId) == true) {
            binding.title.text = examHashMap[submission.parentId]?.name
        }
        holder.itemView.setOnClickListener {
            logSubmissionResponses(submission)
            if (type == "survey") {
                openSurvey(listener, submission.id, true, false, "")
            } else {
                openSubmissionDetail(listener, submission.id)
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolderMySurvey) {
        super.onViewRecycled(holder)
        holder.job?.cancel()
    }

    private fun showSubmittedBy(holder: ViewHolderMySurvey, binding: RowMysurveyBinding, submission: RealmSubmission) {
        holder.job?.cancel()
        holder.job = lifecycleScope.launch {
            val resolvedName = withContext(Dispatchers.IO) {
                runCatching {
                    submission.user?.takeIf { it.isNotBlank() }?.let { userJson ->
                        JSONObject(userJson).optString("name").takeIf { name -> name.isNotBlank() }
                    }
                }.getOrNull() ?: nameResolver(submission.userId)
            }

            withContext(Dispatchers.Main) {
                if (resolvedName.isNullOrBlank()) {
                    binding.submittedBy.visibility = View.GONE
                    binding.submittedBy.text = ""
                } else {
                    binding.submittedBy.visibility = View.VISIBLE
                    binding.submittedBy.text = resolvedName
                }
            }
        }
    }

    private fun logSubmissionResponses(submission: RealmSubmission) {
        val submissionTitle = examHashMap?.get(submission.parentId)?.name ?: "Unknown"
        val answerCount = submission.answers?.size ?: 0

        Log.d("SubmissionResponses", "=== Submission Clicked ===")
        Log.d("SubmissionResponses", "Title: $submissionTitle")
        Log.d("SubmissionResponses", "Submission ID: ${submission.id}")
        Log.d("SubmissionResponses", "Status: ${submission.status}")
        Log.d("SubmissionResponses", "Total Answers: $answerCount")
        Log.d("SubmissionResponses", "")

        submission.answers?.forEachIndexed { index, answer ->
            Log.d("SubmissionResponses", "Answer ${index + 1}:")
            Log.d("SubmissionResponses", "  Question ID: ${answer.questionId}")
            Log.d("SubmissionResponses", "  Value: ${answer.value}")
            Log.d("SubmissionResponses", "  Value Choices: ${answer.valueChoices?.joinToString(", ")}")
            Log.d("SubmissionResponses", "  Passed: ${answer.isPassed}")
            Log.d("SubmissionResponses", "  Mistakes: ${answer.mistakes}")
            Log.d("SubmissionResponses", "")
        }

        Log.d("SubmissionResponses", "=== End of Submission ===")
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

    fun setType(type: String?) {
        if (type != null) {
            this.type = type
        }
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
