package org.ole.planet.myplanet.ui.submission

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentSubmissionListBinding
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.SubmissionPdfGenerator
import java.io.File

@AndroidEntryPoint
class SubmissionListFragment : Fragment() {
    private var _binding: FragmentSubmissionListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmissionViewModel by viewModels()
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
        observeSubmissions()
        loadSubmissions()

        binding.btnDownloadReport.setOnClickListener {
            generateReport(adapter.currentList)
        }
    }

    private fun setupRecyclerView() {
        adapter = SubmissionListAdapter(requireContext()) { submission, action ->
            when (action) {
                "view" -> openSubmissionDetail(submission.id)
                "download" -> generateSubmissionPdf(submission)
            }
        }
        binding.rvSubmissions.layoutManager = LinearLayoutManager(context)
        binding.rvSubmissions.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        binding.rvSubmissions.adapter = adapter
    }

    private fun observeSubmissions() {
        lifecycleScope.launch {
            viewModel.submissionsByParent.collect { submissions ->
                adapter.submitList(submissions)
            }
        }
    }

    private fun loadSubmissions() {
        parentId?.let { pId ->
            userId?.let { uId ->
                viewModel.loadSubmissions(pId, uId)
            }
        }
    }

    private fun openSubmissionDetail(id: String?) {
        val b = Bundle()
        b.putString("id", id)
        val fragment = SubmissionDetailFragment()
        fragment.arguments = b
        parentFragmentManager.beginTransaction()
            .replace(org.ole.planet.myplanet.R.id.content_frame, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun generateSubmissionPdf(submission: RealmSubmission) {
        val mRealm = Realm.getDefaultInstance()
        val file = SubmissionPdfGenerator.generateSubmissionPdf(requireContext(), submission, mRealm)
        mRealm.close()

        if (file != null) {
            Toast.makeText(context, "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            openPdf(file)
        } else {
            Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateReport(submissions: List<RealmSubmission>) {
        val mRealm = Realm.getDefaultInstance()
        val file = SubmissionPdfGenerator.generateMultipleSubmissionsPdf(
            requireContext(),
            submissions,
            examTitle ?: "Submissions",
            mRealm
        )
        mRealm.close()

        if (file != null) {
            Toast.makeText(context, "Report saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            openPdf(file)
        } else {
            Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPdf(file: File) {
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
