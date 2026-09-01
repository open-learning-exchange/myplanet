package org.ole.planet.myplanet.ui.submissions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding
import org.ole.planet.myplanet.utils.collectWhenStarted

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

        val layoutManager = object : LinearLayoutManager(context) {
            override fun canScrollVertically(): Boolean {
                return false
            }
        }

        binding.rvQuestionsAnswers.layoutManager = layoutManager
        binding.rvQuestionsAnswers.adapter = adapter
        binding.rvQuestionsAnswers.setHasFixedSize(false)
        binding.rvQuestionsAnswers.isNestedScrollingEnabled = false
    }

    private fun observeViewModel() {
        collectWhenStarted(viewModel.questionAnswers) { questionAnswers ->
            adapter.submitList(questionAnswers)
        }
        collectWhenStarted(viewModel.title) { title ->
            binding.tvSubmissionTitle.text = title
        }
        collectWhenStarted(viewModel.status) { status ->
            binding.tvSubmissionStatus.text = status
        }
        collectWhenStarted(viewModel.date) { date ->
            binding.tvSubmissionDate.text = date
        }
        collectWhenStarted(viewModel.submittedBy) { submittedBy ->
            binding.tvSubmittedBy.text = submittedBy
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
