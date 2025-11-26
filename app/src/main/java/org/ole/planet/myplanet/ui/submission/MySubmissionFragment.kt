package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.databinding.FragmentMySubmissionBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler

data class SubmissionWithCount(
    val submission: RealmSubmission,
    val count: Int,
    val allSubmissions: List<RealmSubmission>
)

@AndroidEntryPoint
class MySubmissionFragment : Fragment(), CompoundButton.OnCheckedChangeListener {
    private var _binding: FragmentMySubmissionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmissionViewModel by viewModels()
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    var type: String? = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) type = requireArguments().getString("type")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMySubmissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvMysurvey.layoutManager = LinearLayoutManager(activity)
        binding.rvMysurvey.addItemDecoration(
            DividerItemDecoration(activity, DividerItemDecoration.VERTICAL)
        )
        viewModel.loadSubmissions(type ?: "", "")

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.submissions.collectLatest { submissions ->
                val exams = viewModel.exams.value
                val userNames = viewModel.userNames.value
                val submissionCounts = viewModel.submissionCounts.value
                setData(submissions, exams, userNames, submissionCounts)
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                viewModel.loadSubmissions(type ?: "", charSequence.toString())
            }
            override fun afterTextChanged(editable: Editable) {}
        })
        showHideRadioButton()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSubmissions(type ?: "", binding.etSearch.text.toString())
    }

    private fun showHideRadioButton() {
        if (type != "survey") {
            binding.rbExam.isChecked = true
            binding.rbExam.setOnCheckedChangeListener(this)
            binding.rbSurvey.setOnCheckedChangeListener(this)
        } else {
            binding.rbSurvey.visibility = View.GONE
            binding.rbExam.visibility = View.GONE
            binding.rgSubmission.visibility = View.GONE
        }
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        type = if (binding.rbSurvey.isChecked) {
            "survey_submission"
        } else {
            "exam"
        }
        viewModel.loadSubmissions(type ?: "", binding.etSearch.text.toString())
    }

    private fun setData(
        submissions: List<RealmSubmission>,
        exams: HashMap<String?, RealmStepExam>,
        userNameMap: Map<String, String>,
        submissionCountMap: Map<String?, Int>
    ) {
        val adapter = AdapterMySubmission(
            requireActivity(),
            submissions,
            exams,
            submissionCountMap,
            nameResolver = { userId -> userId?.let { userNameMap[it] } }
        )
        val itemCount = adapter.itemCount
        val s = binding.etSearch.text.toString()

        if (s.isEmpty()) {
            binding.llSearch.visibility = View.VISIBLE
            binding.title.visibility = View.VISIBLE
            if (binding.rbSurvey.isChecked || type == "survey") {
                binding.tvFragmentInfo.text = "mySurveys"
                showNoData(binding.tvMessage, itemCount, "survey_submission")
            } else {
                binding.tvFragmentInfo.text = "mySubmissions"
                showNoData(binding.tvMessage, itemCount, "exam_submission")
            }

            if (itemCount == 0) {
                binding.title.visibility = View.GONE
                binding.tlSearch.visibility = View.GONE
            } else {
                binding.tvMessage.visibility = View.GONE
                binding.title.visibility = View.VISIBLE
                binding.tlSearch.visibility = View.VISIBLE
            }
        }

        adapter.setType(type)
        binding.rvMysurvey.adapter = adapter
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun newInstance(type: String?): Fragment {
            val fragment = MySubmissionFragment()
            val b = Bundle()
            b.putString("type", type)
            fragment.arguments = b
            return fragment
        }
    }
}
