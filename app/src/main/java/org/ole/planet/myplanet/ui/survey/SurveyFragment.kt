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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.SurveyAdoptListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.databinding.FragmentSurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

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
    
    @Inject
    lateinit var syncManager: SyncManager
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
        profileDbHandler = UserProfileDbHandler(requireContext())
        val userProfileModel = profileDbHandler?.userModel
        adapter = AdapterSurvey(requireActivity(), mRealm, userProfileModel?.id, isTeam, teamId, this, settings)
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
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText("Syncing surveys...")
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        updateAdapterData(isTeamShareAllowed = false)
                        prefManager.setExamsSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG).setAction("Retry") { startExamSync() }.show()
                    }
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
        binding.layoutSearch.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateAdapterData()
                recyclerView.scrollToPosition(0)
            }

            override fun afterTextChanged(s: Editable) {}
        })
        setupRecyclerView()
        setupListeners()
        updateAdapterData(isTeamShareAllowed = false)
        showHideRadioButton()
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

        val submissionQuery = mRealm.where(RealmSubmission::class.java)
            .isNotNull("membershipDoc")
            .findAll()
        val query = mRealm.where(RealmStepExam::class.java).equalTo("type", "surveys")

        if (isTeam) {
            val teamSpecificExams = submissionQuery
                .filter { it.membershipDoc?.teamId == teamId }
                .mapNotNull { JSONObject(it.parent ?: "{}").optString("_id") }
                .filter { it.isNotEmpty() }
                .toSet()

            if (useTeamShareAllowed) {
                val teamSubmissions = submissionQuery
                    .filter { it.membershipDoc?.teamId == teamId }
                    .mapNotNull { JSONObject(it.parent ?: "{}").optString("_id") }
                    .filter { it.isNotEmpty() }
                    .toSet()

                query.beginGroup()
                    .equalTo("isTeamShareAllowed", true)
                    .and()
                    .not().`in`("id", teamSubmissions.toTypedArray())
                query.endGroup()
            } else {
                query.beginGroup()
                    .equalTo("teamId", teamId)
                    .or()
                    .`in`("id", teamSpecificExams.toTypedArray())
                query.endGroup()
            }
        } else {
            query.equalTo("isTeamShareAllowed", false)
        }

        val surveys = query.findAll()

        if ("${binding.layoutSearch.etSearch.text}".isNotEmpty()) {
            adapter.updateData(
                safeCastList(
                    search("${binding.layoutSearch.etSearch.text}", surveys),
                    RealmStepExam::class.java
                )
            )
        } else {
            adapter.updateDataAfterSearch(
                safeCastList(surveys, RealmStepExam::class.java)
            )
        }

        updateUIState()
        recyclerView.scrollToPosition(0)
    }

    private fun updateUIState() {
        val itemCount = adapter.itemCount
        binding.spnSort.visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        showNoData(tvMessage, itemCount, "survey")
    }

    private fun <T> safeCastList(items: List<Any?>, clazz: Class<T>): List<T> {
        return items.mapNotNull { it?.takeIf(clazz::isInstance)?.let(clazz::cast) }
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
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SurveyFragment = SurveyFragment()
    }
}
