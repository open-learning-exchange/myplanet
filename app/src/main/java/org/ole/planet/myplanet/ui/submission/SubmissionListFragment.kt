package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Sort
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentSubmissionListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import javax.inject.Inject

@AndroidEntryPoint
class SubmissionListFragment : Fragment() {
    private var _binding: FragmentSubmissionListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var databaseService: DatabaseService
    private val pdfGeneratorViewModel: PdfGeneratorViewModel by viewModels()
    private var parentId: String? = null
    private var examTitle: String? = null
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            parentId = it.getString("parentId")
            examTitle = it.getString("examTitle")
            userId = it.getString("userId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubmissionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = examTitle ?: "Submissions"
        setupRecyclerView()
        loadSubmissions()
        observePdfGeneration()
        binding.btnCancelPdf.setOnClickListener {
            pdfGeneratorViewModel.cancel()
        }
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
    }

    private fun loadSubmissions() {
        databaseService.withRealm { realm ->
            val submissions = realm.where(RealmSubmission::class.java)
                .equalTo("parentId", parentId)
                .equalTo("userId", userId)
                .sort("lastUpdateTime", Sort.DESCENDING)
                .findAll()
            val listener = activity as? OnHomeItemClickListener
            val adapter = SubmissionListAdapter(
                requireContext(),
                submissions.toList(),
                databaseService,
                listener,
                pdfGeneratorViewModel
            )
            binding.rvSubmissions.adapter = adapter
            binding.btnDownloadReport.setOnClickListener {
                generateReport(submissions.toList())
            }
        }
    }

    private fun generateReport(submissions: List<RealmSubmission>) {
        databaseService.withRealm { realm ->
            val file =
                org.ole.planet.myplanet.utilities.SubmissionPdfGenerator.generateMultipleSubmissionsPdf(
                    requireContext(),
                    submissions,
                    examTitle ?: "Submissions",
                    realm
                )
            if (file != null) {
                Toast.makeText(context, "Report saved to ${file.absolutePath}", Toast.LENGTH_LONG)
                    .show()
                openPdf(file)
            } else {
                Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observePdfGeneration() {
        pdfGeneratorViewModel.pdfGenerationState.observe(viewLifecycleOwner) { state ->
            binding.pdfGenerationContainer.isVisible = state is PdfGenerationState.Loading
            when (state) {
                is PdfGenerationState.Success -> {
                    Toast.makeText(
                        context,
                        "PDF saved to ${state.file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                    openPdf(state.file)
                }
                is PdfGenerationState.Error -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                }
            }
        }
    }

    private fun openPdf(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Could not open PDF. File saved at: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
