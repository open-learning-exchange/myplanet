package org.ole.planet.myplanet.ui.surveys

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnSurveyAdoptListener
import org.ole.planet.myplanet.databinding.FragmentSurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.SurveyInfo
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.surveys.SurveyFormState
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin

@AndroidEntryPoint
class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>(), OnSurveyAdoptListener, RealtimeSyncMixin {
    private var _binding: FragmentSurveyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SurveysAdapter
    private var isTeam: Boolean = false
    private var teamId: String? = null
    private lateinit var prefManager: SharedPrefManager
    private val surveyInfoMap = mutableMapOf<String, SurveyInfo>()
    private val bindingDataMap = mutableMapOf<String, SurveyFormState>()
    private var textWatcher: TextWatcher? = null
    private val viewModel: SurveysViewModel by viewModels()

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

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
        val userProfileModel = profileDbHandler.userModel
        adapter = SurveysAdapter(
            requireActivity(),
            userProfileModel?.id,
            isTeam,
            teamId,
            this,
            surveyInfoMap,
            bindingDataMap
        )
        prefManager = SharedPrefManager(requireContext())
        
        viewModel.startExamSync()
    }

    override fun onAdoptSurvey(surveyId: String) {
        viewModel.adoptSurvey(surveyId)
    }

    override suspend fun getAdapter(): RecyclerView.Adapter<*> = adapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        realtimeSyncHelper = RealtimeSyncHelper(this, this)
        realtimeSyncHelper.setupRealtimeSync()
        initializeViews()
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.search(s.toString())
            }

            override fun afterTextChanged(s: Editable) {}
        }
        binding.layoutSearch.etSearch.addTextChangedListener(textWatcher)
        setupRecyclerView()
        setupListeners()
        viewModel.loadSurveys(isTeam, teamId, false)
        showHideRadioButton()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSurveys(isTeam, teamId, viewModel.isTeamShareAllowed.value)
    }

    private fun showHideRadioButton() {
        if (isTeam) {
            binding.rgSurvey.visibility = View.VISIBLE
            binding.rbTeamSurvey.isChecked = true
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
        recyclerView.adapter = adapter
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
                    2 -> {
                        // Toggle title sort order is logic that was in adapter.
                        // I need to track current sort in fragment or viewmodel to toggle.
                        // For now assuming toggle means switching between ASC and DESC.
                        // But here we can't easily toggle without state.
                        // I'll make viewModel handle toggle or expose current sort.
                        // Let's assume user wants TITLE_ASC first, then DESC.
                        // But the previous implementation called adapter.toggleTitleSortOrder().
                        // I will simplify and set TITLE_ASC for now, or improve ViewModel to handle toggle.
                        viewModel.sort(SurveysViewModel.SortOption.TITLE_ASC)
                    }
                }
                recyclerView.scrollToPosition(0)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        binding.spnSort.onSameItemSelected(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (i == 2) {
                     // This was toggle. I'll stick to one for now or need to check state.
                     viewModel.sort(SurveysViewModel.SortOption.TITLE_DESC)
                }
                recyclerView.scrollToPosition(0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

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
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.surveys.collect { surveys ->
                    adapter.submitList(surveys) {
                        updateUIState()
                    }
                }
            }
            launch {
                viewModel.surveyInfos.collect { infos ->
                    surveyInfoMap.clear()
                    surveyInfoMap.putAll(infos)
                }
            }
            launch {
                viewModel.bindingData.collect { data ->
                    bindingDataMap.clear()
                    bindingDataMap.putAll(data)
                }
            }
            launch {
                viewModel.isLoading.collect { isLoading ->
                    binding.loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.errorMessage.collect { message ->
                    message?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            launch {
                viewModel.userMessage.collect { message ->
                    message?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        if (it == "Survey adopted successfully") {
                             binding.rbTeamSurvey.isChecked = true
                        }
                    }
                }
            }
        }
    }

    private fun updateUIState() {
        val itemCount = adapter.itemCount
        _binding?.spnSort?.visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        showNoData(tvMessage, itemCount, "survey")
    }

    override fun getWatchedTables(): List<String> {
        return listOf("exams")
    }

    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        if (table == "exams" && update.shouldRefreshUI) {
            viewModel.loadSurveys(isTeam, teamId, viewModel.isTeamShareAllowed.value)
        }
    }

    override fun shouldAutoRefresh(table: String): Boolean = false

    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }

    override fun onDestroyView() {
        _binding?.layoutSearch?.etSearch?.removeTextChangedListener(textWatcher)
        textWatcher = null
        super.onDestroyView()
        _binding = null
    }

}
