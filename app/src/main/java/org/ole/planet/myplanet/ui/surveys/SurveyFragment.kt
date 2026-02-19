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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        prefManager = SharedPrefManager(requireContext())
        
        viewModel.startExamSync()
    }

    override fun onAdoptSurvey(surveyId: String) {
        viewModel.adoptSurvey(surveyId)
    }

    override suspend fun getAdapter(): RecyclerView.Adapter<*> {
        adapterMutex.withLock {
            if (!::adapter.isInitialized) {
                val user = profileDbHandler.getUserModel()
                adapter = SurveysAdapter(
                    requireActivity(),
                    user?.id,
                    isTeam,
                    teamId,
                    this,
                    surveyInfoMap,
                    bindingDataMap
                )
            }
        }
        return adapter
    }

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
        viewLifecycleOwner.lifecycleScope.launch {
            recyclerView.adapter = getAdapter()
        }
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
                        viewModel.toggleTitleSort()
                    }
                }
                recyclerView.scrollToPosition(0)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        binding.spnSort.onSameItemSelected(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (i == 2) {
                    viewModel.toggleTitleSort()
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
                    getAdapter()
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
