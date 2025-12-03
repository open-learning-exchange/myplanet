package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.Sort
import java.io.File
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentSubmissionListBinding
import org.ole.planet.myplanet.model.RealmSubmission

@AndroidEntryPoint
class SubmissionListFragment : Fragment() {
    private var _binding: FragmentSubmissionListBinding? = null
    private val binding get() = _binding!!
    private lateinit var mRealm: Realm
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

        mRealm = Realm.getDefaultInstance()

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
        adapter = SubmissionListAdapter(requireContext(), listener)
        binding.rvSubmissions.adapter = adapter
    }

    private fun loadSubmissions() {
        val submissions = mRealm.where(RealmSubmission::class.java)
            .equalTo("parentId", parentId)
            .equalTo("userId", userId)
            .sort("lastUpdateTime", Sort.DESCENDING)
            .findAll()

        adapter.submitList(submissions.toList())

        binding.btnDownloadReport.setOnClickListener {
            generateReport(submissions.toList())
        }
    }

    private fun generateReport(submissions: List<RealmSubmission>) {
        val file = org.ole.planet.myplanet.utilities.SubmissionPdfGenerator.generateMultipleSubmissionsPdf(
            requireContext(),
            submissions,
            examTitle ?: "Submissions",
            mRealm
        )

        if (file != null) {
            Toast.makeText(context, "Report saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            openPdf(file)
        } else {
            Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPdf(file: File) {
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
            Toast.makeText(context, "Could not open PDF. File saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        mRealm.close()
        _binding = null
        super.onDestroyView()
    }
}
