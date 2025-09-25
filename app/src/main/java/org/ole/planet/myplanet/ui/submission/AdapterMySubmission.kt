package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import io.realm.internal.SyncObjectServerFacade
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.RowMysurveyBinding
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.exam.TakeExamFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission.ViewHolderMySurvey
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate

class AdapterMySubmission(
    private val context: Context,
    list: List<RealmSubmission>?,
    private val examHashMap: HashMap<String?, RealmStepExam>?
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
    private lateinit var rowMySurveyBinding: RowMysurveyBinding
    private var listener: OnHomeItemClickListener? = null
    private var type = ""
    private var mRealm: Realm? = null

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
        if (list != null && list.isEmpty()) {
            Toast.makeText(
                SyncObjectServerFacade.getApplicationContext(),
                context.getString(R.string.no_items),
                Toast.LENGTH_SHORT
            ).show()
        }
        submitList(list)
    }

    fun setmRealm(mRealm: Realm?) {
        this.mRealm = mRealm
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderMySurvey {
        rowMySurveyBinding = RowMysurveyBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderMySurvey(rowMySurveyBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderMySurvey, position: Int) {
        val submission = getItem(position)
        rowMySurveyBinding.status.text = submission.status
        rowMySurveyBinding.date.text = getFormattedDate(submission.startTime)
        showSubmittedBy(rowMySurveyBinding.submittedBy, submission)
        if (examHashMap?.containsKey(submission.parentId) == true) {
            rowMySurveyBinding.title.text = examHashMap[submission.parentId]?.name
        }
        holder.itemView.setOnClickListener {
            logSubmissionQuestionsAndAnswers(submission)
            if (type == "survey") {
                openSurvey(listener, submission.id, true, false, "")
            } else {
                openSubmissionDetail(listener, submission.id)
            }
        }
    }

    private fun showSubmittedBy(submittedBy: TextView, submission: RealmSubmission) {
        submittedBy.visibility = View.VISIBLE
        try {
            val ob = submission.user?.let { JSONObject(it) }
            if (ob != null) {
                submittedBy.text = ob.optString("name")
            }
        } catch (e: Exception) {
            val user =
                mRealm?.where(RealmUserModel::class.java)?.equalTo("id", submission.userId)?.findFirst()
            if (user != null) {
                submittedBy.text = user.name
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

    fun setType(type: String?) {
        if (type != null) {
            this.type = type
        }
    }

    private fun logSubmissionQuestionsAndAnswers(submission: RealmSubmission) {
        try {
            val submissionTitle = examHashMap?.get(submission.parentId)?.name ?: "Unknown Submission"
            Log.d("SubmissionClick", "=== Submission Clicked ===")
            Log.d("SubmissionClick", "Title: $submissionTitle")
            Log.d("SubmissionClick", "Submission ID: ${submission.id}")
            Log.d("SubmissionClick", "Parent ID: ${submission.parentId}")
            Log.d("SubmissionClick", "Status: ${submission.status}")
            Log.d("SubmissionClick", "Type: ${submission.type}")
            Log.d("SubmissionClick", "mRealm is null: ${mRealm == null}")

            if (mRealm == null) {
                Log.w("SubmissionClick", "mRealm is null, cannot fetch questions and answers")
            } else {
                val realm = mRealm!!

                // Try different exam ID extraction methods
                val examId1 = if (submission.parentId?.contains("@") == true) {
                    submission.parentId!!.split("@")[0]
                } else {
                    submission.parentId
                }
                val examId2 = submission.parentId

                Log.d("SubmissionClick", "Trying exam ID 1: $examId1")
                Log.d("SubmissionClick", "Trying exam ID 2: $examId2")

                // Try to find questions with different exam IDs
                val questions1 = realm.where(RealmExamQuestion::class.java)
                    .equalTo("examId", examId1)
                    .findAll()
                val questions2 = realm.where(RealmExamQuestion::class.java)
                    .equalTo("examId", examId2)
                    .findAll()

                Log.d("SubmissionClick", "Questions found with examId1 ($examId1): ${questions1.size}")
                Log.d("SubmissionClick", "Questions found with examId2 ($examId2): ${questions2.size}")

                val questions = if (questions1.isNotEmpty()) questions1 else questions2

                Log.d("SubmissionClick", "Submission answers count: ${submission.answers?.size ?: 0}")

                if (submission.answers.isNullOrEmpty()) {
                    Log.w("SubmissionClick", "No answers found in submission")
                } else {
                    Log.d("SubmissionClick", "--- Questions and Answers ---")
                    for ((index, answer) in submission.answers!!.withIndex()) {
                        Log.d("SubmissionClick", "Answer $index:")
                        Log.d("SubmissionClick", "  Answer ID: ${answer.id}")
                        Log.d("SubmissionClick", "  Question ID: ${answer.questionId}")
                        Log.d("SubmissionClick", "  Exam ID: ${answer.examId}")
                        Log.d("SubmissionClick", "  Submission ID: ${answer.submissionId}")

                        val question = questions.find { it.id == answer.questionId }
                        if (question != null) {
                            Log.d("SubmissionClick", "  Q: ${question.header ?: question.body}")
                            Log.d("SubmissionClick", "  Question Type: ${question.type}")
                            if (!answer.value.isNullOrEmpty()) {
                                Log.d("SubmissionClick", "  A: ${answer.value}")
                            }
                            if (answer.valueChoices != null && answer.valueChoices!!.isNotEmpty()) {
                                Log.d("SubmissionClick", "  Multiple choice answers: ${answer.valueChoices}")
                            }
                        } else {
                            Log.w("SubmissionClick", "  No question found for answer with questionId: ${answer.questionId}")
                        }
                        Log.d("SubmissionClick", "  ---")
                    }
                }
                Log.d("SubmissionClick", "Total Questions: ${questions.size}")
                Log.d("SubmissionClick", "Total Answers: ${submission.answers?.size ?: 0}")

                // Debug: Show all available question IDs
                if (questions.isNotEmpty()) {
                    Log.d("SubmissionClick", "Available question IDs: ${questions.map { it.id }}")
                }
            }
            Log.d("SubmissionClick", "=== End Submission Data ===")
        } catch (e: Exception) {
            Log.e("SubmissionClick", "Error logging submission data", e)
        }
    }

    class ViewHolderMySurvey(rowMySurveyBinding: RowMysurveyBinding) : RecyclerView.ViewHolder(rowMySurveyBinding.root)

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
