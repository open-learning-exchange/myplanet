package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var submissionId: String? = null
    private lateinit var adapter: QuestionAnswerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubmissionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        submissionId = arguments?.getString("id")
        setupRecyclerView()
        loadSubmissionDetails()
    }

    private fun setupRecyclerView() {
        adapter = QuestionAnswerAdapter()

        val layoutManager = object : LinearLayoutManager(context) {
            override fun canScrollVertically(): Boolean {
                return false
            }

            override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State, widthSpec: Int, heightSpec: Int) {
                val count = state.itemCount
                if (count == 0 || adapter.itemCount == 0) {
                    super.onMeasure(recycler, state, widthSpec, heightSpec)
                    return
                }

                var totalHeight = 0
                try {
                    for (i in 0 until count) {
                        if (i >= adapter.itemCount) {
                            super.onMeasure(recycler, state, widthSpec, heightSpec)
                            return
                        }

                        val view = recycler.getViewForPosition(i)
                        addView(view)
                        measureChild(view, 0, 0)
                        totalHeight += getDecoratedMeasuredHeight(view)
                        removeAndRecycleView(view, recycler)
                    }

                    val width = View.MeasureSpec.getSize(widthSpec)
                    setMeasuredDimension(width, totalHeight)
                } catch (e: Exception) {
                    super.onMeasure(recycler, state, widthSpec, heightSpec)
                }
            }
        }

        binding.rvQuestionsAnswers.layoutManager = layoutManager
        binding.rvQuestionsAnswers.adapter = adapter
        binding.rvQuestionsAnswers.setHasFixedSize(false)
        binding.rvQuestionsAnswers.isNestedScrollingEnabled = false
    }

    private fun loadSubmissionDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (submissionInfo, questionAnswerList) = withContext(Dispatchers.IO) {
                Realm.getDefaultInstance().use { realm ->
                    val submission = submissionId?.let { id ->
                        realm.where(RealmSubmission::class.java)
                            .equalTo("id", id)
                            .or()
                            .equalTo("_id", id)
                            .findFirst()
                            ?: realm.where(RealmSubmission::class.java)
                                .contains("parentId", id)
                                .findFirst()
                    }

                    if (submission != null) {
                        val unmanagedSubmission = realm.copyFromRealm(submission)

                        var examId = unmanagedSubmission.parentId
                        if (unmanagedSubmission.parentId?.contains("@") == true) {
                            examId = unmanagedSubmission.parentId!!.split("@")[0]
                        }

                        val exam = realm.where(RealmStepExam::class.java)
                            .equalTo("id", examId)
                            .findFirst()
                        val unmanagedExam = exam?.let { realm.copyFromRealm(it) }

                        val submittedBy = try {
                            val userJson = unmanagedSubmission.user?.let { JSONObject(it) }
                            userJson?.optString("name") ?: ""
                        } catch (e: Exception) {
                            val user = realm.where(RealmUserModel::class.java)
                                .equalTo("id", unmanagedSubmission.userId)
                                .findFirst()
                            user?.let { realm.copyFromRealm(it) }?.name ?: ""
                        }

                        val info = SubmissionInfo(
                            title = unmanagedExam?.name ?: "Submission Details",
                            status = "Status: ${unmanagedSubmission.status ?: "Unknown"}",
                            date = "Date: ${TimeUtils.getFormattedDate(unmanagedSubmission.startTime)}",
                            submittedBy = "Submitted by: $submittedBy"
                        )

                        val questions = realm.where(RealmExamQuestion::class.java)
                            .equalTo("examId", getExamId(unmanagedSubmission.parentId))
                            .findAll()
                        val unmanagedQuestions = realm.copyFromRealm(questions)

                        val qnaList = unmanagedQuestions.map { question ->
                            val answer = unmanagedSubmission.answers?.find { it.questionId == question.id }
                            QuestionAnswerInfo(
                                questionHeader = question.header,
                                questionBody = question.body,
                                answer = answer?.let {
                                    AnswerInfo(
                                        value = formatAnswer(it, question),
                                        isPassed = it.isPassed
                                    )
                                },
                                type = question.type
                            )
                        }
                        Pair(info, qnaList)
                    } else {
                        Pair(null, emptyList())
                    }
                }
            }

            submissionInfo?.let {
                binding.tvSubmissionTitle.text = it.title
                binding.tvSubmissionStatus.text = it.status
                binding.tvSubmissionDate.text = it.date
                binding.tvSubmittedBy.text = it.submittedBy
            }
            adapter.updateData(questionAnswerList)
        }
    }

    private fun formatAnswer(answer: RealmAnswer?, question: RealmExamQuestion): String {
        if (answer == null) {
            return "No answer provided"
        }

        return when {
            !answer.value.isNullOrEmpty() -> {
                answer.value!!
            }
            answer.valueChoices != null && answer.valueChoices!!.isNotEmpty() -> {
                formatMultipleChoiceAnswer(answer.valueChoices!!, question)
            }
            else -> "No answer provided"
        }
    }

    private fun formatMultipleChoiceAnswer(choices: List<String>, question: RealmExamQuestion): String {
        val selectedChoices = mutableListOf<String>()

        try {
            val questionChoicesJson = if (!question.choices.isNullOrEmpty()) {
                Gson().fromJson(question.choices, JsonArray::class.java)
            } else {
                JsonArray()
            }

            for (choice in choices) {
                try {
                    val choiceJson = Gson().fromJson(choice, JsonObject::class.java)
                    val choiceId = choiceJson.get("id")?.asString
                    val choiceText = choiceJson.get("text")?.asString

                    if (!choiceText.isNullOrEmpty()) {
                        selectedChoices.add(choiceText)
                    } else if (!choiceId.isNullOrEmpty()) {
                        val matchingChoice = findChoiceTextById(choiceId, questionChoicesJson)
                        if (matchingChoice != null) {
                            selectedChoices.add(matchingChoice)
                        } else {
                            selectedChoices.add(choiceId)
                        }
                    }
                } catch (e: Exception) {
                    selectedChoices.add(choice)
                }
            }
        } catch (e: Exception) {
            return choices.joinToString(", ")
        }

        return if (selectedChoices.isNotEmpty()) {
            selectedChoices.joinToString(", ")
        } else {
            "No selection made"
        }
    }

    private fun findChoiceTextById(choiceId: String, questionChoices: JsonArray): String? {
        for (i in 0 until questionChoices.size()) {
            try {
                val choice = questionChoices[i].asJsonObject
                if (choice.get("id")?.asString == choiceId) {
                    return choice.get("text")?.asString
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }


    private fun getExamId(parentId: String?): String? {
        return if (parentId?.contains("@") == true) {
            parentId.split("@")[0]
        } else {
            parentId
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
