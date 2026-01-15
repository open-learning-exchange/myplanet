package org.ole.planet.myplanet.ui.submissions

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.databinding.FragmentSubmissionListBinding
import org.ole.planet.myplanet.model.SubmissionItem
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.utilities.SubmissionPdfUtils

@AndroidEntryPoint
class SubmissionListFragment : Fragment() {
    private var _binding: FragmentSubmissionListBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var submissionsRepository: SubmissionsRepository
    @Inject
    lateinit var databaseService: DatabaseService
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
        loadSubmissions()
    }

    override fun onResume() {
        super.onResume()
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val submissions = submissionsRepository.getSubmissionsByParentId(parentId, userId)
            val submissionItems = submissions.map {
                SubmissionItem(
                    id = it.id,
                    lastUpdateTime = it.lastUpdateTime,
                    status = it.status ?: "",
                    uploaded = it.uploaded
                )
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                adapter.submitList(submissionItems)

                val submissionIds = submissions.mapNotNull { it.id }
                binding.btnDownloadReport.setOnClickListener {
                    generateReport(submissionIds)
                }
            }
        }
    }

    private fun generateSubmissionPdf(submissionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val file = SubmissionPdfUtils.generateSubmissionPdf(requireContext(), submissionId, databaseService)
            binding.progressBar.visibility = View.GONE

            if (file != null) {
                Toast.makeText(requireContext(), "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                openPdf(file)
            } else {
                Toast.makeText(requireContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateReport(submissionIds: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val file = SubmissionPdfUtils.generateMultipleSubmissionsPdf(
                requireContext(),
                submissionIds,
                examTitle ?: "Submissions",
                databaseService
            )
            binding.progressBar.visibility = View.GONE

            if (file != null) {
                Toast.makeText(context, "Report saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                openPdf(file)
            } else {
                Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openPdf(file: java.io.File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not open PDF. File saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
