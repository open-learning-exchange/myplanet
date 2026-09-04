package org.ole.planet.myplanet.ui.health

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentVitalSignBinding
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.ui.user.BecomeMemberActivity
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted
import org.ole.planet.myplanet.utils.textChanges

@AndroidEntryPoint
@OptIn(FlowPreview::class)
class MyHealthFragment : Fragment() {

    private val viewModel: HealthViewModel by viewModels()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }


    @Inject
    lateinit var realtimeSyncManager: RealtimeSyncManager
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider
    private var _binding: FragmentVitalSignBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertMyPersonalBinding: AlertMyPersonalBinding
    private var alertHealthListBinding: AlertHealthListBinding? = null
    var userId: String? = null
    var userModel: UserEntity? = null
    var loggedInUser: UserEntity? = null
    lateinit var userModelList: List<UserEntity>
    lateinit var adapter: HealthUsersAdapter
    private lateinit var healthAdapter: HealthExaminationAdapter
    var dialog: AlertDialog? = null

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVitalSignBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun refreshHealthData() {
        if (!isAdded || requireActivity().isFinishing) return
        viewModel.loadInitialPatient()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
        setupRealtimeSync()
        alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))

        val allowDateEdit = false
        if(allowDateEdit) {
            binding.txtDob.setOnClickListener {
                val now = Calendar.getInstance()
                val dpd = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                    val selectedDate =
                        String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)
                    binding.txtDob.text = selectedDate
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                dpd.show()
            }
        } else {
            disableDobField()
        }

        binding.rvRecords.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))

        adapter = HealthUsersAdapter()

        observeData()

        setupInitialData()
    }

    private fun observeData() {

        collectWhenStarted(viewModel.loggedInUser) { user ->
            loggedInUser = user
            setupButtons()
        }


        collectWhenStarted(viewModel.patientList) { users ->
            if (::adapter.isInitialized) {
                adapter.submitList(users)
                alertHealthListBinding?.btnAddMember?.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        collectWhenStarted(viewModel.patientDetailState) { state ->
            val currentUser = state.user
            val healthRecord = state.healthRecord

            if (currentUser != null) {
                userModel = currentUser
                binding.lblHealthName.text = getDisplayName(currentUser)
                userId = if (currentUser._id.isNullOrEmpty()) currentUser.id else currentUser._id
                setupButtons()
            } else {
                userModel = null
                binding.lblHealthName.text = ""
                userId = null
            }
            val uid = userId
            if (currentUser == null || uid.isNullOrEmpty()) {
                binding.layoutUserDetail.visibility = View.GONE
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = getString(R.string.health_record_not_available)
                binding.txtOtherNeed.text = getString(R.string.empty_text)
                binding.txtSpecialNeeds.text = getString(R.string.empty_text)
                binding.txtBirthPlace.text = getString(R.string.empty_text)
                binding.txtEmergencyContact.text = getString(R.string.empty_text)
                binding.rvRecords.adapter = null
                binding.rvRecords.visibility = View.GONE
                binding.tvNoRecords.visibility = View.VISIBLE
                binding.tvDataPlaceholder.visibility = View.GONE
                return@collectWhenStarted
            }

            binding.layoutUserDetail.visibility = View.VISIBLE
            binding.tvMessage.visibility = View.GONE
            binding.txtFullName.text = getDisplayName(currentUser)
            ImageUtils.loadPlaceholderImage(currentUser.userImage, binding.userImage)
            binding.txtEmail.text = Utilities.checkNA(currentUser.email)
            binding.txtLanguage.text = Utilities.checkNA(currentUser.language)
            binding.txtDob.text = TimeUtils.formatDateToDDMMYYYY(currentUser.dob).ifEmpty { "dd-MM-yyyy" }

            if (healthRecord != null) {
                val (mh, mm, list, userMap) = healthRecord
                val myHealths = mm.profile
                binding.txtOtherNeed.text = Utilities.checkNA(myHealths?.notes)
                binding.txtSpecialNeeds.text = Utilities.checkNA(myHealths?.specialNeeds)
                binding.txtBirthPlace.text = Utilities.checkNA(currentUser.birthPlace)
                val contact = myHealths?.emergencyContact?.takeIf { it.isNotBlank() }
                binding.txtEmergencyContact.text = getString(
                    R.string.emergency_contact_details,
                    Utilities.checkNA(myHealths?.emergencyContactName),
                    Utilities.checkNA(myHealths?.emergencyContactType),
                    Utilities.checkNA(contact)
                ).trimIndent()

                if (list.isNotEmpty()) {
                    binding.rvRecords.visibility = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                    binding.tvDataPlaceholder.visibility = View.VISIBLE

                    if (!::healthAdapter.isInitialized) {
                        healthAdapter = HealthExaminationAdapter(requireActivity(), mh, currentUser, userMap, dispatcherProvider)
                    }
                    healthAdapter.updateData(mh, currentUser, userMap, list)
                    binding.rvRecords.apply {
                        layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                        isNestedScrollingEnabled = false
                        adapter = healthAdapter
                    }

                    binding.rvRecords.post {
                        val lastPosition = list.size - 1
                        if (lastPosition >= 0) {
                            binding.rvRecords.scrollToPosition(lastPosition)
                        }
                    }
                } else {
                    binding.rvRecords.visibility = View.GONE
                    binding.tvNoRecords.visibility = View.GONE
                    binding.tvDataPlaceholder.visibility = View.VISIBLE
                }
            } else {
                binding.txtOtherNeed.text = getString(R.string.empty_text)
                binding.txtSpecialNeeds.text = getString(R.string.empty_text)
                binding.txtBirthPlace.text = getString(R.string.empty_text)
                binding.txtEmergencyContact.text = getString(R.string.empty_text)
                binding.rvRecords.adapter = null
                binding.rvRecords.visibility = View.GONE
                binding.tvNoRecords.visibility = View.VISIBLE
                binding.tvDataPlaceholder.visibility = View.GONE
            }
        }

        collectWhenStarted(viewModel.isListLoading) { isLoading ->
            if (isLoading) {
                alertHealthListBinding?.searchProgress?.visibility = View.VISIBLE
                alertHealthListBinding?.list?.visibility = View.GONE
            } else {
                alertHealthListBinding?.searchProgress?.visibility = View.GONE
                alertHealthListBinding?.list?.visibility = View.VISIBLE
            }
        }
    }


    private fun setupInitialData() {
        viewModel.loadInitialPatient()
    }

    private fun setupButtons() {
        val isHealthProvider = loggedInUser?.rolesList?.contains("health") ?: false
        binding.btnnewPatient.visibility = if (isHealthProvider) View.VISIBLE else View.GONE

        binding.btnnewPatient.setOnClickListener {
            if (isHealthProvider) {
                selectPatient()
            }
        }
        binding.updateHealth.visibility = View.VISIBLE

        binding.addNewRecord.setOnClickListener {
            startActivity(Intent(activity, HealthExaminationActivity::class.java).putExtra("userId", userId))
        }

        binding.updateHealth.setOnClickListener {
            startActivity(Intent(activity, AddHealthActivity::class.java).putExtra("userId", userId))
        }

        binding.txtDob.text = if (userModel?.dob.isNullOrEmpty()) getString(R.string.birth_date) else TimeUtils.formatDateToDDMMYYYY(userModel?.dob)
    }

    private fun setupRealtimeSync() {
        collectWhenStarted(realtimeSyncManager.dataUpdateFlow) { update ->
            if (update.table == "health" && update.shouldRefreshUI) {
                refreshHealthData()
            }
        }
    }

    private fun selectPatient() {
        adapter = HealthUsersAdapter { selected ->
            userId = if (selected._id.isNullOrEmpty()) selected.id else selected._id
            val normalizedId = userId?.trim()
            if (!normalizedId.isNullOrEmpty()) {
                viewModel.selectPatient(normalizedId)
            }
            dialog?.dismiss()
        }

        viewModel.loadPatients()

        alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(context))
        alertHealthListBinding?.btnAddMember?.setOnClickListener {
            startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
        }

        alertHealthListBinding?.let { binding ->
            binding.list.layoutManager = LinearLayoutManager(requireContext())
            binding.list.adapter = adapter
            setTextWatcher(binding.etSearch, binding.btnAddMember, binding.list)
            sortList(binding.spnSort, binding.list)
            dialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle(getString(R.string.select_health_member)).setView(binding.root)
                .setCancelable(false).setNegativeButton(R.string.dismiss, null).create()
            dialog?.show()
        }
    }

    private fun sortList(spnSort: AppCompatSpinner, rv: RecyclerView) {
        spnSort.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val (sortBy, sort) = when (p2) {
                    0 -> "joinDate" to true
                    1 -> "joinDate" to false
                    2 -> "name" to false
                    else -> "name" to true
                }
                viewModel.loadPatients(sortBy, sort)
            }
        }
    }

    private fun setTextWatcher(etSearch: EditText, btnAddMember: Button, rv: RecyclerView) {
        searchJob?.cancel()
        searchJob = etSearch.textChanges()
            .drop(1)
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { query -> viewModel.searchPatients(query?.toString() ?: "", "joinDate", true) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        val uid = userId
        if (!uid.isNullOrEmpty()) {
            val normalizedId = uid.trim()
            viewModel.selectPatient(normalizedId)
        }
    }

    private fun getDisplayName(user: UserEntity?): String {
        if (user == null) return getString(R.string.n_a)

        val fullName = listOfNotNull(user.firstName, user.middleName, user.lastName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        return fullName.ifEmpty { user.name.orEmpty() }
    }

    private fun disableDobField() {
        binding.txtDob.isClickable = false
        binding.txtDob.isFocusable = false
        binding.txtDob.setOnClickListener(null)
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        searchJob = null

        _binding = null
        super.onDestroyView()
    }

}
