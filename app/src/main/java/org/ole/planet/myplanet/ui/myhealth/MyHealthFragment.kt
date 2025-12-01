package org.ole.planet.myplanet.ui.myhealth

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.BaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentVitalSignBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class MyHealthFragment : Fragment() {
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var syncManager: SyncManager
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var healthRepository: HealthRepository

    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    private lateinit var realtimeSyncListener: BaseRealtimeSyncListener
    private var _binding: FragmentVitalSignBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertMyPersonalBinding: AlertMyPersonalBinding
    private var alertHealthListBinding: AlertHealthListBinding? = null
    private var userId: String? = null
    private var userModel: RealmUserModel? = null
    private lateinit var userModelList: List<RealmUserModel>
    private lateinit var adapter: UserListArrayAdapter
    private lateinit var healthAdapter: AdapterHealthExamination
    private var dialog: AlertDialog? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private lateinit var prefManager: SharedPrefManager
    private lateinit var settings: SharedPreferences
    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""
    private var textWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        settings = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
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
                        Snackbar.make(
                            binding.root,
                            "Sync failed: ${msg ?: "Unknown error"}",
                            Snackbar.LENGTH_LONG
                        ).setAction("Retry") { startHealthSync() }.show()
                    }
                }
            }
        }, "full", listOf("health"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            MainApplication.isServerReachable(url)
        }
    }

    private fun refreshHealthData() {
        if (!isAdded || requireActivity().isFinishing) return
        val currentUser = getCurrentUserProfileCopy()
        userId = if (currentUser?._id.isNullOrEmpty()) {
            currentUser?.id
        } else {
            currentUser?._id
        }
        getHealthRecords(userId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
        setupRealtimeSync()
        alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))
        binding.txtDob.hint = "yyyy-MM-dd'"
        healthAdapter = AdapterHealthExamination(requireContext())
        binding.rvRecords.apply {
            layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
            isNestedScrollingEnabled = false
            adapter = healthAdapter
            addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        }

        setupInitialData()
        setupButtons()
    }

    private fun setupInitialData() {
        val currentUser = getCurrentUserProfileCopy()
        userId = if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id
        getHealthRecords(userId)
    }

    private fun getCurrentUserProfileCopy(): RealmUserModel? {
        return userProfileDbHandler.getUserModelCopy()
    }

    private fun setupButtons() {
        val isHealthProvider = userModel?.rolesList?.contains("health") ?: false
        binding.btnnewPatient.visibility = if (isHealthProvider) View.VISIBLE else View.GONE
        binding.btnnewPatient.setOnClickListener {
            if (isHealthProvider) {
                selectPatient()
            }
        }
        binding.updateHealth.visibility = View.VISIBLE
        binding.updateHealth.setOnClickListener {
            startActivity(Intent(activity, AddMyHealthActivity::class.java).putExtra("userId", userId))
        }
        binding.txtDob.text = if (userModel?.dob.isNullOrEmpty()) getString(R.string.birth_date) else TimeUtils.getFormattedDate(
            userModel?.dob,
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
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
            val users = withContext(Dispatchers.IO) {
                Realm.getDefaultInstance().use { realm ->
                    val results = realm.where(RealmUserModel::class.java).sort("joinDate", Sort.DESCENDING).findAll()
                    realm.copyFromRealm(results)
                }
            }
            withContext(Dispatchers.Main) {
                userModelList = users
                adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(context))
                alertHealthListBinding?.btnAddMember?.setOnClickListener {
                    startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
                }

                alertHealthListBinding?.let { binding ->
                    setTextWatcher(binding.etSearch, binding.btnAddMember, binding.list)
                    binding.list.adapter = adapter
                    binding.list.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View, i: Int, _: Long ->
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
        spnSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val sort = when (p2) {
                        0 -> Sort.DESCENDING
                        1, 2 -> Sort.ASCENDING
                        else -> Sort.DESCENDING
                    }
                    val sortBy = when (p2) {
                        0, 1 -> "joinDate"
                        else -> "name"
                    }
                    val sortedUsers = userRepository.getUsersSorted(sortBy, sort)
                    withContext(Dispatchers.Main) {
                        userModelList = sortedUsers
                        adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                        lv.adapter = adapter
                    }
                }
            }
        }
    }

    private fun setTextWatcher(etSearch: EditText, btnAddMember: Button, lv: ListView) {
        var timer: CountDownTimer? = null
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun afterTextChanged(editable: Editable) {
                timer?.cancel()
                timer = object : CountDownTimer(1000, 1500) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val userModelList = userRepository.getUsersByFilter(editable.toString())
                            withContext(Dispatchers.Main) {
                                val adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
                                lv.adapter = adapter
                                btnAddMember.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
                            }
                        }
                    }
                }.start()
            }
        }
        etSearch.addTextChangedListener(textWatcher)
    }

    override fun onResume() {
        super.onResume()
        showRecords()
    }

    private fun showRecords() {
        val currentUser = userModel
        if (currentUser == null || userId == null) {
            showNoHealthRecords()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val healthProfile = withContext(Dispatchers.IO) {
                healthRepository.getMyHealthProfile(userId!!, currentUser)
            }
            if (!isAdded || _binding == null) return@launch

            if (healthProfile != null) {
                updateUiWithHealthProfile(healthProfile)
            } else {
                showNoHealthRecords()
                Utilities.toast(activity, getString(R.string.health_record_not_available))
            }
        }
    }

    private fun updateUiWithHealthProfile(profile: MyHealthProfile) {
        binding.layoutUserDetail.visibility = if (profile.showPatientCard) View.VISIBLE else View.GONE
        binding.tvMessage.visibility = View.GONE
        binding.txtFullName.text = profile.fullName
        binding.txtEmail.text = profile.email
        binding.txtLanguage.text = profile.language
        binding.txtDob.text = profile.dob
        binding.txtOtherNeed.text = profile.otherNeeds
        binding.txtSpecialNeeds.text = profile.specialNeeds
        binding.txtBirthPlace.text = profile.birthPlace
        binding.txtEmergencyContact.text = profile.emergencyContact

        if (profile.examinations.isNotEmpty()) {
            binding.rvRecords.visibility = View.VISIBLE
            binding.tvNoRecords.visibility = View.GONE
            binding.tvDataPlaceholder.visibility = View.VISIBLE
            healthAdapter.submitList(profile.examinations)
            binding.rvRecords.post {
                val lastPosition = profile.examinations.size - 1
                if (lastPosition >= 0) {
                    binding.rvRecords.scrollToPosition(lastPosition)
                }
            }
        } else {
            binding.rvRecords.visibility = View.GONE
            binding.tvNoRecords.visibility = View.GONE
            binding.tvDataPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun showNoHealthRecords() {
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
    }

    override fun onDestroyView() {
        if (::realtimeSyncListener.isInitialized) {
            syncCoordinator.removeListener(realtimeSyncListener)
        }
        alertHealthListBinding?.etSearch?.removeTextChangedListener(textWatcher)
        textWatcher = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }
}
