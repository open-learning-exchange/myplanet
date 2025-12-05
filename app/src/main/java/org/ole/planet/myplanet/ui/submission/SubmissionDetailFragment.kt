package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.json.JSONObject
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding
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

        // Use a LinearLayoutManager that forces full height calculation
        val layoutManager = object : LinearLayoutManager(context) {
            override fun canScrollVertically(): Boolean {
                return false
            }

            override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State, widthSpec: Int, heightSpec: Int) {
                // Use state.itemCount for more reliable count
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

        adapter.submitList(questionAnswerPairs)
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
