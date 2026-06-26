package org.ole.planet.myplanet.ui.submissions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentSubmissionListBinding
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class SubmissionListFragment : Fragment() {
    private var _binding: FragmentSubmissionListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmissionListViewModel by viewModels()
    private var parentId: String? = null
    private var examTitle: String? = null
    private var userId: String? = null
    private lateinit var adapter: SubmissionsListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            parentId = it.getString("parentId")
            examTitle = it.getString("examTitle")
            userId = it.getString("userId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubmissionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = examTitle ?: "Submissions"
        setupRecyclerView()

        collectWhenStarted(viewModel.submissions) { submissionItems ->
            if (_binding != null) {
                adapter.submitList(submissionItems)
            }
        }

        collectWhenStarted(viewModel.exportProgress) { isExporting ->
            if (_binding != null) {
                binding.progressBar.visibility = if (isExporting) View.VISIBLE else View.GONE
            }
        }

        collectWhenStarted(viewModel.exportFile) { file ->
            if (_binding != null) {
                if (file != null) {
                    Toast.makeText(requireContext(), "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    FileUtils.openPdf(requireContext(), file)
                } else {
                    Toast.makeText(requireContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDownloadReport.setOnClickListener {
            val submissionIds = viewModel.submissions.value.mapNotNull { it.id }
            if (submissionIds.isNotEmpty()) {
                generateReport(submissionIds)
            } else {
                Toast.makeText(context, "No submissions to report", Toast.LENGTH_SHORT).show()
            }
        }

        loadSubmissions()
    }

    private fun setupRecyclerView() {
        binding.rvSubmissions.layoutManager = LinearLayoutManager(context)
        binding.rvSubmissions.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )
        val listener = activity as? OnHomeItemClickListener
        adapter = SubmissionsListAdapter(requireContext(), listener) { submissionId ->
            if (submissionId != null) {
                generateSubmissionPdf(submissionId)
            }
        }
        binding.rvSubmissions.adapter = adapter
    }

    private fun loadSubmissions() {
        viewModel.loadSubmissions(parentId, userId)
    }

    private fun generateSubmissionPdf(submissionId: String) {
        viewModel.generateSubmissionPdf(submissionId)
    }

    private fun generateReport(submissionIds: List<String>) {
        viewModel.generateMultipleSubmissionsPdf(submissionIds, examTitle ?: "Submissions")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
