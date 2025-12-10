package org.ole.planet.myplanet.ui.submission

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
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentSubmissionListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.SubmissionPdfGenerator

@AndroidEntryPoint
class SubmissionListFragment : Fragment() {
    private var _binding: FragmentSubmissionListBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var databaseService: DatabaseService
    private var parentId: String? = null
    private var examTitle: String? = null
    private var userId: String? = null
    private lateinit var adapter: SubmissionListAdapter

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
        adapter = SubmissionListAdapter(requireContext(), listener) { submissionId ->
            if (submissionId != null) {
                generateSubmissionPdf(submissionId)
            }
        }
        binding.rvSubmissions.adapter = adapter
    }

    private fun loadSubmissions() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var submissionItems: List<SubmissionItem> = emptyList()
            databaseService.withRealm { realm ->
                val submissions = realm.where(RealmSubmission::class.java)
                    .equalTo("parentId", parentId)
                    .equalTo("userId", userId)
                    .sort("lastUpdateTime", Sort.DESCENDING)
                    .findAll()

                submissionItems = submissions.map {
                    SubmissionItem(
                        submission = realm.copyFromRealm(it),
                        examName = examTitle,
                        submissionCount = 1,
                        userName = null
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                adapter.submitList(submissionItems)

                binding.btnDownloadReport.setOnClickListener {
                    generateReport(submissionItems.map { it.submission })
                }
            }
        }
    }

    private fun generateSubmissionPdf(submissionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val file = SubmissionPdfGenerator.generateSubmissionPdf(requireContext(), submissionId)
            binding.progressBar.visibility = View.GONE

            if (file != null) {
                Toast.makeText(requireContext(), "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                openPdf(file)
            } else {
                Toast.makeText(requireContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateReport(submissions: List<RealmSubmission>) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val file = SubmissionPdfGenerator.generateMultipleSubmissionsPdf(
                requireContext(),
                submissions.mapNotNull { it.id },
                examTitle ?: "Submissions"
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
