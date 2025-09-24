package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import org.json.JSONObject
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.TimeUtils

class SubmissionDetailFragment : Fragment() {
    private var _binding: FragmentSubmissionDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var mRealm: Realm
    private var submissionId: String? = null
    private lateinit var adapter: QuestionAnswerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubmissionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mRealm = Realm.getDefaultInstance()
        submissionId = arguments?.getString("id")

        setupRecyclerView()
        loadSubmissionDetails()
    }

    private fun setupRecyclerView() {
        adapter = QuestionAnswerAdapter()
        binding.rvQuestionsAnswers.layoutManager = LinearLayoutManager(context)
        binding.rvQuestionsAnswers.adapter = adapter
    }

    private fun loadSubmissionDetails() {
        submissionId?.let { id ->
            var submission = mRealm.where(RealmSubmission::class.java)
                .equalTo("id", id)
                .or()
                .equalTo("_id", id)
                .findFirst()

            if (submission == null) {
                submission = mRealm.where(RealmSubmission::class.java)
                    .contains("parentId", id)
                    .findFirst()
            }

            submission?.let {
                displaySubmissionInfo(it)
                loadQuestionsAndAnswers(it)
            }
        }
    }

    private fun displaySubmissionInfo(submission: RealmSubmission) {
        var examId = submission.parentId
        if (submission.parentId?.contains("@") == true) {
            examId = submission.parentId!!.split("@")[0]
        }

        val exam = mRealm.where(RealmStepExam::class.java)
            .equalTo("id", examId)
            .findFirst()

        binding.tvSubmissionTitle.text = exam?.name ?: "Submission Details"
        binding.tvSubmissionStatus.text = "Status: ${submission.status ?: "Unknown"}"
        binding.tvSubmissionDate.text = "Date: ${TimeUtils.getFormattedDate(submission.startTime)}"

        showSubmittedBy(submission)
    }

    private fun showSubmittedBy(submission: RealmSubmission) {
        try {
            val userJson = submission.user?.let { JSONObject(it) }
            if (userJson != null) {
                binding.tvSubmittedBy.text = "Submitted by: ${userJson.optString("name")}"
            }
        } catch (e: Exception) {
            val user = mRealm.where(RealmUserModel::class.java)
                .equalTo("id", submission.userId)
                .findFirst()
            if (user != null) {
                binding.tvSubmittedBy.text = "Submitted by: ${user.name}"
            }
        }
    }

    private fun loadQuestionsAndAnswers(submission: RealmSubmission) {
        val examId = getExamId(submission.parentId)

        val questions = mRealm.where(RealmExamQuestion::class.java)
            .equalTo("examId", examId)
            .findAll()

        val questionAnswerPairs = questions.map { question ->
            val answer = submission.answers?.find { it.questionId == question.id }
            QuestionAnswerPair(question, answer)
        }

        adapter.updateData(questionAnswerPairs)
    }

    private fun logDetailedAnswers(submission: RealmSubmission, questionAnswerPairs: List<QuestionAnswerPair>) {
        Log.d("SubmissionAnswers", "=== SUBMISSION ANSWERS LOG ===")
        Log.d("SubmissionAnswers", "Submission ID: ${submission.id}")
        Log.d("SubmissionAnswers", "Submission _ID: ${submission._id}")
        Log.d("SubmissionAnswers", "Submission ParentID: ${submission.parentId}")
        Log.d("SubmissionAnswers", "Status: ${submission.status}")
        Log.d("SubmissionAnswers", "Total Questions: ${questionAnswerPairs.size}")
        Log.d("SubmissionAnswers", "Answered Questions: ${questionAnswerPairs.count { it.answer != null }}")
        Log.d("SubmissionAnswers", "")

        debugRealmAnswerTable(submission)

        Log.d("SubmissionAnswers", "=== SUBMISSION ANSWERS COLLECTION DEBUG ===")
        if (submission.answers == null) {
            Log.d("SubmissionAnswers", "submission.answers is NULL")
        } else if (submission.answers!!.isEmpty()) {
            Log.d("SubmissionAnswers", "submission.answers is EMPTY (size: ${submission.answers!!.size})")
        } else {
            Log.d("SubmissionAnswers", "submission.answers has ${submission.answers!!.size} items:")
            submission.answers!!.forEachIndexed { index, answer ->
                Log.d("SubmissionAnswers", "  Answer ${index + 1}: id=${answer.id}, questionId=${answer.questionId}, submissionId=${answer.submissionId}")
            }
        }
        Log.d("SubmissionAnswers", "")

        questionAnswerPairs.forEachIndexed { index, pair ->
            val question = pair.question
            val answer = pair.answer

            Log.d("SubmissionAnswers", "--- Question ${index + 1} ---")
            Log.d("SubmissionAnswers", "Question ID: ${question.id}")
            Log.d("SubmissionAnswers", "Question Type: ${question.type}")
            Log.d("SubmissionAnswers", "Question Header: ${question.header ?: "No header"}")
            Log.d("SubmissionAnswers", "Question Body: ${question.body?.take(100) ?: "No body"}${if ((question.body?.length ?: 0) > 100) "..." else ""}")

            if (answer != null) {
                Log.d("SubmissionAnswers", "Answer ID: ${answer.id}")
                Log.d("SubmissionAnswers", "Answer Value: ${answer.value ?: "null"}")

                if (answer.valueChoices != null && answer.valueChoices!!.isNotEmpty()) {
                    Log.d("SubmissionAnswers", "Answer Choices Count: ${answer.valueChoices!!.size}")
                    answer.valueChoices!!.forEachIndexed { choiceIndex, choice ->
                        Log.d("SubmissionAnswers", "  Choice ${choiceIndex + 1}: $choice")
                    }
                }

                Log.d("SubmissionAnswers", "Answer Grade: ${answer.grade}")
                Log.d("SubmissionAnswers", "Answer Passed: ${answer.isPassed}")
                Log.d("SubmissionAnswers", "Answer Mistakes: ${answer.mistakes}")

                val displayText = formatAnswerForDisplay(question, answer)
                Log.d("SubmissionAnswers", "Formatted Answer: $displayText")
            } else {
                Log.d("SubmissionAnswers", "Answer: NO ANSWER PROVIDED")
            }

            Log.d("SubmissionAnswers", "")
        }

        Log.d("SubmissionAnswers", "=== END SUBMISSION ANSWERS LOG ===")
    }

    private fun debugRealmAnswerTable(submission: RealmSubmission) {
        Log.d("SubmissionAnswers", "=== REALM ANSWER TABLE DEBUG ===")
        val allAnswers = mRealm.where(RealmAnswer::class.java).findAll()
        Log.d("SubmissionAnswers", "Total answers in RealmAnswer table: ${allAnswers.size}")

        if (allAnswers.isNotEmpty()) {
            Log.d("SubmissionAnswers", "All answers in table:")
            allAnswers.forEachIndexed { index, answer ->
                Log.d("SubmissionAnswers", "  Answer ${index + 1}: id=${answer.id}, questionId=${answer.questionId}, submissionId=${answer.submissionId}, examId=${answer.examId}, value=${answer.value ?: "null"}")
            }
        }

        val answersBySubmissionId = mRealm.where(RealmAnswer::class.java)
            .equalTo("submissionId", submission.id)
            .findAll()
        Log.d("SubmissionAnswers", "Answers with submissionId='${submission.id}': ${answersBySubmissionId.size}")
        answersBySubmissionId.forEachIndexed { index, answer ->
            Log.d("SubmissionAnswers", "  Match ${index + 1}: id=${answer.id}, value=${answer.value}, choices=${answer.valueChoices?.size ?: 0}")
        }

        val answersBySubmission_Id = mRealm.where(RealmAnswer::class.java)
            .equalTo("submissionId", submission._id)
            .findAll()
        Log.d("SubmissionAnswers", "Answers with submissionId='${submission._id}': ${answersBySubmission_Id.size}")

        val examId = getExamId(submission.parentId)
        val answersByExamId = mRealm.where(RealmAnswer::class.java)
            .equalTo("examId", examId)
            .findAll()
        Log.d("SubmissionAnswers", "Answers with examId='$examId': ${answersByExamId.size}")

        Log.d("SubmissionAnswers", "")
    }

    private fun formatAnswerForDisplay(question: RealmExamQuestion, answer: RealmAnswer?): String {
        return when {
            answer == null -> "No answer provided"
            question.type == "selectMultiple" -> {
                if (answer.valueChoices != null && answer.valueChoices!!.isNotEmpty()) {
                    answer.valueChoices!!.joinToString(", ") { choice ->
                        try {
                            val choiceObj = org.json.JSONObject(choice)
                            choiceObj.optString("text", choice)
                        } catch (e: Exception) {
                            choice
                        }
                    }
                } else {
                    "No selections made"
                }
            }
            !answer.value.isNullOrEmpty() -> answer.value!!
            else -> "No answer"
        }
    }

    private fun getExamId(parentId: String?): String? {
        return if (parentId?.contains("@") == true) {
            parentId.split("@")[0]
        } else {
            parentId
        }
    }


    override fun onDestroyView() {
        mRealm.close()
        _binding = null
        super.onDestroyView()
    }
}
