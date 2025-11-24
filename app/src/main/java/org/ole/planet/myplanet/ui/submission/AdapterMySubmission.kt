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
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowMysurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission.ViewHolderMySurvey
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMySubmission(
    private val context: Context,
    list: List<RealmSubmission>?,
    private val examHashMap: HashMap<String?, RealmStepExam>?,
    private val submissionCountMap: Map<String?, Int> = emptyMap(),
    private val nameResolver: (String?) -> String?,
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
        showSubmittedBy(binding, submission)
        if (examHashMap?.containsKey(submission.parentId) == true) {
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
        val embeddedName = runCatching {
            submission.user?.takeIf { it.isNotBlank() }?.let { userJson ->
                JSONObject(userJson).optString("name").takeIf { name -> name.isNotBlank() }
            }
        }.getOrNull()

        val resolvedName = embeddedName ?: nameResolver(submission.userId)

        if (resolvedName.isNullOrBlank()) {
            binding.submittedBy.visibility = View.GONE
            binding.submittedBy.text = ""
        } else {
            binding.submittedBy.visibility = View.VISIBLE
            binding.submittedBy.text = resolvedName
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

    fun setType(type: String?) {
        if (type != null) {
            this.type = type
        }
    }

    private fun showAllSubmissions(submission: RealmSubmission) {
        val examTitle = examHashMap?.get(submission.parentId)?.name ?: "Submissions"

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
