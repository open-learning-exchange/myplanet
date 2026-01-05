package org.ole.planet.myplanet.ui.health

import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Sort
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.BaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentVitalSignBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.sync.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator
import org.ole.planet.myplanet.service.sync.ServerUrlMapper
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class MyHealthFragment : Fragment() {

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var userRepository: UserRepository
    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    private lateinit var realtimeSyncListener: BaseRealtimeSyncListener
    private var _binding: FragmentVitalSignBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertMyPersonalBinding: AlertMyPersonalBinding
    private var alertHealthListBinding: AlertHealthListBinding? = null
    var userId: String? = null
    var userModel: RealmUserModel? = null
    lateinit var userModelList: List<RealmUserModel>
    lateinit var adapter: UserListAdapter
    private lateinit var healthAdapter: HealthExaminationAdapter
    var dialog: AlertDialog? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    lateinit var settings: SharedPreferences
    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""
    private var textWatcher: TextWatcher? = null
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        settings = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        startHealthSync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVitalSignBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun startHealthSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isHealthSynced()) {
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
                        customProgressDialog?.setText(getString(R.string.syncing_health_data))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshHealthData()
                        prefManager.setHealthSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG).setAction("Retry") { startHealthSync() }.show()
                    }
                }
            }
        }, "full", listOf("health"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun refreshHealthData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            val currentUser = getCurrentUserProfileCopy()
            userId = if (TextUtils.isEmpty(currentUser?._id)) {
                currentUser?.id
            } else {
                currentUser?._id
            }
            getHealthRecords(userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
        setupRealtimeSync()
        alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))
        binding.txtDob.hint = "yyyy-MM-dd'"

        val allowDateEdit = false
        if(allowDateEdit) {
            binding.txtDob.setOnClickListener {
                val now = Calendar.getInstance()
                val dpd = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                    val selectedDate =
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    binding.txtDob.text = selectedDate
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                dpd.show()
            }
        } else {
            disableDobField()
        }

        binding.rvRecords.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))

        adapter = UserListAdapter(requireActivity(), android.R.layout.simple_list_item_1, mutableListOf())
        setupInitialData()
        setupButtons()
    }

    private fun setupInitialData() {
        val currentUser = getCurrentUserProfileCopy()
        userId = if (TextUtils.isEmpty(currentUser?._id)) currentUser?.id else currentUser?._id
        getHealthRecords(userId)
    }

    private fun getCurrentUserProfileCopy(): RealmUserModel? {
        return userProfileDbHandler.getUserModelCopy()
    }

    private fun setupButtons() {
        val isHealthProvider = userModel?.rolesList?.contains("health") ?: false
        binding.btnnewPatient.visibility =
            if (isHealthProvider) View.VISIBLE else View.GONE

        binding.btnnewPatient.setOnClickListener {
            if (isHealthProvider) {
                selectPatient()
            }
        }
        binding.updateHealth.visibility = View.VISIBLE

        binding.updateHealth.setOnClickListener {
            startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId))
        }

        binding.txtDob.text = if (TextUtils.isEmpty(userModel?.dob)) getString(R.string.birth_date) else getFormattedDate(userModel?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    private fun setupRealtimeSync() {
        realtimeSyncListener = object : BaseRealtimeSyncListener() {
            override fun onTableDataUpdated(update: TableDataUpdate) {
                if (update.table == "health" && update.shouldRefreshUI) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        refreshHealthData()
                    }
                }
            }
        }
        syncCoordinator.addListener(realtimeSyncListener)
    }

    private fun getHealthRecords(memberId: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val normalizedId = memberId?.trim()
            userId = normalizedId
            val fetchedUser = if (normalizedId.isNullOrEmpty()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    userRepository.getUserByAnyId(normalizedId)
                }
            }
            if (!isAdded || _binding == null) {
                return@launch
            }
            userModel = fetchedUser
            binding.lblHealthName.text = userModel?.getFullName() ?: getString(R.string.empty_text)
            binding.addNewRecord.setOnClickListener {
                startActivity(Intent(activity, AddExaminationActivity::class.java).putExtra("userId", userId))
            }
            binding.updateHealth.setOnClickListener {
                startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId))
            }
            showRecords()
        }
    }

    private fun selectPatient() {
        viewLifecycleOwner.lifecycleScope.launch {
            val users = userRepository.getUsersSortedBy("joinDate", Sort.DESCENDING)
            withContext(Dispatchers.Main) {
                userModelList = users
                adapter.clear()
                adapter.addAll(userModelList)
                adapter.notifyDataSetChanged()
                alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(context))
                alertHealthListBinding?.btnAddMember?.setOnClickListener {
                    startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
                }

                alertHealthListBinding?.let { binding ->
                    binding.list.adapter = adapter
                    setTextWatcher(binding.etSearch, binding.btnAddMember, binding.list)
                    binding.list.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, _: View, i: Int, _: Long ->
                        val selected = binding.list.adapter.getItem(i) as RealmUserModel
                        userId = if (selected._id.isNullOrEmpty()) selected.id else selected._id
                        getHealthRecords(userId)
                        dialog?.dismiss()
                    }
                    sortList(binding.spnSort, binding.list)
                    dialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                        .setTitle(getString(R.string.select_health_member)).setView(binding.root)
                        .setCancelable(false).setNegativeButton(R.string.dismiss, null).create()
                    dialog?.show()
                }
            }
        }
    }

    private fun sortList(spnSort: AppCompatSpinner, lv: ListView) {
        spnSort.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val (sortBy, sort) = when (p2) {
                        0 -> "joinDate" to Sort.DESCENDING
                        1 -> "joinDate" to Sort.ASCENDING
                        2 -> "name" to Sort.ASCENDING
                        else -> "name" to Sort.DESCENDING
                    }
                    val sortedList = withContext(Dispatchers.IO) {
                        userRepository.getUsersSortedBy(sortBy, sort)
                    }
                    if (isAdded) {
                        userModelList = sortedList
                        adapter.clear()
                        adapter.addAll(userModelList)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun setTextWatcher(etSearch: EditText, btnAddMember: Button, lv: ListView) {
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    val loadingJob = launch(Dispatchers.Main) {
                        delay(100)
                        alertHealthListBinding?.searchProgress?.visibility = View.VISIBLE
                        lv.visibility = View.GONE
                    }

                    val userModelList = withContext(Dispatchers.IO) {
                        userRepository.searchUsers(editable.toString(), "joinDate", Sort.DESCENDING)
                    }

                    loadingJob.cancel()
                    if (isAdded) {
                        alertHealthListBinding?.searchProgress?.visibility = View.GONE
                        lv.visibility = View.VISIBLE
                        val adapter = UserListAdapter(
                            requireActivity(),
                            android.R.layout.simple_list_item_1,
                            userModelList
                        )
                        lv.adapter = adapter
                        btnAddMember.visibility =
                            if (adapter.count == 0) View.VISIBLE else View.GONE
                    }
                }
            }
        }
        etSearch.addTextChangedListener(textWatcher)
    }

    override fun onResume() {
        super.onResume()
        showRecords()
    }

    private fun showRecords() {
        if (!isAdded || _binding == null) return

        viewLifecycleOwner.lifecycleScope.launch {
            val currentUser = userModel
            if (currentUser == null || userId.isNullOrEmpty()) {
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
                return@launch
            }

            binding.layoutUserDetail.visibility = View.VISIBLE
            binding.tvMessage.visibility = View.GONE
            binding.txtFullName.text = getString(R.string.three_strings, currentUser.firstName, currentUser.middleName, currentUser.lastName)
            binding.txtEmail.text = Utilities.checkNA(currentUser.email)
            binding.txtLanguage.text = Utilities.checkNA(currentUser.language)
            binding.txtDob.text = Utilities.checkNA(currentUser.dob)

            val healthRecord = userRepository.getHealthRecordsAndAssociatedUsers(userId!!, currentUser)

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
                        healthAdapter = HealthExaminationAdapter(requireActivity(), mh, currentUser, userMap)
                    } else {
                        healthAdapter.updateData(mh, currentUser, userMap)
                    }
                    binding.rvRecords.apply {
                        layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                        isNestedScrollingEnabled = false
                        adapter = healthAdapter
                    }
                    healthAdapter.submitList(list)
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
    }

    private fun disableDobField() {
        binding.txtDob.isClickable = false
        binding.txtDob.isFocusable = false
        binding.txtDob.setOnClickListener(null)
    }

    override fun onDestroyView() {
        if (::realtimeSyncListener.isInitialized) {
            syncCoordinator.removeListener(realtimeSyncListener)
        }
        alertHealthListBinding?.etSearch?.removeTextChangedListener(textWatcher)
        textWatcher = null
        searchJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }
}
