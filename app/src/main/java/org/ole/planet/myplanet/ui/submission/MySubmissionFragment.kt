package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.databinding.FragmentMySubmissionBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.UserRepository
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
    @Inject
    lateinit var submissionRepository: SubmissionRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    var type: String? = ""
    var exams: HashMap<String?, RealmStepExam>? = null
    private var submissions: List<RealmSubmission>? = null
    private var allSubmissions: List<RealmSubmission> = emptyList()
    var user: RealmUserModel? = null
    private var mRealm: Realm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) type = requireArguments().getString("type")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMySubmissionBinding.inflate(inflater, container, false)
        exams = HashMap()
        user = userProfileDbHandler.userModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvMysurvey.layoutManager = LinearLayoutManager(activity)
        binding.rvMysurvey.addItemDecoration(
            DividerItemDecoration(activity, DividerItemDecoration.VERTICAL)
        )
        loadSubmissions()
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                val cleanString = charSequence.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    setData(cleanString)
                }
            }
            override fun afterTextChanged(editable: Editable) {}
        })
        showHideRadioButton()
    }

    private fun loadSubmissions() {
        viewLifecycleOwner.lifecycleScope.launch {
            val subs = submissionRepository.getSubmissionsByUserId(user?.id ?: "")
            allSubmissions = subs
            exams = HashMap(submissionRepository.getExamMapForSubmissions(subs))
            setData(binding.etSearch.text.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        loadSubmissions()
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
        viewLifecycleOwner.lifecycleScope.launch {
            setData("")
        }
    }

    private suspend fun setData(s: String) {
        var filtered = allSubmissions

        filtered = when (type) {
            "survey" -> filtered.filter { it.userId == user?.id && it.type == "survey" }
            "survey_submission" -> filtered.filter {
                it.userId == user?.id && it.type == "survey" && it.status != "pending"
            }
            else -> filtered.filter { it.userId == user?.id && it.type != "survey" }
        }.sortedByDescending { it.lastUpdateTime }

        if (s.isNotEmpty()) {
            val examIds = exams?.filter { (_, exam) ->
                exam.name?.contains(s, ignoreCase = true) == true
            }?.keys ?: emptySet()
            filtered = filtered.filter { examIds.contains(it.parentId) }
        }

        val groupedSubmissions = filtered.groupBy { it.parentId }

        val submissionsWithCount = groupedSubmissions.map { (_, submissions) ->
            val latestSubmission = submissions.maxByOrNull { it.lastUpdateTime }
            SubmissionWithCount(
                submission = latestSubmission!!,
                count = submissions.size,
                allSubmissions = submissions
            )
        }

        submissions = submissionsWithCount.map { it.submission }

        val submitterIds = submissions?.mapNotNull { it.userId }?.toSet()
        val userNameMap = submitterIds?.mapNotNull { id ->
            val userModel = userRepository.getUserById(id)
            val displayName = userModel?.name
            if (displayName.isNullOrBlank()) {
                null
            } else {
                id to displayName
            }
        }?.toMap()

        val submissionCountMap = submissionsWithCount.associate { it.submission.id to it.count }

        val adapter = AdapterMySubmission(
            requireActivity(),
            submissions,
            exams,
            submissionCountMap,
            nameResolver = { userId -> userId?.let { userNameMap?.get(it) } }
        )
        val itemCount = adapter.itemCount

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
        mRealm?.close()
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
