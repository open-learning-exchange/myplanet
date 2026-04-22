package org.ole.planet.myplanet.ui.submissions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentSubmissionListBinding
import org.ole.planet.myplanet.repository.SubmissionsRepositoryExporter
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class SubmissionListFragment : Fragment() {
    private var _binding: FragmentSubmissionListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmissionListViewModel by viewModels()
    @Inject
    lateinit var submissionsRepositoryExporter: SubmissionsRepositoryExporter
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
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val file = submissionsRepositoryExporter.generateSubmissionPdf(requireContext(), submissionId)
            binding.progressBar.visibility = View.GONE

            if (file != null) {
                Toast.makeText(requireContext(), "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                FileUtils.openPdf(requireContext(), file)
            } else {
                Toast.makeText(requireContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateReport(submissionIds: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val file = submissionsRepositoryExporter.generateMultipleSubmissionsPdf(
                requireContext(),
                submissionIds,
                examTitle ?: "Submissions"
            )
            binding.progressBar.visibility = View.GONE

            if (file != null) {
                Toast.makeText(context, "Report saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                FileUtils.openPdf(requireContext(), file)
            } else {
                Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
