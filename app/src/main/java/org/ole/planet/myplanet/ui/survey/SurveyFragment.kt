package org.ole.planet.myplanet.ui.survey

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.SurveyAdoptListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.databinding.FragmentSurveyBinding
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utilities.DialogUtils

@AndroidEntryPoint
class SurveyFragment : BaseRecyclerFragment<RealmStepExam?>(), SurveyAdoptListener, RealtimeSyncMixin {
    private var _binding: FragmentSurveyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AdapterSurvey
    private var isTeam: Boolean = false
    private var teamId: String? = null
    private lateinit var realtimeSyncHelper: RealtimeSyncHelper
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private val viewModel: SurveyViewModel by lazy {
        val activity = requireActivity()
        ViewModelProvider(
            activity.viewModelStore,
            activity.defaultViewModelProviderFactory,
            activity.defaultViewModelCreationExtras
        )[SurveyViewModel::class.java]
    }

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
        val userProfileModel = profileDbHandler.userModel
        adapter = AdapterSurvey(requireActivity(), mRealm, userProfileModel?.id, isTeam, teamId, this, settings)

        viewModel.init(isTeam, teamId)
        viewModel.startExamSync()
    }


    override fun onSurveyAdopted() {
        binding.rbTeamSurvey.isChecked = true
        viewModel.setTeamShareAllowed(false)
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
                viewModel.updateSearch(s.toString())
                recyclerView.scrollToPosition(0)
            }

            override fun afterTextChanged(s: Editable) {}
        })
        setupRecyclerView()
        setupListeners()
        showHideRadioButton()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.surveys.collectLatest { state ->
                if (state.isSearch) {
                    adapter.updateData(state.items)
                } else {
                    adapter.updateDataAfterSearch(state.items)
                }
                updateUIState()
                recyclerView.scrollToPosition(0)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncState.collectLatest { state ->
                when (state) {
                    is SurveyViewModel.SyncState.Syncing -> {
                        if (isAdded && !requireActivity().isFinishing) {
                            customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                            customProgressDialog?.setText("Syncing surveys...")
                            customProgressDialog?.show()
                        }
                    }
                    is SurveyViewModel.SyncState.Success -> {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                    }
                    is SurveyViewModel.SyncState.Error -> {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        Snackbar.make(
                            binding.root,
                            "Sync failed: ${state.message}",
                            Snackbar.LENGTH_LONG
                        ).setAction("Retry") { viewModel.startExamSync() }.show()
                    }
                    else -> {}
                }
            }
        }

        viewModel.setTeamShareAllowed(false)
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
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        binding.spnSort.onSameItemSelected(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (i == 2) adapter.toggleTitleSortOrder()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

        binding.rbAdoptSurvey.setOnClickListener {
            viewModel.setTeamShareAllowed(true)
        }

        binding.rbTeamSurvey.setOnClickListener {
            viewModel.setTeamShareAllowed(false)
        }
    }

    private fun updateUIState() {
        val itemCount = adapter.itemCount
        binding.spnSort.visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        showNoData(tvMessage, itemCount, "survey")
    }

    override fun getWatchedTables(): List<String> {
        return listOf("exams")
    }

    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        if (table == "exams" && update.shouldRefreshUI) {
            binding.rbTeamSurvey.isChecked = true
            viewModel.setTeamShareAllowed(false)
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
