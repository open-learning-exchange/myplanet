package org.ole.planet.myplanet.ui.submissions

import android.os.Bundle
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.databinding.FragmentMySubmissionBinding
import org.ole.planet.myplanet.services.UserSessionManager
import kotlinx.coroutines.flow.drop
import org.ole.planet.myplanet.utils.collectLatestWhenStarted
import org.ole.planet.myplanet.utils.textChanges

@AndroidEntryPoint
@OptIn(kotlinx.coroutines.FlowPreview::class)
class SubmissionsFragment : Fragment(), CompoundButton.OnCheckedChangeListener {
    private var _binding: FragmentMySubmissionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmissionViewModel by viewModels()

    @Inject
    lateinit var userSessionManager: UserSessionManager

    private lateinit var adapter: SubmissionsAdapter
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

        adapter = SubmissionsAdapter(requireActivity())
        adapter.setType(type)
        binding.rvMysurvey.adapter = adapter

        viewModel.setFilter(type ?: "", "")

        collectLatestWhenStarted(viewModel.submissions) { submissions ->
            adapter.submitList(submissions)
            updateEmptyState(submissions.size)
        }



        binding.etSearch.textChanges()
            .drop(1)
            .debounce(300)
            .onEach { text -> viewModel.setFilter(type ?: "", text?.toString() ?: "") }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        showHideRadioButton()
    }

    override fun onResume() {
        super.onResume()
        // Filter is already set in onViewCreated and maintained by ViewModel
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
        adapter.setType(type)
        viewModel.setFilter(type ?: "", binding.etSearch.text.toString())
    }

    private fun updateEmptyState(itemCount: Int) {
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
    }

    override fun onDestroyView() {
        binding.rbExam.setOnCheckedChangeListener(null)
        binding.rbSurvey.setOnCheckedChangeListener(null)
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(type: String?): Fragment {
            val fragment = SubmissionsFragment()
            val b = Bundle()
            b.putString("type", type)
            fragment.arguments = b
            return fragment
        }
    }
}
