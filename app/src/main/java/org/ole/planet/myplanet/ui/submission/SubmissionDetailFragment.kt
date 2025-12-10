package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding

@AndroidEntryPoint
class SubmissionDetailFragment : Fragment() {
    private var _binding: FragmentSubmissionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmissionDetailViewModel by viewModels()
    private lateinit var adapter: QuestionAnswerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubmissionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.questionAnswers.collect { questionAnswers ->
                adapter.submitList(questionAnswers)
            }
        }
        lifecycleScope.launch {
            viewModel.title.collect { title ->
                binding.tvSubmissionTitle.text = title
            }
        }
        lifecycleScope.launch {
            viewModel.status.collect { status ->
                binding.tvSubmissionStatus.text = status
            }
        }
        lifecycleScope.launch {
            viewModel.date.collect { date ->
                binding.tvSubmissionDate.text = date
            }
        }
        lifecycleScope.launch {
            viewModel.submittedBy.collect { submittedBy ->
                binding.tvSubmittedBy.text = submittedBy
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
