package org.ole.planet.myplanet.ui.submission

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Sort
import javax.inject.Inject
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
            generateSubmissionPdf(submissionId)
        }
        binding.rvSubmissions.adapter = adapter
    }

    private fun loadSubmissions() {
        databaseService.withRealm { realm ->
            val submissions = realm.where(RealmSubmission::class.java)
                .equalTo("parentId", parentId)
                .equalTo("userId", userId)
                .sort("lastUpdateTime", Sort.DESCENDING)
                .findAll()

            val submissionItems = submissions.map {
                SubmissionItem(
                    submission = realm.copyFromRealm(it),
                    examName = examTitle,
                    submissionCount = 1,
                    userName = null
                )
            }
            adapter.submitList(submissionItems)

            binding.btnDownloadReport.setOnClickListener {
                generateReport(submissionItems.map { it.submission })
            }
        }
    }

    private fun generateSubmissionPdf(submissionId: String?) {
        databaseService.withRealm { realm ->
            val submission = realm.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
            if (submission != null) {
                val file = SubmissionPdfGenerator.generateSubmissionPdf(requireContext(), submission, realm)
                if (file != null) {
                    Toast.makeText(requireContext(), "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    openPdf(file)
                } else {
                    Toast.makeText(requireContext(), "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateReport(submissions: List<RealmSubmission>) {
        databaseService.withRealm { realm ->
            val file = SubmissionPdfGenerator.generateMultipleSubmissionsPdf(
                requireContext(),
                submissions,
                examTitle ?: "Submissions",
                realm
            )
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
