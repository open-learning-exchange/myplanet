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
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.repository.SurveyRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

data class SurveyData(
    val surveys: List<RealmStepExam>,
    val questionsMap: Map<String, List<RealmExamQuestion>>,
    val submissionsMap: Map<String, RealmSubmission?>,
    val surveyInfoMap: Map<String, SurveyInfo>
)

@AndroidEntryPoint
class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>(), SurveyAdoptListener, RealtimeSyncMixin {
    private var _binding: FragmentSurveyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AdapterSurvey
    private var isTeam: Boolean = false
    private var teamId: String? = null
    private var currentIsTeamShareAllowed: Boolean = false
    lateinit var prefManager: SharedPrefManager
    private val serverUrlMapper = ServerUrlMapper()
    private var loadSurveysJob: Job? = null
    private var currentSurveys: List<RealmStepExam> = emptyList()
    private val surveyInfoMap = mutableMapOf<String, SurveyInfo>()
    private var textWatcher: TextWatcher? = null

    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var surveyRepository: SurveyRepository
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

    override fun onSurveyAdopted() {
        binding.rbTeamSurvey.isChecked = true
        updateAdapterData(isTeamShareAllowed = false)
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
            surveyRepository.getSurveysFlow(
                isTeam,
                teamId,
                userProfileModel?.id,
                useTeamShareAllowed
            ).collect { surveyData ->
                currentSurveys = surveyData.surveys
                surveyInfoMap.clear()
                surveyInfoMap.putAll(surveyData.surveyInfoMap)
                if (!::adapter.isInitialized) {
                    adapter = AdapterSurvey(
                        requireActivity(),
                        mRealm,
                        userProfileModel?.id,
                        isTeam,
                        teamId,
                        this@SurveyFragment,
                        settings,
                        profileDbHandler,
                        surveyData.surveyInfoMap,
                        surveyData.questionsMap,
                        surveyData.submissionsMap
                    )
                    recyclerView.adapter = adapter
                } else {
                    adapter.submitList(surveyData.surveys)
                }
                applySearchFilter()
            }
        }
    }

    private fun applySearchFilter() {
        val searchText = _binding?.layoutSearch?.etSearch?.text?.toString().orEmpty()
        if (searchText.isNotEmpty()) {
            adapter.updateData(search(searchText, currentSurveys)) {
                updateUIState()
                recyclerView.scrollToPosition(0)
            }
        } else {
            adapter.updateDataAfterSearch(currentSurveys) {
                updateUIState()
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
            updateAdapterData(isTeamShareAllowed = false)
        }
    }

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
