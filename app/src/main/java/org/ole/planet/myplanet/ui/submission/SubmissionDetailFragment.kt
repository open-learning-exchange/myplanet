package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.QuestionAnswerPair
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.utilities.TimeUtils

@AndroidEntryPoint
class SubmissionDetailFragment : Fragment() {
    private var _binding: FragmentSubmissionDetailBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var submissionRepository: SubmissionRepository
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
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch
            loadSubmissionDetails()
        }
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

    private suspend fun loadSubmissionDetails() {
        submissionId?.let {
            val detail = submissionRepository.getSubmissionDetail(it)
            if (detail != null && isAdded) {
                displaySubmissionInfo(detail.submission, detail.exam, detail.user)
                adapter.submitList(detail.questionAnswerPairs)
            }
        }
    }

    private fun displaySubmissionInfo(submission: RealmSubmission, exam: RealmStepExam?, user: RealmUserModel?) {
        binding.tvSubmissionTitle.text = exam?.name ?: "Submission Details"
        binding.tvSubmissionStatus.text = "Status: ${submission.status ?: "Unknown"}"
        binding.tvSubmissionDate.text = "Date: ${TimeUtils.getFormattedDate(submission.startTime)}"
        user?.let {
            binding.tvSubmittedBy.text = "Submitted by: ${it.name}"
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
        _binding = null
        super.onDestroyView()
    }
}
