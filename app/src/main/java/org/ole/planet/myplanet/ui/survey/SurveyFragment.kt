package org.ole.planet.myplanet.ui.survey

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.SurveyAdoptListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.databinding.FragmentSurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.service.sync.SyncManager
import org.ole.planet.myplanet.ui.survey.SurveyFormState
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.model.SurveyInfo
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager

@AndroidEntryPoint
class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>(), SurveyAdoptListener, RealtimeSyncMixin {
    private var _binding: FragmentSurveyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SurveyAdapter
    private var isTeam: Boolean = false
    private var teamId: String? = null
    private var currentIsTeamShareAllowed: Boolean = false
    lateinit var prefManager: SharedPrefManager
    private val serverUrlMapper = ServerUrlMapper()
    private var loadSurveysJob: Job? = null
    private var currentSurveys: List<RealmStepExam> = emptyList()
    private val surveyInfoMap = mutableMapOf<String, SurveyInfo>()
    private val bindingDataMap = mutableMapOf<String, SurveyFormState>()
    private var textWatcher: TextWatcher? = null

    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var surveysRepository: SurveysRepository
    private lateinit var realtimeSyncHelper: RealtimeSyncHelper
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null

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
        adapter = SurveyAdapter(
            requireActivity(),
            userProfileModel?.id,
            isTeam,
            teamId,
            this,
            surveyInfoMap,
            bindingDataMap
        )
        prefManager = SharedPrefManager(requireContext())
        
        startExamSync()
    }

    private fun startExamSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isExamsSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                launchWhenViewIsReady {
                    if (!requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText("Syncing surveys...")
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                prefManager.setExamsSynced(true)
                val job = launchWhenViewIsReady {
                    customProgressDialog?.dismiss()
                    customProgressDialog = null
                    updateAdapterData(isTeamShareAllowed = false)
                }
                if (job == null) {
                    customProgressDialog?.dismiss()
                    customProgressDialog = null
                }
            }

            override fun onSyncFailed(msg: String?) {
                val job = launchWhenViewIsReady {
                    customProgressDialog?.dismiss()
                    customProgressDialog = null
                    _binding?.let { binding ->
                        Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                            .setAction("Retry") { startExamSync() }
                            .show()
                    }
                }
                if (job == null) {
                    customProgressDialog?.dismiss()
                    customProgressDialog = null
                }
            }
        }, "full", listOf("exams"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    override fun onAdoptSurvey(surveyId: String) {
        val userProfileModel = profileDbHandler.userModel
        lifecycleScope.launch {
            try {
                surveysRepository.adoptSurvey(surveyId, userProfileModel?.id, teamId, isTeam)
                Snackbar.make(binding.root, getString(R.string.survey_adopted_successfully), Snackbar.LENGTH_LONG).show()
                binding.rbTeamSurvey.isChecked = true
                updateAdapterData(isTeamShareAllowed = false)
            } catch (e: Exception) {
                Snackbar.make(binding.root, getString(R.string.failed_to_adopt_survey), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun getAdapter(): RecyclerView.Adapter<*> = adapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        realtimeSyncHelper = RealtimeSyncHelper(this, this)
        realtimeSyncHelper.setupRealtimeSync()
        initializeViews()
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                applySearchFilter()
            }

            override fun afterTextChanged(s: Editable) {}
        }
        binding.layoutSearch.etSearch.addTextChangedListener(textWatcher)
        setupRecyclerView()
        setupListeners()
        updateAdapterData(isTeamShareAllowed = false)
        showHideRadioButton()
    }

    override fun onResume() {
        super.onResume()
        updateAdapterData(currentIsTeamShareAllowed)
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
                    0 -> adapter.sortByDate(false)
                    1 -> adapter.sortByDate(true)
                    2 -> adapter.toggleTitleSortOrder()
                }
                recyclerView.scrollToPosition(0)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        binding.spnSort.onSameItemSelected(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (i == 2) adapter.toggleTitleSortOrder()
                recyclerView.scrollToPosition(0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        binding.rbAdoptSurvey.setOnClickListener {
            updateAdapterData(isTeamShareAllowed = true)
            recyclerView.scrollToPosition(0)
        }

        binding.rbTeamSurvey.setOnClickListener {
            updateAdapterData(isTeamShareAllowed = false)
            recyclerView.scrollToPosition(0)
        }
    }

    private fun search(s: String, list: List<RealmStepExam>): List<RealmStepExam> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQuery = normalizeText(s)
        val startsWithQuery = mutableListOf<RealmStepExam>()
        val containsQuery = mutableListOf<RealmStepExam>()

        for (item in list) {
            val title = item.name?.let { normalizeText(it) } ?: continue
            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (queryParts.all { title.contains(normalizeText(it), ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    fun updateAdapterData(isTeamShareAllowed: Boolean? = null) {
        val useTeamShareAllowed = isTeamShareAllowed ?: currentIsTeamShareAllowed
        currentIsTeamShareAllowed = useTeamShareAllowed
        val userProfileModel = profileDbHandler.userModel
        loadSurveysJob?.cancel()
        loadSurveysJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.loadingSpinner.visibility = View.VISIBLE
            try {
                val (surveys, infos, data) = withContext(Dispatchers.IO) {
                    val currentSurveys = when {
                        isTeam && useTeamShareAllowed -> surveysRepository.getAdoptableTeamSurveys(teamId)
                        isTeam -> surveysRepository.getTeamOwnedSurveys(teamId)
                        else -> surveysRepository.getIndividualSurveys()
                    }
                    val surveyInfos = surveysRepository.getSurveyInfos(
                        isTeam,
                        teamId,
                        userProfileModel?.id,
                        currentSurveys
                    )
                    val bindingData = surveysRepository.getSurveyFormState(currentSurveys, teamId)
                    Triple(currentSurveys, surveyInfos, bindingData)
                }
                currentSurveys = surveys.sortedByDescending { survey ->
                    if (survey.sourceSurveyId != null) {
                        if (survey.adoptionDate > 0) survey.adoptionDate else survey.createdDate
                    } else {
                        survey.createdDate
                    }
                }
                surveyInfoMap.clear()
                surveyInfoMap.putAll(infos)
                bindingDataMap.clear()
                bindingDataMap.putAll(data)
                binding.spnSort.setSelection(0, false)
                applySearchFilter()
            } finally {
                if (isAdded && _binding != null) {
                    binding.loadingSpinner.visibility = View.GONE
                }
            }
        }
    }

    private fun applySearchFilter() {
        val searchText = _binding?.layoutSearch?.etSearch?.text?.toString().orEmpty()
        val list = if (searchText.isNotEmpty()) {
            search(searchText, currentSurveys)
        } else {
            currentSurveys
        }
        adapter.submitList(list) {
            updateUIState()
            if (searchText.isNotEmpty()) {
                recyclerView.scrollToPosition(0)
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
            updateAdapterData(currentIsTeamShareAllowed)
        }
    }

    override fun shouldAutoRefresh(table: String): Boolean = false

    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }

    override fun onDestroyView() {
        if (::realtimeSyncHelper.isInitialized) {
            realtimeSyncHelper.cleanup()
        }
        loadSurveysJob?.cancel()
        loadSurveysJob = null
        currentSurveys = emptyList()
        _binding?.layoutSearch?.etSearch?.removeTextChangedListener(textWatcher)
        textWatcher = null
        super.onDestroyView()
        _binding = null
    }

    private fun launchWhenViewIsReady(block: suspend CoroutineScope.() -> Unit): Job? {
        val owner = viewLifecycleOwnerLiveData.value ?: return null
        return owner.lifecycleScope.launch {
            if (!isAdded || _binding == null) return@launch
            block()
        }
    }

}
