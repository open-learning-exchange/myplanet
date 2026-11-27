package org.ole.planet.myplanet.ui.surveys

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnSurveyAdoptListener
import org.ole.planet.myplanet.databinding.FragmentSurveyBinding
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.TeamSummary
import org.ole.planet.myplanet.utils.collectWhenStarted
import org.ole.planet.myplanet.utils.debounceDistinct
import org.ole.planet.myplanet.utils.textChanges

@AndroidEntryPoint
class SurveyFragment : BaseRecyclerFragment<StepExam?>(), OnSurveyAdoptListener {
    private var _binding: FragmentSurveyBinding? = null
    private val binding get() = _binding!!
    private var adapter: SurveysAdapter? = null
    private var isTeam: Boolean = false
    private var teamId: String? = null
    private val viewModel: SurveysViewModel by viewModels()

    private val adapterMutex = Mutex()

    override fun getLayout(): Int = R.layout.fragment_survey

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        _binding = view?.let { FragmentSurveyBinding.bind(it) }
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTeam = arguments?.getBoolean("isTeam", false) == true
        teamId = arguments?.getString("teamId", null)
    }

    override fun onAdoptSurvey(surveyId: String) {
        if (isTeam) {
            viewModel.adoptSurvey(surveyId)
        } else {
            viewModel.requestAdoption(surveyId)
        }
    }

    private fun showAdoptionTargets(request: SurveysViewModel.AdoptionRequest) {
        if (request.targets.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_survey_adoption_targets, Snackbar.LENGTH_LONG).show()
            return
        }
        val rows = AdoptSurveyTargetsAdapter.rowsFor(
            request.targets,
            getString(R.string.team),
            getString(R.string.enterprises)
        )
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialog)
        builder.setTitle(R.string.adopt_survey_to)
            .setAdapter(AdoptSurveyTargetsAdapter(builder.context, rows)) { _, which ->
                (rows[which] as? TeamSummary)?.let { viewModel.adoptSurveyTo(request.surveyId, it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override suspend fun getAdapter(): ListAdapter<*, *> {
        adapterMutex.withLock {
            if (adapter == null) {
                val userProfileModel = userRepository.getUserModel()
                adapter = SurveysAdapter(
                    requireActivity(),
                    userProfileModel?.id,
                    isTeam,
                    teamId,
                    this@SurveyFragment
                )
            }
        }
        return requireNotNull(adapter) { "SurveysAdapter must be initialized inside mutex" }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews()
        binding.layoutSearch.etSearch.textChanges()
            .drop(1)
            .debounceDistinct(300)
            .onEach { text -> viewModel.search(text?.toString() ?: "") }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewLifecycleOwner.lifecycleScope.launch {
            recyclerView.adapter = getAdapter()
        }
        setupRecyclerView()
        setupListeners()
        showHideRadioButton(isFirstLaunch = savedInstanceState == null)
        setupObservers()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (isTeam && !binding.rbAdoptSurvey.isChecked && !binding.rbTeamSurvey.isChecked) {
            binding.rbTeamSurvey.isChecked = true
        }
        
        viewModel.loadSurveys(isTeam, teamId, isTeam && binding.rbAdoptSurvey.isChecked)
    }

    private fun showHideRadioButton(isFirstLaunch: Boolean) {
        if (isTeam) {
            binding.rgSurvey.visibility = View.VISIBLE
            if (isFirstLaunch) {
                binding.rbTeamSurvey.isChecked = true
            }
        }
    }

    private fun initializeViews() {
        val adapter = ArrayAdapter.createFromResource(
            requireContext(), R.array.sort_by_date, R.layout.spinner_text
        )
        adapter.setDropDownViewResource(R.layout.spinner_text)
        binding.spnSort.adapter = adapter
    }

    private fun setupRecyclerView() {
        recyclerView.setHasFixedSize(true)
    }

    private fun setupListeners() {
        binding.fabAddNewSurvey.setOnClickListener {}

        var isSpinnerInitialized = false
        binding.spnSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true
                    return
                }
                when (i) {
                    0 -> viewModel.sort(SurveysViewModel.SortOption.DATE_DESC)
                    1 -> viewModel.sort(SurveysViewModel.SortOption.DATE_ASC)
                    2 -> viewModel.sort(SurveysViewModel.SortOption.TITLE_ASC)
                    3 -> viewModel.sort(SurveysViewModel.SortOption.TITLE_DESC)
                    4 -> viewModel.sort(SurveysViewModel.SortOption.INCOMPLETE_FIRST)
                    5 -> viewModel.sort(SurveysViewModel.SortOption.COMPLETE_FIRST)
                }
                recyclerView.scrollToPosition(0)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        binding.rbAdoptSurvey.setOnClickListener {
            viewModel.loadSurveys(isTeam, teamId, true)
            recyclerView.scrollToPosition(0)
        }

        binding.rbTeamSurvey.setOnClickListener {
            viewModel.loadSurveys(isTeam, teamId, false)
            recyclerView.scrollToPosition(0)
        }
    }

    private fun setupObservers() {
        collectWhenStarted(viewModel.surveys) { surveys ->
            (getAdapter() as SurveysAdapter).submitList(surveys) {
                recyclerView.scrollToPosition(0)
                updateUIState()
            }
        }
        collectWhenStarted(viewModel.isLoading) { isLoading ->
            binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        collectWhenStarted(viewModel.errorMessage) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.onErrorMessageShown()
            }
        }
        collectWhenStarted(viewModel.adoptionRequests) { request ->
            showAdoptionTargets(request)
        }
        collectWhenStarted(viewModel.adoptedTo) { teamName ->
            Snackbar.make(binding.root, getString(R.string.survey_adopted_to, teamName), Snackbar.LENGTH_LONG).show()
        }
        collectWhenStarted(viewModel.userMessage) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                if (it == "Survey adopted successfully") {
                     binding.rbTeamSurvey.isChecked = true
                }
                viewModel.onUserMessageShown()
            }
        }
    }

    private fun updateUIState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val itemCount = getAdapter().itemCount
            _binding?.spnSort?.visibility = if (itemCount == 0) View.GONE else View.VISIBLE
            showNoData(tvMessage, itemCount, "survey")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
